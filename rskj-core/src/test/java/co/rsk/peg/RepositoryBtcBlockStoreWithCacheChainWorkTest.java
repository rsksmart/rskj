package co.rsk.peg;

import static co.rsk.bitcoinj.core.StoredBlock.COMPACT_SERIALIZED_SIZE_LEGACY;
import static co.rsk.bitcoinj.core.StoredBlock.COMPACT_SERIALIZED_SIZE_V2;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.trie.Trie;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongycastle.util.encoders.Hex;

class RepositoryBtcBlockStoreWithCacheChainWorkTest {

    private static final String BLOCK_STORE_CHAIN_HEAD_KEY = "blockStoreChainHead";
    // Max chain work to fit in 12 bytes
    private static final BigInteger MAX_WORK_V1 = new BigInteger(/* 12 bytes */ "ffffffffffffffffffffffff", 16);
    // Chain work too large to fit in 12 bytes
    private static final BigInteger TOO_LARGE_WORK_V1 = new BigInteger(/* 13 bytes */ "ffffffffffffffffffffffffff", 16);
    // Max chain work to fit in 32 bytes
    private static final BigInteger MAX_WORK_V2 = new BigInteger(/* 32 bytes */
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
    // Chain work too large to fit in 32 bytes
    private static final BigInteger TOO_LARGE_WORK_V2 = new BigInteger(/* 33 bytes */
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);

    private static final ActivationConfig.ForBlock arrowHeadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0);
    private static final ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0);

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters mainneNetworkParameters = bridgeMainnetConstants.getBtcParams();
    private static final RskAddress BRIDGE_ADDR = PrecompiledContracts.BRIDGE_ADDR;

    private Repository repository;
    private RepositoryBtcBlockStoreWithCache repositoryBtcBlockStoreWithCache;

    // Just an arbitrary block
    private static final String BLOCK_HEADER = "00e00820925b77c9ff4d0036aa29f3238cde12e9af9d55c34ed30200000000000000000032a9fa3e12ef87a2327b55db6a16a1227bb381db8b269d90aa3a6e38cf39665f91b47766255d0317c1b1575f";
    private static final int BLOCK_HEIGHT = 849137;
    private static final BtcBlock BLOCK = new BtcBlock(mainneNetworkParameters, Hex.decode(BLOCK_HEADER));

    @BeforeEach
    void setUp() {
        repository = spy(new MutableRepository(
            new MutableTrieCache(new MutableTrieImpl(null, new Trie()))
        ));
    }

    void arrange(ActivationConfig.ForBlock activations) {
        Map<Sha256Hash, StoredBlock> cacheBlocks = new HashMap<>();
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(repository,
            mainneNetworkParameters, activations);

        repositoryBtcBlockStoreWithCache = new RepositoryBtcBlockStoreWithCache(
            mainneNetworkParameters,
            repository,
            cacheBlocks,
            BRIDGE_ADDR,
            bridgeMainnetConstants,
            bridgeStorageProvider,
            activations
        );
    }

    @ParameterizedTest()
    @MethodSource("invalidChainWorkForV1")
    void put_preRskip_whenInvalidChainWorkForV1_shouldFail(BigInteger chainWork) {
        arrange(arrowHeadActivations);
        StoredBlock storedBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        // act
        IllegalArgumentException actualException = assertThrows(
            IllegalArgumentException.class, () -> repositoryBtcBlockStoreWithCache.put(storedBlock));

        String expectedMessage = "The given number does not fit in 12";
        String actualMessage = actualException.getMessage();
        Assertions.assertEquals(expectedMessage, actualMessage);
    }

    private static Stream<Arguments> invalidChainWorkForV1() {
        return Stream.of(
            Arguments.of(TOO_LARGE_WORK_V1),
            Arguments.of(MAX_WORK_V2),
            Arguments.of(TOO_LARGE_WORK_V2)
        );
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV1")
    void put_preRskip_whenValidChainWorkForV1_shouldStoreBlock(BigInteger chainWork) {
        arrange(arrowHeadActivations);
        StoredBlock storedBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        // act
        repositoryBtcBlockStoreWithCache.put(storedBlock);

        // assert
        Sha256Hash expectedHash = storedBlock.getHeader().getHash();

        int expectedCompactSerializedSize = COMPACT_SERIALIZED_SIZE_LEGACY;
        ByteBuffer byteBufferForExpectedBlock = ByteBuffer.allocate(expectedCompactSerializedSize);
        storedBlock.serializeCompactLegacy(byteBufferForExpectedBlock);

        byte[] expectedSerializedBlock = byteBufferForExpectedBlock.array();
        verify(repository, times(1)).addStorageBytes(
            BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString()),
            expectedSerializedBlock
        );
        byte[] actualSerializedBlock = repository.getStorageBytes(BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString()));
        Assertions.assertNotNull(actualSerializedBlock);
        Assertions.assertEquals(expectedCompactSerializedSize, actualSerializedBlock.length);

        Assertions.assertArrayEquals(expectedSerializedBlock, actualSerializedBlock);
    }

    private static Stream<Arguments> validChainWorkForV1 () {
        return Stream.of(
            Arguments.of(BigInteger.ZERO), // no work
            Arguments.of(BigInteger.ONE), // small work
            Arguments.of(BigInteger.valueOf(Long.MAX_VALUE)), // a larg-ish work
            Arguments.of(MAX_WORK_V1)
        );
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV2")
    void put_postRskip_whenChainWorkAnySizeUnder32Bytes_shouldStoreBlock(BigInteger chainWork) {
        int expectedCompactSerializedSize = COMPACT_SERIALIZED_SIZE_V2;

        arrange(lovellActivations);
        StoredBlock storedBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        // act
        repositoryBtcBlockStoreWithCache.put(storedBlock);

        // assert
        Sha256Hash expectedHash = storedBlock.getHeader().getHash();

        ByteBuffer byteBufferForExpectedBlock = ByteBuffer.allocate(expectedCompactSerializedSize);
        storedBlock.serializeCompactV2(byteBufferForExpectedBlock);

        byte[] expectedSerializedBlock = byteBufferForExpectedBlock.array();
        verify(repository, times(1)).addStorageBytes(
                BRIDGE_ADDR,
                DataWord.valueFromHex(expectedHash.toString()),
                expectedSerializedBlock
            );
        byte[] actualSerializedBlock = repository.getStorageBytes(BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString()));
        Assertions.assertNotNull(actualSerializedBlock);
        Assertions.assertEquals(expectedCompactSerializedSize, actualSerializedBlock.length);

        Assertions.assertArrayEquals(expectedSerializedBlock, actualSerializedBlock);
    }

    private static Stream<Arguments> validChainWorkForV2() {
        return Stream.of(
            Arguments.of(BigInteger.ZERO), // no work
            Arguments.of(BigInteger.ONE), // small work
            Arguments.of(BigInteger.valueOf(Long.MAX_VALUE)), // a larg-ish work
            Arguments.of(MAX_WORK_V1),
            Arguments.of(TOO_LARGE_WORK_V1),
            Arguments.of(MAX_WORK_V2)
        );
    }

    @ParameterizedTest()
    @MethodSource("invalidChainWorkForV2")
    void put_postRskip_whenInvalidChainWorkForV2_shouldFail(BigInteger chainWork) {
        arrange(lovellActivations);
        StoredBlock storedBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        // act
        IllegalArgumentException actualException = assertThrows(
            IllegalArgumentException.class, () -> repositoryBtcBlockStoreWithCache.put(storedBlock)
        );

        String expectedMessage = "The given number does not fit in 32";
        String actualMessage = actualException.getMessage();
        Assertions.assertEquals(expectedMessage, actualMessage);
    }

    private static Stream<Arguments> invalidChainWorkForV2() {
        return Stream.of(
            Arguments.of(TOO_LARGE_WORK_V2)
        );
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV1")
    void get_preRskip_whenValidChainWorkForV1_shouldGetStoredBlock(BigInteger chainWork) {
        arrange(arrowHeadActivations);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);
        Sha256Hash expectedHash = expectedStoreBlock.getHeader().getHash();
        arrangeRepositoryWithExpectedStoredBlock(expectedStoreBlock);

        // act
        StoredBlock actualStoreBlock = repositoryBtcBlockStoreWithCache.get(expectedHash);

        // assert
        verify(repository, times(1)).getStorageBytes(
            BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString())
        );

        Assertions.assertEquals(expectedStoreBlock, actualStoreBlock);
    }

    private void arrangeRepositoryWithExpectedStoredBlock(StoredBlock expectedStoreBlock) {
        Sha256Hash expectedHash = expectedStoreBlock.getHeader().getHash();
        ByteBuffer byteBuffer = ByteBuffer.allocate(COMPACT_SERIALIZED_SIZE_LEGACY);
        expectedStoreBlock.serializeCompactLegacy(byteBuffer);
        when(repository.getStorageBytes(
            BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString())
        )).thenReturn(byteBuffer.array());
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV1")
    void get_postRskip_whenChainWorkForV1_shouldGetStoredBlock(BigInteger chainWork) {
        arrange(lovellActivations);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);
        Sha256Hash expectedHash = expectedStoreBlock.getHeader().getHash();
        arrangeRepositoryWithExpectedStoredBlockV2(expectedStoreBlock);

        // act
        StoredBlock actualStoreBlock = repositoryBtcBlockStoreWithCache.get(expectedHash);

        // assert
        verify(repository, times(1)).getStorageBytes(
            BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString())
        );

        Assertions.assertEquals(expectedStoreBlock, actualStoreBlock);
    }

    private void arrangeRepositoryWithExpectedStoredBlockV2(StoredBlock expectedStoreBlock) {
        Sha256Hash expectedHash = expectedStoreBlock.getHeader().getHash();
        ByteBuffer byteBuffer = ByteBuffer.allocate(COMPACT_SERIALIZED_SIZE_V2);
        expectedStoreBlock.serializeCompactV2(byteBuffer);
        when(repository.getStorageBytes(
            BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString())
        )).thenReturn(byteBuffer.array());
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV2")
    void get_postRskip_whenStoredBLochHasChainWorkOver12Bytes_shouldGetStoredBlock(BigInteger chainWork) {
        arrange(lovellActivations);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);
        Sha256Hash expectedHash = expectedStoreBlock.getHeader().getHash();
        arrangeRepositoryWithExpectedStoredBlockV2(expectedStoreBlock);

        // act
        StoredBlock actualStoreBlock = repositoryBtcBlockStoreWithCache.get(expectedHash);

        // assert
        verify(repository, times(1)).getStorageBytes(
            BRIDGE_ADDR,
            DataWord.valueFromHex(expectedHash.toString())
        );

        Assertions.assertEquals(expectedStoreBlock, actualStoreBlock);
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV1")
    void getChainHead_preRskip_whenValidChainWorkForV1_shouldGetChainHead(BigInteger chainWork) {
        arrange(arrowHeadActivations);
        reset(repository);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        arrangeRepositoryWithExpectedChainHead(expectedStoreBlock);

        // act
        StoredBlock actualStoreBlock = repositoryBtcBlockStoreWithCache.getChainHead();

        // assert
        verify(repository, times(1)).getStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY)
        );

        Assertions.assertEquals(expectedStoreBlock, actualStoreBlock);
    }

    private void arrangeRepositoryWithExpectedChainHead(StoredBlock expectedStoreBlock) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(COMPACT_SERIALIZED_SIZE_LEGACY);
        expectedStoreBlock.serializeCompactLegacy(byteBuffer);
        when(repository.getStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY))
        ).thenReturn(byteBuffer.array());
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV1")
    void getChainHead_postRskip_whenChainWorkForV1_shouldGetChainHead(BigInteger chainWork) {
        arrange(lovellActivations);
        reset(repository);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        arrangeRepositoryWithChainHeadV2(expectedStoreBlock);

        // act
        StoredBlock actualStoreBlock = repositoryBtcBlockStoreWithCache.getChainHead();

        // assert
        verify(repository, times(1)).getStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY)
        );

        Assertions.assertEquals(expectedStoreBlock, actualStoreBlock);
    }

    private void arrangeRepositoryWithChainHeadV2(StoredBlock expectedStoreBlock) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(COMPACT_SERIALIZED_SIZE_V2);
        expectedStoreBlock.serializeCompactV2(byteBuffer);
        when(repository.getStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY))
        ).thenReturn(byteBuffer.array());
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV2")
    void getChainHead_postRskip_whenStoredBLochHasChainWorkOver12Bytes_shouldGetChainHead(BigInteger chainWork) {
        arrange(lovellActivations);
        reset(repository);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        arrangeRepositoryWithChainHeadV2(expectedStoreBlock);

        // act
        StoredBlock actualStoreBlock = repositoryBtcBlockStoreWithCache.getChainHead();

        // assert
        verify(repository, times(1)).getStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY)
        );

        Assertions.assertEquals(expectedStoreBlock, actualStoreBlock);
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV1")
    void setChainHead_preRskip_whenValidChainWorkForV1_shouldStoreChainHead(BigInteger chainWork) {
        arrange(arrowHeadActivations);
        reset(repository);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        // act
        repositoryBtcBlockStoreWithCache.setChainHead(expectedStoreBlock);

        // assert
        ByteBuffer byteBuffer = ByteBuffer.allocate(COMPACT_SERIALIZED_SIZE_LEGACY);
        expectedStoreBlock.serializeCompactLegacy(byteBuffer);

        verify(repository, times(1)).addStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY),
            byteBuffer.array()
        );
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV1")
    void setChainHead_postRskip_whenChainWorkForV1_shouldStoreChainHead(BigInteger chainWork) {
        arrange(lovellActivations);
        reset(repository);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        // act
        repositoryBtcBlockStoreWithCache.setChainHead(expectedStoreBlock);

        // assert
        ByteBuffer byteBuffer = ByteBuffer.allocate(COMPACT_SERIALIZED_SIZE_V2);
        expectedStoreBlock.serializeCompactV2(byteBuffer);

        verify(repository, times(1)).addStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY),
            byteBuffer.array()
        );
    }

    @ParameterizedTest()
    @MethodSource("validChainWorkForV2")
    void setChainHead_postRskip_whenStoredBLochHasChainWorkOver12Bytes1_shouldStoreChainHead(BigInteger chainWork) {
        arrange(lovellActivations);
        reset(repository);
        StoredBlock expectedStoreBlock = new StoredBlock(BLOCK, chainWork, BLOCK_HEIGHT);

        // act
        repositoryBtcBlockStoreWithCache.setChainHead(expectedStoreBlock);

        // assert
        ByteBuffer byteBuffer = ByteBuffer.allocate(COMPACT_SERIALIZED_SIZE_V2);
        expectedStoreBlock.serializeCompactV2(byteBuffer);

        verify(repository, times(1)).addStorageBytes(
            BRIDGE_ADDR,
            DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY),
            byteBuffer.array()
        );
    }
}
