/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.peg;

import static co.rsk.bitcoinj.core.Utils.uint32ToByteStreamLE;
import static co.rsk.peg.federation.FederationStorageIndexKey.*;
import static co.rsk.peg.federation.FederationTestUtils.REGTEST_FEDERATION_PRIVATE_KEYS;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.whitelist.WhitelistResponseCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;

import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.federation.*;
import co.rsk.test.builders.BlockChainBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import co.rsk.asm.EVMAssembler;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.bitcoin.SimpleBtcTransaction;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.test.World;
import co.rsk.trie.*;
import co.rsk.util.HexUtils;

/**
 * Created by ajlopez on 6/8/2016.
 */
@ExtendWith(MockitoExtension.class)
// to avoid Junit5 unnecessary stub error due to some setup generalizations
@MockitoSettings(strictness = Strictness.LENIENT)
class BridgeIT {
    private static final RskAddress BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private static final String BRIDGE_ADDRESS_TO_STRING = PrecompiledContracts.BRIDGE_ADDR_STR;
    private static final ECKey federatorECKey = ECKey.fromPrivate(REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKey());
    private static final String[] ACTIVE_FED_SEEDS = new String[] {
        "activeFedMember1",
        "activeFedMember2",
        "activeFedMember3",
        "activeFedMember4",
        "activeFedMember5",
        "activeFedMember6",
        "activeFedMember7",
        "activeFedMember8",
        "activeFedMember9"
    };
    private static final List<BtcECKey> ACTIVE_FEDERATION_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(ACTIVE_FED_SEEDS, true);
    private static final BtcECKey ACTIVE_FEDERATOR_SIGNER_KEY = ACTIVE_FEDERATION_KEYS.get(0);
    private static final Federation ACTIVE_FEDERATION = P2shErpFederationBuilder.builder()
        .withMembersBtcPublicKeys(ACTIVE_FEDERATION_KEYS)
        .build();
    private static final String[] RETIRING_FED_SEEDS = new String[] {
        "retiringFedMember1",
        "retiringFedMember2",
        "retiringFedMember3",
        "retiringFedMember4",
        "retiringFedMember5",
        "retiringFedMember6",
        "retiringFedMember7",
        "retiringFedMember8",
        "retiringFedMember9"
    };
    private static final List<BtcECKey> RETIRING_FEDERATION_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(RETIRING_FED_SEEDS, true);
    private static final BtcECKey RETIRING_FEDERATOR_SIGNER_KEY = RETIRING_FEDERATION_KEYS.get(0);
    private static final Federation RETIRING_FEDERATION = P2shErpFederationBuilder.builder()
        .withMembersBtcPublicKeys(RETIRING_FEDERATION_KEYS)
        .build();
    private static final String[] PROPOSED_FED_SEEDS = new String[] {
        "proposedFedMember1",
        "proposedFedMember2",
        "proposedFedMember3",
        "proposedFedMember4",
        "proposedFedMember5",
        "proposedFedMember6",
        "proposedFedMember7",
        "proposedFedMember8",
        "proposedFedMember9"
    };
    private static final List<BtcECKey> PROPOSED_FEDERATION_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(PROPOSED_FED_SEEDS, true);
    private static final BtcECKey PROPOSED_FEDERATOR_SIGNER_KEY = PROPOSED_FEDERATION_KEYS.get(0);
    private static final Federation PROPOSED_FEDERATION = P2shErpFederationBuilder.builder()
        .withMembersBtcPublicKeys(PROPOSED_FEDERATION_KEYS)
        .build();

    private static final BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters regtestParameters = bridgeRegTestConstants.getBtcParams();

    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final String ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED = "The sender is not a member of the active or retiring federations";
    private static final String ERR_NOT_FROM_ACTIVE_RETIRING_OR_PROPOSED_FED = "The sender is not a member of the active, retiring, or proposed federations";

    private TestSystemProperties config = new TestSystemProperties();
    private Constants constants;
    private ActivationConfig activationConfig;
    private ActivationConfig.ForBlock activationConfigAll;
    private BlockFactory blockFactory;
    private SignatureCache signatureCache;
    private Bridge bridge;
    private Repository track;

    @BeforeEach
    void resetConfigToRegTest() {
        config = spy(new TestSystemProperties());
        constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
        blockFactory = new BlockFactory(activationConfig);
        activationConfigAll = ActivationConfigsForTest.all().forBlock(0);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    }

    @Test
    void callUpdateCollectionsWithSignatureNotFromFederation() throws IOException {
        BtcTransaction tx1 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, BRIDGE_ADDRESS, regtestParameters, activationConfigAll);

        provider0.getPegoutsWaitingForConfirmations().add(tx1, 1L, PegTestUtils.createHash3(0));
        provider0.save();

        track.commit();

        track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder().setRequireUnclesValidation(false);
        World world = new World(blockChainBuilder);
        bridge.init(rskTx, world.getBlockChain().getBestBlock(), track, world.getBlockStore(), null, new LinkedList<>());
        try {
            bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    void callUpdateCollectionsWithTransactionsWaitingForConfirmation() throws IOException, VMException {
        BtcTransaction tx1 = createTransaction(2, bridgeRegTestConstants.getMinimumPegoutTxValue());
        BtcTransaction tx2 = createTransaction(3, bridgeRegTestConstants.getMinimumPegoutTxValue().add(Coin.MILLICOIN));
        BtcTransaction tx3 = createTransaction(4, bridgeRegTestConstants.getMinimumPegoutTxValue().add(Coin.MILLICOIN).add(Coin.MILLICOIN));

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, BRIDGE_ADDRESS, regtestParameters, activationConfig.forBlock(0));

        provider0.getPegoutsWaitingForConfirmations().add(tx1, 1L);
        provider0.getPegoutsWaitingForConfirmations().add(tx2, 2L);
        provider0.getPegoutsWaitingForConfirmations().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();
        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);

        BlockChainBuilder blockChainBuilder = new BlockChainBuilder().setRequireUnclesValidation(false);
        World world = new World(blockChainBuilder);
        bridge.init(rskTx, world.getBlockChain().getBestBlock(), track, world.getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        //Reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, BRIDGE_ADDRESS, regtestParameters, activationConfigAll);

        assertEquals(3, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForSignatures().size());
    }

    @Test
    void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException, VMException {
        BtcTransaction tx1 = createTransaction(2, bridgeRegTestConstants.getMinimumPegoutTxValue());
        BtcTransaction tx2 = createTransaction(3, bridgeRegTestConstants.getMinimumPegoutTxValue().add(Coin.MILLICOIN));
        BtcTransaction tx3 = createTransaction(4, bridgeRegTestConstants.getMinimumPegoutTxValue().add(Coin.MILLICOIN).add(Coin.MILLICOIN));

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, BRIDGE_ADDRESS, regtestParameters, activationConfig.forBlock(0));

        provider0.getPegoutsWaitingForConfirmations().add(tx1, 1L);
        provider0.getPegoutsWaitingForConfirmations().add(tx2, 2L);
        provider0.getPegoutsWaitingForConfirmations().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();

        BlockChainBuilder blockChainBuilder = new BlockChainBuilder().setRequireUnclesValidation(false);
        World world = new World(blockChainBuilder);
        List<Block> blocks = new BlockGenerator().getSimpleBlockChain(world.getBlockChain().getBestBlock(), 10);

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        world.getBlockStore().saveBlock(blocks.get(1), new BlockDifficulty(BigInteger.ONE), true);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, blocks.get(9), track, world.getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        // reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, BRIDGE_ADDRESS, regtestParameters, activationConfigAll);

        assertEquals(2, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForSignatures().size());
    }

    @Test
    void sendNoRskTx() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), track, null, null, null);
        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("Rsk Transaction is null"));
        }

        track.commit();
    }

    @Test
    void sendNoBlockHeader() throws BlockStoreException, IOException, VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = mock(BridgeSupportFactory.class);
        BridgeSupport bridgeSupport = mock(BridgeSupport.class);
        when(bridgeSupportFactory.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupport);
        when(bridgeSupport.getActiveFederation()).thenReturn(FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants()));
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        bridge.execute(Bridge.RECEIVE_HEADERS.encode());

        track.commit();

        verify(bridgeSupport, times(1)).receiveHeaders(new BtcBlock[]{});
        // TODO improve test
        Assertions.assertNotNull(track.getRoot());
    }

    @Test
    void sendOrphanBlockHeader() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig, signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        Integer previousHeight = bridge.getBtcBlockchainBestChainHeight(new Object[]{});

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(regtestParameters, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, Utils.encodeCompactBits(regtestParameters.getMaxTarget()), 1, new ArrayList<>())
                .cloneAsHeader();
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        Object[] objectArray = new Object[headers.length];

        for (int i = 0; i < headers.length; i++)
            objectArray[i] = headers[i].bitcoinSerialize();

        bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray}));

        track.commit();

        assertEquals(previousHeight, bridge.getBtcBlockchainBestChainHeight(new Object[]{}));
        // TODO improve test
        Assertions.assertNotNull(track.getRoot());
    }

    @Test
    void executeWithFunctionSignatureLengthTooShortBeforeRskip88() throws VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig, signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        assertNull(bridge.execute(new byte[3]));
    }

    @Test
    void executeWithFunctionSignatureLengthTooShortAfterRskip88() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache
        );
        Bridge bridge = new Bridge(
            BRIDGE_ADDRESS,
                constants,
                activationConfig,
                bridgeSupportFactory,
                signatureCache
        );
        Transaction mockedTx = mock(Transaction.class);

        try {
            bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
            bridge.execute(new byte[3]);
            fail();
        } catch (VMException e) {
            assertTrue(e.getMessage().contains("Invalid data given"));
        }
    }

    @Test
    void executeWithInexistentFunctionBeforeRskip88() throws VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        assertNull(bridge.execute(new byte[4]));
    }

    @Test
    void executeWithInexistentFunctionAfterRskip88() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        Transaction mockedTx = mock(Transaction.class);

        try {
            bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
            bridge.execute(new byte[4]);
            fail();
        } catch (VMException e) {
            assertTrue(e.getMessage().contains("Invalid data given"));
        }
    }

    @Test
    void receiveHeadersNotFromTheFederation() {
        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    void receiveHeadersWithNonParseableHeader() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        Object[] objectArray = new Object[1];
        objectArray[0] = new byte[60];

        byte[] data = Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray});

        assertNull(bridge.execute(data));

    }

    @Test
    void receiveHeadersWithCorrectSizeHeaders() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = spy(new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache));

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        final int numBlocks = 10;
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[numBlocks];

        for (int i = 0; i < numBlocks; i++) {
            co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(regtestParameters, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, Utils.encodeCompactBits(regtestParameters.getMaxTarget()), 1, new ArrayList<>()).cloneAsHeader();
            headers[i] = block;
        }

        byte[][] headersSerialized = new byte[headers.length][];

        for (int i = 0; i < headers.length; i++) {
            headersSerialized[i] = headers[i].bitcoinSerialize();
        }

        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.isFromFederateMember(any(), any(), any())).thenReturn(true);

            MessageSerializer serializer = regtestParameters.getDefaultSerializer();
            MessageSerializer spySerializer = Mockito.spy(serializer);

            NetworkParameters btcParamsMock = mock(NetworkParameters.class);
            BridgeConstants bridgeConstantsMock = mock(BridgeConstants.class);

            when(bridgeConstantsMock.getBtcParams()).thenReturn(btcParamsMock);
            when(btcParamsMock.getDefaultSerializer()).thenReturn(spySerializer);

            TestUtils.setInternalState(bridge, "bridgeConstants", bridgeConstantsMock);

            bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

            bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{headersSerialized}));

            track.commit();

            verify(bridgeSupportMock, times(1)).receiveHeaders(headers);
            for (int i = 0; i < headers.length; i++) {
                verify(spySerializer, times(1)).makeBlock(headersSerialized[i]);
            }
        }
    }

    @Test
    void receiveHeadersWithIncorrectSizeHeaders() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());


        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        Bridge spiedBridge = spy(new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache));
        spiedBridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        final int numBlocks = 10;
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[numBlocks];
        byte[][] headersSerialized = new byte[headers.length][];

        // Add a couple of transactions to the block so that it doesn't serialize as just the header
        for (int i = 0; i < numBlocks; i++) {
            co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
                regtestParameters,
                    1,
                    PegTestUtils.createHash(),
                    PegTestUtils.createHash(),
                    1,
                    Utils.encodeCompactBits(regtestParameters.getMaxTarget()),
                    1,
                    new ArrayList<>()
            );

            BtcECKey from = new BtcECKey();
            BtcECKey to = new BtcECKey();

            // Coinbase TX
            BtcTransaction coinbaseTx = new BtcTransaction(regtestParameters);
            coinbaseTx.addInput(Sha256Hash.ZERO_HASH, -1, ScriptBuilder.createOpReturnScript(new byte[0]));
            block.addTransaction(coinbaseTx);

            // Random TX
            BtcTransaction inputTx = new BtcTransaction(regtestParameters);
            inputTx.addOutput(Coin.FIFTY_COINS, from.toAddress(regtestParameters));
            BtcTransaction outputTx = new BtcTransaction(regtestParameters);
            outputTx.addInput(inputTx.getOutput(0));
            outputTx.getInput(0).disconnect();
            outputTx.addOutput(Coin.COIN, to.toAddress(regtestParameters));
            block.addTransaction(outputTx);

            headers[i] = block;
            headersSerialized[i] = block.bitcoinSerialize();

            // Make sure we would be able to deserialize the block
            assertEquals(block, regtestParameters.getDefaultSerializer().makeBlock(headersSerialized[i]));
        }

        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.isFromFederateMember(any(), any(), any())).thenReturn(true);

            NetworkParameters btcParamsMock = mock(NetworkParameters.class);
            BridgeConstants bridgeConstantsMock = mock(BridgeConstants.class);

            TestUtils.setInternalState(spiedBridge, "bridgeConstants", bridgeConstantsMock);

            spiedBridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{headersSerialized}));

            track.commit();

            verify(bridgeSupportMock, never()).receiveHeaders(headers);
            verify(btcParamsMock, never()).getDefaultSerializer();
        }
    }

    @Test
    void registerBtcTransactionNotFromFederation() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig, signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        try {
            bridge.execute(data);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    void receiveHeadersWithHugeDeclaredTransactionsSize() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig, signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcBlock block = new BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>()) {
            @Override
            protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
                Utils.uint32ToByteStreamLE(getVersion(), stream);
                stream.write(getPrevBlockHash().getReversedBytes());
                stream.write(getMerkleRoot().getReversedBytes());
                Utils.uint32ToByteStreamLE(getTimeSeconds(), stream);
                Utils.uint32ToByteStreamLE(getDifficultyTarget(), stream);
                Utils.uint32ToByteStreamLE(getNonce(), stream);

                stream.write(new VarInt(Integer.MAX_VALUE).encode());
            }

            @Override
            public byte[] bitcoinSerialize() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    bitcoinSerializeToStream(baos);
                } catch (IOException e) {
                }
                return baos.toByteArray();
            }
        };

        Object[] objectArray = new Object[1];
        objectArray[0] = block.bitcoinSerialize();

        byte[] data = Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray});

        assertNull(bridge.execute(data));

    }

    @Test
    void registerBtcTransactionWithNonParseableTx() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig, signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        assertNull(bridge.execute(data));
    }

    @Test
    void registerBtcTransactionWithHugeDeclaredInputsSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, true, false, false, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    void registerBtcTransactionWithHugeDeclaredOutputsSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, true, false, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    void registerBtcTransactionWithHugeDeclaredWitnessPushCountSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, false, true, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    void registerBtcTransactionWithHugeDeclaredWitnessPushSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, false, false, true);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    void registerBtcTransactionWithNonParseableMerkleeProof1() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[3]);

        assertNull(bridge.execute(data));
    }

    @Test
    void registerBtcTransactionWithNonParseableMerkleeProof2() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[30]);

        assertNull(bridge.execute(data));
    }

    @Test
    void registerBtcTransactionWithHugeDeclaredSizeMerkleeProof() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001"));
        hashes.add(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000002"));
        hashes.add(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000003"));
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3) {
            @Override
            public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
                uint32ToByteStreamLE(getTransactionCount(), stream);
                stream.write(new VarInt(Integer.MAX_VALUE).encode());
                for (Sha256Hash hash : hashes) {
                    stream.write(hash.getReversedBytes());
                }

                stream.write(new VarInt(bits.length).encode());
                stream.write(bits);
            }
        };
        byte[] pmtSerialized = pmt.bitcoinSerialize();

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, pmtSerialized);

        assertNull(bridge.execute(data));
    }

    @Test
    void getFederationAddress() throws Exception {
        // Case with genesis federation
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants());
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
            new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
            bridgeRegTestConstants,
            activationConfig,
            signatureCache
        );
        Bridge bridge = new Bridge(
            BRIDGE_ADDRESS,
            constants,
            activationConfig,
            bridgeSupportFactory,
            signatureCache
        );
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), track, null, null, null);

        byte[] data = Bridge.GET_FEDERATION_ADDRESS.encode();

        assertArrayEquals(Bridge.GET_FEDERATION_ADDRESS.encodeOutputs(genesisFederation.getAddress().toString()), bridge.execute(data));
    }

    @Test
    void getMinimumLockTxValue() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), track, null, null, null);

        byte[] data = Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode();

        Assertions.assertArrayEquals(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encodeOutputs(bridgeRegTestConstants.getMinimumPeginTxValue(activationConfig.forBlock(0)).value), bridge.execute(data));
    }

    @Test
    void addSignature_fromNewFederator_whenNewAndOldFederationsAndNewFedIsActive_shouldCall() {
        // arrange
        setUp();

        saveRetiringFederationAsOldFederation();
        saveActiveFederationAsNewFederation();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with active federator, so he is the one calling the method
        BtcECKey activeFederatorSignerKey = ACTIVE_FEDERATION_KEYS.get(0);
        rskTx.sign(activeFederatorSignerKey.getPrivKeyBytes());

        long blockNumberForNewFedToBeActive = bridgeMainnetConstants.getFederationConstants().getFederationActivationAge(activationConfigAll)
            + ACTIVE_FEDERATION.getCreationBlockNumber();
        org.ethereum.core.Block rskExecutionBlock = RskTestUtils.createRskBlock(blockNumberForNewFedToBeActive);
        initializeBridge(rskTx, rskExecutionBlock);

        // get the data to call method
        byte[] data = getAddSignatureEncodedData(activeFederatorSignerKey);

        // act & assert
        assertDoesNotThrow(() -> bridge.execute(data));
    }

    @Test
    void addSignature_fromOldFederator_whenNewAndOldFederationsAndNewFedIsActive_shouldCall() {
        // arrange
        setUp();

        saveRetiringFederationAsOldFederation();
        saveActiveFederationAsNewFederation();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with retiring federator, so he is the one calling the method
        rskTx.sign(RETIRING_FEDERATOR_SIGNER_KEY.getPrivKeyBytes());

        long blockNumberForNewFedToBeActive = bridgeMainnetConstants.getFederationConstants().getFederationActivationAge(activationConfigAll)
            + ACTIVE_FEDERATION.getCreationBlockNumber();
        org.ethereum.core.Block rskExecutionBlock = RskTestUtils.createRskBlock(blockNumberForNewFedToBeActive);
        initializeBridge(rskTx, rskExecutionBlock);

        byte[] data = getAddSignatureEncodedData(RETIRING_FEDERATOR_SIGNER_KEY);

        // act & assert
        assertDoesNotThrow(() -> bridge.execute(data));
    }

    @Test
    void addSignature_fromOldFederator_whenNewAndOldFederationsAndNewFedIsInactive_shouldCall() {
        // arrange
        setUp();

        saveRetiringFederationAsOldFederation();
        saveActiveFederationAsNewFederation();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with retiring federator, so he is the one calling the method
        rskTx.sign(RETIRING_FEDERATOR_SIGNER_KEY.getPrivKeyBytes());

        long blockNumberForNewFedToBeInactive = bridgeMainnetConstants.getFederationConstants().getFederationActivationAge(activationConfigAll)
            + ACTIVE_FEDERATION.getCreationBlockNumber()
            - 1;
        org.ethereum.core.Block rskExecutionBlock = RskTestUtils.createRskBlock(blockNumberForNewFedToBeInactive);
        initializeBridge(rskTx, rskExecutionBlock);

        byte[] data = getAddSignatureEncodedData(RETIRING_FEDERATOR_SIGNER_KEY);

        // act & assert
        assertDoesNotThrow(() -> bridge.execute(data));
    }

    @Test
    void addSignature_fromNewFederator_whenNewAndOldFederationsAndNewFedIsInactive_shouldThrow() {
        // arrange
        setUp();

        saveRetiringFederationAsOldFederation();
        saveActiveFederationAsNewFederation();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with retiring federator, so he is the one calling the method
        BtcECKey activeFederatorSignerKey = ACTIVE_FEDERATION_KEYS.get(0);
        rskTx.sign(activeFederatorSignerKey.getPrivKeyBytes());

        long blockNumberForNewFedToBeInactive = bridgeMainnetConstants.getFederationConstants().getFederationActivationAge(activationConfigAll)
            + ACTIVE_FEDERATION.getCreationBlockNumber()
            - 1;
        org.ethereum.core.Block rskExecutionBlock = RskTestUtils.createRskBlock(blockNumberForNewFedToBeInactive);
        initializeBridge(rskTx, rskExecutionBlock);

        byte[] data = getAddSignatureEncodedData(activeFederatorSignerKey);

        // act & assert
        VMException result = assertThrows(VMException.class, () -> bridge.execute(data));
        assertTrue(result.getMessage().contains(ERR_NOT_FROM_ACTIVE_RETIRING_OR_PROPOSED_FED));
    }

    @Test
    void addSignature_fromNewFederator_whenNoOldFederation_shouldCall() {
        // arrange
        setUp();

        saveActiveFederationAsNewFederation();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with active federator, so he is the one calling the method
        rskTx.sign(ACTIVE_FEDERATOR_SIGNER_KEY.getPrivKeyBytes());

        initializeBridge(rskTx, getGenesisBlock());

        byte[] data = getAddSignatureEncodedData(ACTIVE_FEDERATOR_SIGNER_KEY);

        // act & assert
        assertDoesNotThrow(() -> bridge.execute(data));
    }

    private void saveActiveFederationAsNewFederation() {
        byte[] activeFederationSerialized = BridgeSerializationUtils.serializeFederation(ACTIVE_FEDERATION);
        track.addStorageBytes(BRIDGE_ADDRESS, NEW_FEDERATION_FORMAT_VERSION.getKey(), BridgeSerializationUtils.serializeInteger(3000));
        track.addStorageBytes(BRIDGE_ADDRESS, NEW_FEDERATION_KEY.getKey(), activeFederationSerialized);
    }

    private void saveRetiringFederationAsOldFederation() {
        byte[] retiringFederationSerialized = BridgeSerializationUtils.serializeFederation(RETIRING_FEDERATION);
        track.addStorageBytes(BRIDGE_ADDRESS, OLD_FEDERATION_FORMAT_VERSION.getKey(), BridgeSerializationUtils.serializeInteger(3000));
        track.addStorageBytes(BRIDGE_ADDRESS, OLD_FEDERATION_KEY.getKey(), retiringFederationSerialized);
    }

    @Test
    void addSignature_fromProposedFederator_whenProposedFederation_shouldCall() {
        // arrange
        setUp();

        saveProposedFederation();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with proposed federator, so he is the one calling the method
        rskTx.sign(PROPOSED_FEDERATOR_SIGNER_KEY.getPrivKeyBytes());

        initializeBridge(rskTx, getGenesisBlock());

        byte[] data = getAddSignatureEncodedData(PROPOSED_FEDERATOR_SIGNER_KEY);

        // act & assert
        assertDoesNotThrow(() -> bridge.execute(data));
    }

    private void saveProposedFederation() {
        byte[] proposedFederationSerialized = BridgeSerializationUtils.serializeFederation(PROPOSED_FEDERATION);
        track.addStorageBytes(BRIDGE_ADDRESS, PROPOSED_FEDERATION_FORMAT_VERSION.getKey(), BridgeSerializationUtils.serializeInteger(3000));
        track.addStorageBytes(BRIDGE_ADDRESS, FederationStorageIndexKey.PROPOSED_FEDERATION.getKey(), proposedFederationSerialized);
    }

    @Test
    void addSignature_fromProposedFederator_whenNoProposedFederation_shouldThrow() {
        // arrange
        setUp();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with proposed federator, so he is the one calling the method
        rskTx.sign(PROPOSED_FEDERATOR_SIGNER_KEY.getPrivKeyBytes());

        initializeBridge(rskTx, getGenesisBlock());

        byte[] data = getAddSignatureEncodedData(PROPOSED_FEDERATOR_SIGNER_KEY);

        // act & assert
        VMException result = assertThrows(VMException.class, () -> bridge.execute(data));
        assertTrue(result.getMessage().contains(ERR_NOT_FROM_ACTIVE_RETIRING_OR_PROPOSED_FED));
    }

    private void setUp() {
        constants = Constants.mainnet();
        activationConfig = ActivationConfigsForTest.all();

        Repository repository = createRepository();
        track = repository.startTracking();
    }

    private Transaction recreateRskTx() {
        return Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
            .data(Hex.decode(DATA))
            .chainId(Constants.MAINNET_CHAIN_ID)
            .value(AMOUNT)
            .build();
    }

    private void initializeBridge(Transaction rskTx, Block block) {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
            new RepositoryBtcBlockStoreWithCache.Factory(bridgeMainnetConstants.getBtcParams()),
            bridgeMainnetConstants,
            activationConfig,
            signatureCache
        );

        bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, block, track, null, null, null);
    }

    private byte[] getAddSignatureEncodedData(BtcECKey signerKey) {
        byte[] rskTxHash = new byte[32];
        byte[] signerPublicKeySerialized = signerKey.getPubKey();
        BtcECKey.ECDSASignature signature = signerKey.sign(Sha256Hash.ZERO_HASH);
        Object[] signaturesObjectArray = new Object[]{ signature.encodeToDER() };
        return Bridge.ADD_SIGNATURE.encode(signerPublicKeySerialized, signaturesObjectArray, rskTxHash);
    }

    @Test
    void addSignature_fromNotFederatorSigner_shouldThrow() {
        // arrange
        setUp();

        // this is not a situation that can happen in real life, but it simplifies the test
        saveActiveFederationAsNewFederation();
        saveRetiringFederationAsOldFederation();
        saveProposedFederation();

        Transaction rskTx = recreateRskTx();
        // sign rskTx with not federator, so he is the one calling the method
        BtcECKey notFederatorSigner = BitcoinTestUtils.getBtcEcKeyFromSeed("notFederator");
        rskTx.sign(notFederatorSigner.getPrivKeyBytes());

        initializeBridge(rskTx, getGenesisBlock());

        byte[] data = getAddSignatureEncodedData(notFederatorSigner);

        // act & assert
        VMException result = assertThrows(VMException.class, () -> bridge.execute(data));
        assertTrue(result.getMessage().contains(ERR_NOT_FROM_ACTIVE_RETIRING_OR_PROPOSED_FED));
    }

    @Test
    void addSignatureWithNonParseablePublicKey() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new byte[3];
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        assertNull(bridge.execute(data));
    }

    @Test
    void addSignatureWithEmptySignatureArray() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        assertNull(bridge.execute(data));
    }

    @Test
    void addSignatureWithNonParseableSignature() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new byte[3]};
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        assertNull(bridge.execute(data));
    }

    @Test
    void addSignatureWithNonParseableRskTx() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new BtcECKey().sign(Sha256Hash.ZERO_HASH).encodeToDER()};
        byte[] rskTxHash = new byte[3];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        assertNull(bridge.execute(data));
    }

    @Test
    void exceptionInUpdateCollection() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        try {
            bridge.updateCollections(null);
            fail();
        } catch (VMException ex) {
            assertEquals("Exception onBlock", ex.getMessage());
        }
    }

    @Test
    void exceptionInReleaseBtc() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        try {
            bridge.releaseBtc(null);
            fail();
        } catch (VMException ex) {
            assertEquals("Exception in releaseBtc", ex.getMessage());
        }
    }

    @Test
    void exceptionInGetStateForBtcReleaseClient() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        try {
            bridge.getStateForBtcReleaseClient(null);
            fail();
        } catch (VMException ex) {
            assertEquals("Exception in getStateForBtcReleaseClient", ex.getMessage());
        }
    }

    @Test
    void exceptionInGetStateForSvpClient() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        try {
            bridge.getStateForSvpClient(null);
            fail();
        } catch (VMException ex) {
            assertEquals("Exception in getStateForSvpClient", ex.getMessage());
        }
    }

    @Test
    void exceptionInGetStateForDebugging() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        try {
            bridge.getStateForDebugging(null);
            fail();
        } catch (VMException ex) {
            assertEquals("Exception in getStateForDebugging", ex.getMessage());
        }
    }

    @Test
    void exceptionInGetBtcBlockchainBestChainHeight() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        try {
            bridge.getBtcBlockchainBestChainHeight(null);
            fail();
        } catch (VMException ex) {
            assertEquals("Exception in getBtcBlockchainBestChainHeight", ex.getMessage());
        }
    }

    @Test
    void exceptionInGetBtcBlockchainBlockLocator() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        try {
            bridge.getBtcBlockchainBlockLocator(null);
            fail();
        } catch (VMException ex) {
            assertEquals("Exception in getBtcBlockchainBlockLocator", ex.getMessage());
        }
    }

    @Test
    void getBtcBlockchainBlockLocatorBeforeRskip89Fork() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        String hashedString = "0000000000000000000000000000000000000000000000000000000000000001";

        Sha256Hash hash = Sha256Hash.wrap(hashedString);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = spy(new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache));

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getBtcBlockchainBlockLocator()).then(
            (InvocationOnMock invocation) -> Collections.singletonList(hash)
        );

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encode());
        Object[] decodedResult = (Object[]) BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.getFunction().decodeResult(result)[0];

        assertEquals(1, decodedResult.length);
        assertEquals(hashedString, decodedResult[0]);
    }

    @Test
    void getBtcBlockchainBlockLocatorAfterRskip88And89Fork() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        doReturn(true).when(activationConfig).isActive(eq(RSKIP89), anyLong());
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), track, null, null, null);

        try {
            bridge.execute(Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encode());
            fail();
        } catch (VMException e) {
            assertTrue(e.getMessage().contains("Invalid data given:"));
        }
    }

    @Test
    void getGasForDataFreeTx() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                0,
                1,
                1,
            BRIDGE_ADDRESS,
                0,
                Bridge.UPDATE_COLLECTIONS,
                Constants.REGTEST_CHAIN_ID);
        rskTx.sign(REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());

        Block rskExecutionBlock = new BlockGenerator().createChildBlock(getGenesisInstance(config));

        Repository mockRepository = mock(Repository.class);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        assertEquals(0, bridge.getGasForData(rskTx.getData()));
    }

    @Test
    void getGasForDataInvalidFunction() {
        getGasForDataPaidTx(23000, null);
    }

    @Test
    void getGasForDataUpdateCollections() {
        getGasForDataPaidTx(48000 + 8, Bridge.UPDATE_COLLECTIONS);
    }

    @Test
    void getGasForDataReceiveHeaders() {
        getGasForDataPaidTx(22000 + 8, Bridge.RECEIVE_HEADERS);
    }

    @Test
    void getGasForDataRegisterBtcTransaction() {
        getGasForDataPaidTx(22000 + 228 * 2, Bridge.REGISTER_BTC_TRANSACTION, new byte[3], 1, new byte[3]);
    }

    @Test
    void getGasForDataReleaseBtc() {
        getGasForDataPaidTx(23000 + 8, Bridge.RELEASE_BTC);
    }

    @Test
    void getGasForDataAddSignature() {
        getGasForDataPaidTx(70000 + 548 * 2, Bridge.ADD_SIGNATURE, new byte[3], new byte[3][2], new byte[3]);
    }

    @Test
    void getGasForDataGSFBRC() {
        getGasForDataPaidTx(4000 + 8, Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT);
    }

    @Test
    void getGasForDataGSFD() {
        getGasForDataPaidTx(3_000_000 + 8, Bridge.GET_STATE_FOR_DEBUGGING);
    }

    @Test
    void getGasForDataGBBBCH() {
        getGasForDataPaidTx(19000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
    }

    @Test
    void getGasForDataGBBBL() {
        getGasForDataPaidTx(76000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR);
    }

    @Test
    void getGasForDataGetFederationAddress() {
        getGasForDataPaidTx(11000 + 8, Bridge.GET_FEDERATION_ADDRESS);
    }

    @Test
    void getGasForDataGetMinimumLockTxValue() {
        getGasForDataPaidTx(2000 + 8, Bridge.GET_MINIMUM_LOCK_TX_VALUE);
    }

    @Test
    void isBtcTxHashAlreadyProcessed_normalFlow() throws IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Set<Sha256Hash> hashes = new HashSet<>();
        when(bridgeSupportMock.isBtcTxHashAlreadyProcessed(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.contains(invocation.<Sha256Hash>getArgument(0)));

        hashes.add(Sha256Hash.of("hash_1".getBytes()));
        hashes.add(Sha256Hash.of("hash_2".getBytes()));
        hashes.add(Sha256Hash.of("hash_3".getBytes()));
        hashes.add(Sha256Hash.of("hash_4".getBytes()));

        for (Sha256Hash hash : hashes) {
            assertTrue(bridge.isBtcTxHashAlreadyProcessed(new Object[]{hash.toString()}));
            verify(bridgeSupportMock).isBtcTxHashAlreadyProcessed(hash);
        }
        Assertions.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
        Assertions.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("yetanotheranything".getBytes()).toString()}));
    }

    @Test
    void isBtcTxHashAlreadyProcessed_exception() throws IOException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        try {
            bridge.isBtcTxHashAlreadyProcessed(new Object[]{"notahash"});
            fail();
        } catch (VMException e) {
            verify(bridgeSupportMock, never()).isBtcTxHashAlreadyProcessed(any());
        }
    }

    @Test
    void getBtcTxHashProcessedHeight_normalFlow() throws IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Map<Sha256Hash, Long> hashes = new HashMap<>();
        when(bridgeSupportMock.getBtcTxHashProcessedHeight(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.get(invocation.<Sha256Hash>getArgument(0)));

        hashes.put(Sha256Hash.of("hash_1".getBytes()), 1L);
        hashes.put(Sha256Hash.of("hash_2".getBytes()), 2L);
        hashes.put(Sha256Hash.of("hash_3".getBytes()), 3L);
        hashes.put(Sha256Hash.of("hash_4".getBytes()), 4L);

        for (Map.Entry<Sha256Hash, Long> entry : hashes.entrySet()) {
            assertEquals(entry.getValue(), bridge.getBtcTxHashProcessedHeight(new Object[]{entry.getKey().toString()}));
            verify(bridgeSupportMock).getBtcTxHashProcessedHeight(entry.getKey());
        }
        assertNull(bridge.getBtcTxHashProcessedHeight(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
    }

    @Test
    void getBtcTxHashProcessedHeight_exception() throws IOException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        try {
            bridge.getBtcTxHashProcessedHeight(new Object[]{"notahash"});
            fail();
        } catch (VMException e) {
            verify(bridgeSupportMock, never()).getBtcTxHashProcessedHeight(any());
        }
    }

    @Test
    void getFederationSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getActiveFederationSize()).thenReturn(1234);

        assertEquals(1234, bridge.getFederationSize(new Object[]{}).intValue());
    }

    @Test
    void getFederationThreshold() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getActiveFederationThreshold()).thenReturn(5678);

        assertEquals(5678, bridge.getFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    void getFederationCreationBlockNumber() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getActiveFederationCreationBlockNumber()).thenReturn(42L);

        MatcherAssert.assertThat(bridge.getFederationCreationBlockNumber(new Object[]{}), is(42L));
    }

    @Test
    void getFederatorPublicKey_beforeMultikey() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        when(bridgeSupportMock.getActiveFederatorBtcPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Integer>getArgument(0)).toByteArray());
        bridge.init(mock(Transaction.class), getGenesisBlock(), null, null, null, null);

        assertArrayEquals(
            new byte[]{10},
            (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(BigInteger.valueOf(10))))[0]
        );

        assertArrayEquals(
            new byte[]{20},
            (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(BigInteger.valueOf(20))))[0]
        );

        assertArrayEquals(
            new byte[]{1, 0},
            (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(BigInteger.valueOf(256))))[0]
        );
    }

    @Test
    void getFederatorPublicKey_afterMultikey() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = new Bridge(
            BRIDGE_ADDRESS,
            constants,
            activationConfig,
            bridgeSupportFactoryMock,
            signatureCache
        );
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        assertNull(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)})));
        verify(bridgeSupportMock, never()).getActiveFederatorBtcPublicKey(any(int.class));
    }

    @Test
    void getFederatorPublicKeyOfType_beforeMultikey() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        assertNull(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(BigInteger.valueOf(10), "btc")));
        verify(bridgeSupportMock, never()).getActiveFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class));
    }

    @Test
    void getFederatorPublicKeyOfType_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getActiveFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Number>getArgument(0).longValue()).toString()
                        .concat((invocation.<FederationMember.KeyType>getArgument(1)).getValue()).getBytes()
        );
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        assertArrayEquals(
            "10btc".getBytes(),
            (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(BigInteger.valueOf(10), "btc")))[0]
        );

        assertArrayEquals(
            "200rsk".getBytes(),
            (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(BigInteger.valueOf(200), "rsk")))[0]
        );

        assertArrayEquals(
            "172mst".getBytes(),
            (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(BigInteger.valueOf(172), "mst")))[0]
        );
    }

    @Test
    void getRetiringFederationSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
            new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
            bridgeRegTestConstants,
            activationConfig,
            signatureCache
        );
        Bridge bridge = new Bridge(
            BRIDGE_ADDRESS,
            constants,
            activationConfig,
            bridgeSupportFactory,
            signatureCache
        );
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationSize()).thenReturn(Optional.of(1234));

        assertEquals(1234, bridge.getRetiringFederationSize(new Object[]{}).intValue());
    }

    @Test
    void getRetiringFederationThreshold() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationThreshold()).thenReturn(Optional.of(5678));

        assertEquals(5678, bridge.getRetiringFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    void getRetiringFederationCreationBlockNumber() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Optional<Long> retiringFederationCreationBlockNumber = Optional.of(42L);
        when(bridgeSupportMock.getRetiringFederationCreationBlockNumber()).thenReturn(retiringFederationCreationBlockNumber);
        MatcherAssert.assertThat(bridge.getRetiringFederationCreationBlockNumber(new Object[]{}), is(retiringFederationCreationBlockNumber));
    }

    @Test
    void getRetiringFederatorPublicKey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederatorBtcPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Integer>getArgument(0)).toByteArray());
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);


        assertTrue(Arrays.equals(new byte[]{10},
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)}))
                )[0]
        ));

        assertTrue(Arrays.equals(new byte[]{20},
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(20)}))
                )[0]
        ));

        assertTrue(Arrays.equals(new byte[]{1, 0},
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(256)}))
                )[0]
        ));
    }

    @Test
    void getRetiringFederatorPublicKey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)})));
        verify(bridgeSupportMock, never()).getRetiringFederatorBtcPublicKey(any(int.class));
    }

    @Test
    void getRetiringFederatorPublicKeyOfType_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        assertNull(bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"})));
        verify(bridgeSupportMock, never()).getRetiringFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class));
    }

    @Test
    void getRetiringFederatorPublicKeyOfType_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Number>getArgument(0).longValue()).toString()
                        .concat((invocation.<FederationMember.KeyType>getArgument(1)).getValue()).getBytes()
        );
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        assertTrue(Arrays.equals("10btc".getBytes(),
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"}))
                )[0]
        ));

        assertTrue(Arrays.equals("105rsk".getBytes(),
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(105), "rsk"}))
                )[0]
        ));

        assertTrue(Arrays.equals("232mst".getBytes(),
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(232), "mst"}))
                )[0]
        ));
    }

    @Test
    void getPendingFederationSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederationSize()).thenReturn(1234);

        assertEquals(1234, bridge.getPendingFederationSize(new Object[]{}).intValue());
    }

    @Test
    void getPendingFederatorPublicKey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederatorBtcPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Integer>getArgument(0)).toByteArray());
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        assertArrayEquals(new byte[]{10}, (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
            bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)}))
        )[0]);

        assertArrayEquals(new byte[]{20}, (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
            bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(20)}))
        )[0]);

        assertArrayEquals(new byte[]{1, 0}, (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
            bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(256)}))
        )[0]);
    }

    @Test
    void getPendingFederatorPublicKey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        assertNull(bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)})));
        verify(bridgeSupportMock, never()).getPendingFederatorBtcPublicKey(any(int.class));
    }

    @Test
    void getPendingFederatorPublicKeyOfType_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        assertNull(bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"})));
        verify(bridgeSupportMock, never()).getPendingFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class));
    }

    @Test
    void getPendingFederatorPublicKeyOfType_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Number>getArgument(0).longValue()).toString()
                        .concat((invocation.<FederationMember.KeyType>getArgument(1)).getValue()).getBytes()
        );
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        assertArrayEquals("10btc".getBytes(), (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
            bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"}))
        )[0]);

        assertArrayEquals("82rsk".getBytes(), (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
            bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(82), "rsk"}))
        )[0]);

        assertArrayEquals(
            "123mst".getBytes(),
            (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(BigInteger.valueOf(123), "mst"))
            )[0]
        );
    }

    @Test
    void createFederation() {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("create", new byte[][]{}))).thenReturn(123);

        assertEquals(123, bridge.createFederation(new Object[]{}).intValue());
    }

    @Test
    void addFederatorPublicKey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("add", new byte[][]{Hex.decode("aabbccdd")})))
                .thenReturn(123);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        assertEquals(123,
                ((BigInteger) BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{Hex.decode("aabbccdd")}))
                )[0]).intValue()
        );
    }

    @Test
    void addFederatorPublicKey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);


        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        assertNull(
                bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{Hex.decode("aabbccdd")}))
        );

        verify(bridgeSupportMock, never()).voteFederationChange(any(Transaction.class), any(ABICallSpec.class));
    }

    @Test
    void addFederatorPublicKeyMultikey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);


        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        assertNull(
                bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction().encode(new Object[]{
                        Hex.decode("aabb"), Hex.decode("ccdd"), Hex.decode("eeff")
                }))
        );

        verify(bridgeSupportMock, never()).voteFederationChange(any(Transaction.class), any(ABICallSpec.class));
    }

    @Test
    void addFederatorPublicKeyMultikey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("add-multi", new byte[][]{
                Hex.decode("aabb"), Hex.decode("ccdd"), Hex.decode("eeff")
        }))).thenReturn(123);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);

        assertEquals(123,
                ((BigInteger) BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction().encode(new Object[]{
                                Hex.decode("aabb"), Hex.decode("ccdd"), Hex.decode("eeff")
                        }))
                )[0]).intValue()
        );
    }

    @Test
    void commitFederation_ok() {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("commit", new byte[][]{Hex.decode("01020304")}))).thenReturn(123);

        assertEquals(123, bridge.commitFederation(new Object[]{Hex.decode("01020304")}).intValue());
    }

    @Test
    void commitFederation_wrongParameterType() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        assertEquals(-10, bridge.commitFederation(new Object[]{"i'm not a byte array"}).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    void rollbackFederation() {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("rollback", new byte[][]{}))).thenReturn(456);

        assertEquals(456, bridge.rollbackFederation(new Object[]{}).intValue());
    }

    @Test
    void getLockWhitelistSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistSize()).thenReturn(1234);

        assertEquals(1234, bridge.getLockWhitelistSize(new Object[]{}).intValue());
    }

    @Test
    void getLockWhitelistAddress() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        OneOffWhiteListEntry mockedEntry10 = new OneOffWhiteListEntry(new BtcECKey().toAddress(regtestParameters), Coin.COIN);
        OneOffWhiteListEntry mockedEntry20 = new OneOffWhiteListEntry(new BtcECKey().toAddress(regtestParameters), Coin.COIN);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistEntryByIndex(10)).then((InvocationOnMock invocation) -> Optional.of(mockedEntry10));
        when(bridgeSupportMock.getLockWhitelistEntryByIndex(20)).then((InvocationOnMock invocation) -> Optional.of(mockedEntry20));

        assertEquals(mockedEntry10.address().toBase58(), bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(10)}));
        assertEquals(mockedEntry20.address().toBase58(), bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(20)}));
    }

    @Test
    void getLockWhitelistEntryByAddressBeforeRskip87And88Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        Address address = new BtcECKey().toAddress(regtestParameters);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        assertNull(bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{address.toBase58()})));
    }

    @Test
    void getLockWhitelistEntryByAddressAfterRskip87Fork() throws Exception {
        byte[] result;
        Transaction mockedTransaction;

        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Address mockedAddressForUnlimited = new BtcECKey().toAddress(regtestParameters);
        Address mockedAddressForOneOff = new BtcECKey().toAddress(regtestParameters);


        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getLockWhitelistEntryByAddress(mockedAddressForUnlimited.toBase58()))
                .then((InvocationOnMock invocation) -> Optional.of(new UnlimitedWhiteListEntry(mockedAddressForUnlimited)));
        when(bridgeSupportMock.getLockWhitelistEntryByAddress(mockedAddressForOneOff.toBase58()))
                .then((InvocationOnMock invocation) -> Optional.of(new OneOffWhiteListEntry(mockedAddressForOneOff, Coin.COIN)));

        mockedTransaction = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        // Get the unlimited whitelist address
        result = bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{mockedAddressForUnlimited.toBase58()}));
        BigInteger decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        assertEquals(0, decodedResult.longValue());

        // Get the one-off whitelist address
        result = bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{mockedAddressForOneOff.toBase58()}));
        decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        assertEquals(Coin.COIN.value, decodedResult.longValue());

        // Try fetch an unexisting address
        result = bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{(new BtcECKey().toAddress(regtestParameters)).toBase58()}));
        decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        assertEquals(-1, decodedResult.longValue());
    }

    @Test
    void addLockWhitelistAddressBeforeRskip87Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(federatorECKey.getAddress());
        when(mockedTransaction.getSender(any(SignatureCache.class))).thenReturn(sender);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(regtestParameters).toBase58(),
                BigInteger.valueOf(Coin.COIN.getValue())
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];

        assertEquals(WhitelistResponseCode.GENERIC_ERROR.getCode(), decodedResult.intValue());
    }

    @Test
    void addLockWhitelistAddressAfterRskip87And88Fork() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        try {
            bridge.execute(Bridge.ADD_LOCK_WHITELIST_ADDRESS.encode("i-am-an-address", BigInteger.valueOf(25L)));
            fail();
        } catch (Exception e) {
            Throwable causeException = e.getCause();
            assertEquals(BridgeIllegalArgumentException.class, causeException.getClass());
            assertTrue(causeException.getMessage().startsWith("Invalid data given"));
        }
    }

    @Test
    void addOneOffLockWhitelistAddressBeforeRskip87And88Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        assertNull(bridge.execute(Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.encode(new Object[]{"i-am-an-address", BigInteger.valueOf(25L)})));
    }

    @Test
    void addOneOffLockWhitelistAddressAfterRskip87Fork() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(federatorECKey.getAddress());
        when(mockedTransaction.getSender(any(SignatureCache.class))).thenReturn(sender);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(regtestParameters).toBase58(),
                BigInteger.valueOf(Coin.COIN.getValue())
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];

        assertEquals(WhitelistResponseCode.GENERIC_ERROR.getCode(), decodedResult.intValue());
    }

    @Test
    void addUnlimitedLockWhitelistAddressBeforeRskip87And88Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        assertNull(bridge.execute(Bridge.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.encode(new Object[]{"i-am-an-address"})));
    }

    @Test
    void addUnlimitedLockWhitelistAddressAfterRskip87Fork() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(federatorECKey.getAddress());
        when(mockedTransaction.getSender(any(SignatureCache.class))).thenReturn(sender);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(regtestParameters).toBase58()
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];
        bridge.init(mockedTransaction, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        assertEquals(WhitelistResponseCode.GENERIC_ERROR.getCode(), decodedResult.intValue());
    }

    @Test
    void removeLockWhitelistAddress() {
        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(mockedTransaction, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.removeLockWhitelistAddress(mockedTransaction, "i-am-an-address")).thenReturn(1234);

        assertEquals(1234, bridge.removeLockWhitelistAddress(new Object[]{"i-am-an-address"}).intValue());
    }

    @Test
    void getFeePerKb() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFeePerKb())
                .thenReturn(Coin.valueOf(12345678901234L));

        assertEquals(12345678901234L, bridge.getFeePerKb(new Object[]{}));
    }

    @Test
    void voteFeePerKb_ok() {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFeePerKbChange(txMock, Coin.valueOf(2)))
                .thenReturn(123);

        assertEquals(123, bridge.voteFeePerKbChange(new Object[]{BigInteger.valueOf(2)}).intValue());
    }

    @Test
    void voteFeePerKb_wrongParameterType() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        assertEquals(-10, bridge.voteFeePerKbChange(new Object[]{"i'm not a byte array"}).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    void precompiledContractAddress() {
        byte[] bridgeAddressSerialized = BRIDGE_ADDRESS.getBytes();

        Assertions.assertArrayEquals(
                bridgeAddressSerialized,
                Hex.decode(BRIDGE_ADDRESS_TO_STRING));
        Assertions.assertArrayEquals(
                bridgeAddressSerialized,
                HexUtils.stringHexToByteArray(BRIDGE_ADDRESS_TO_STRING));
    }

    @Test
    void testBlock457BridgeCall() throws Exception {
        // block 457 in mainnet exposed a bug in a fix made to SolidityType. The purpose of this test is to make sure this block keeps working
        // block 457 was the first federate call.
        byte[] data = Files.readAllBytes(Paths.get(this.getClass().getResource("/bridge/block457.bin").toURI()));

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = spy(Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build());
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        // Setup bridge
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);

        Genesis genesisBlock = new BlockGenerator().getGenesisBlock();
        ActivationConfig.ForBlock spiedActivations = spy(activationConfig.forBlock(genesisBlock.getNumber()));
        when(activationConfig.forBlock(genesisBlock.getNumber())).thenReturn(spiedActivations);

        bridge.init(rskTx, genesisBlock, track, null, null, null);

        bridge.execute(data);
        verify(spiedActivations, times(1)).isActive(ConsensusRule.RSKIP88);
        verify(rskTx, never()).isLocalCallTransaction();
    }

    @Test
    void executeMethodWithOnlyLocalCallsAllowed_localCallTx() throws Exception {
        Transaction tx = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Address address = new BtcECKey().toAddress(regtestParameters);
        when(bridgeSupportMock.getActiveFederationAddress()).thenReturn(address);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).getActiveFederationAddress();
    }

    @Test
    void executeMethodWithOnlyLocalCallsAllowed_nonLocalCallTx_beforeOrchid() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Transaction tx = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        Address expectedResult = new BtcECKey().toAddress(regtestParameters);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederationAddress()).thenReturn(expectedResult);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
        String result = (String) BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().decodeResult(bridge.execute(data))[0];
        assertEquals(expectedResult.toBase58(), result);
        bridge.execute(data);

        // TODO improve test
        Assertions.assertNotNull(data);
    }

    @Test
    void executeMethodWithOnlyLocalCallsAllowed_nonLocalCallTx() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        try {
            Transaction tx = mock(Transaction.class);
            when(tx.isLocalCallTransaction()).thenReturn(false);
            BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

            Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                    bridgeSupportFactoryMock, signatureCache);
            when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
            bridge.init(tx, getGenesisBlock(), null, null, null, null);

            byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
            bridge.execute(data);
            fail();
        } catch (VMException e) {
            verify(bridgeSupportMock, never()).getActiveFederationAddress();
            assertTrue(e.getMessage().contains("Non-local-call"));
        }
    }

    @Test
    void executeMethodWithAnyCallsAllowed_localCallTx() throws Exception {
        executeAndCheckMethodWithAnyCallsAllowed();
    }

    @Test
    void executeMethodWithAnyCallsAllowed_nonLocalCallTx() throws Exception {
        executeAndCheckMethodWithAnyCallsAllowed();
    }

    @Test
    void getBtcTransactionConfirmationsBeforeWasabi() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP122), anyLong());

        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);

        byte[][] arr = new byte[1][];
        arr[0] = new byte[]{};
        Object[] params = new Object[]{new byte[0], new byte[0], BigInteger.valueOf(1), arr};
        assertNull(bridge.execute(Bridge.GET_BTC_TRANSACTION_CONFIRMATIONS.encode(params)));
    }

    @Test
    void getBtcTransactionConfirmationsAfterWasabi_ok() throws Exception {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        BiFunction<List<Sha256Hash>, Integer, MerkleBranch> merkleBranchFactory = mock(BiFunction.class);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, merkleBranchFactory, signatureCache);

        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
        byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
        byte[][] merkleBranchHashes = new byte[][]{
                Sha256Hash.of(Hex.decode("11")).getBytes(),
                Sha256Hash.of(Hex.decode("22")).getBytes(),
                Sha256Hash.of(Hex.decode("33")).getBytes(),
        };
        BigInteger merkleBranchBits = BigInteger.valueOf(123);

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranchFactory.apply(any(), any())).then((Answer<MerkleBranch>) invocation -> {
            // Check constructor parameters are correct

            List<Sha256Hash> hashes = invocation.getArgument(0);
            Assertions.assertArrayEquals(merkleBranchHashes[0], hashes.get(0).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[1], hashes.get(1).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[2], hashes.get(2).getBytes());

            Integer bits = invocation.getArgument(1);
            assertEquals(123, bits.intValue());

            return merkleBranch;
        });

        when(bridgeSupportMock.getBtcTransactionConfirmations(any(Sha256Hash.class), any(Sha256Hash.class), any(MerkleBranch.class))).then((Answer<Integer>) invocation -> {
            // Check parameters are correct
            Sha256Hash txHash = invocation.getArgument(0);
            Assertions.assertArrayEquals(btcTxHash, txHash.getBytes());

            Sha256Hash blockHash = invocation.getArgument(1);
            Assertions.assertArrayEquals(btcBlockHash, blockHash.getBytes());

            MerkleBranch merkleBranchArg = invocation.getArgument(2);
            assertEquals(merkleBranch, merkleBranchArg);

            return 78;
        });

        assertEquals(78, bridge.getBtcTransactionConfirmations(new Object[]{
                btcTxHash,
                btcBlockHash,
                merkleBranchBits,
                merkleBranchHashes
        }));
    }

    @Test
    void getBtcTransactionConfirmationsAfterWasabi_errorInBridgeSupport() throws Exception {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);

        BiFunction<List<Sha256Hash>, Integer, MerkleBranch> merkleBranchFactory = mock(BiFunction.class);
        Bridge bridge = spy(new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, merkleBranchFactory, signatureCache));
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
        byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
        byte[][] merkleBranchHashes = new byte[][]{
                Sha256Hash.of(Hex.decode("11")).getBytes(),
                Sha256Hash.of(Hex.decode("22")).getBytes(),
                Sha256Hash.of(Hex.decode("33")).getBytes(),
        };
        BigInteger merkleBranchBits = BigInteger.valueOf(123);

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranchFactory.apply(any(), any())).then((Answer<MerkleBranch>) invocation -> {
            // Check constructor parameters are correct

            List<Sha256Hash> hashes = invocation.getArgument(0);
            Assertions.assertArrayEquals(merkleBranchHashes[0], hashes.get(0).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[1], hashes.get(1).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[2], hashes.get(2).getBytes());

            Integer bits = invocation.getArgument(1);
            assertEquals(123, bits.intValue());

            return merkleBranch;
        });

        when(bridgeSupportMock.getBtcTransactionConfirmations(any(Sha256Hash.class), any(Sha256Hash.class), any(MerkleBranch.class))).then((Answer<Integer>) invocation -> {
            // Check parameters are correct
            Sha256Hash txHash = invocation.getArgument(0);
            Assertions.assertArrayEquals(btcTxHash, txHash.getBytes());

            Sha256Hash blockHash = invocation.getArgument(1);
            Assertions.assertArrayEquals(btcBlockHash, blockHash.getBytes());

            MerkleBranch merkleBranchArg = invocation.getArgument(2);
            assertEquals(merkleBranch, merkleBranchArg);

            throw new VMException("bla bla bla");
        });

        try {
            bridge.getBtcTransactionConfirmations(new Object[]{
                    btcTxHash,
                    btcBlockHash,
                    merkleBranchBits,
                    merkleBranchHashes
            });
            fail();
        } catch (VMException e) {
            assertTrue(e.getMessage().contains("in getBtcTransactionConfirmations"));
            assertEquals(VMException.class, e.getCause().getClass());
            assertTrue(e.getCause().getMessage().contains("bla bla bla"));
        }
    }

    @Test
    void getBtcTransactionConfirmationsAfterWasabi_merkleBranchConstructionError() throws Exception {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);

        BiFunction<List<Sha256Hash>, Integer, MerkleBranch> merkleBranchFactory = mock(BiFunction.class);
        Bridge bridge = spy(new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, merkleBranchFactory, signatureCache));
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
        byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
        byte[][] merkleBranchHashes = new byte[][]{
                Sha256Hash.of(Hex.decode("11")).getBytes(),
                Sha256Hash.of(Hex.decode("22")).getBytes(),
                Sha256Hash.of(Hex.decode("33")).getBytes(),
        };
        BigInteger merkleBranchBits = BigInteger.valueOf(123);

        when(merkleBranchFactory.apply(any(), any())).then((Answer<MerkleBranch>) invocation -> {
            // Check constructor parameters are correct

            List<Sha256Hash> hashes = invocation.getArgument(0);
            Assertions.assertArrayEquals(merkleBranchHashes[0], hashes.get(0).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[1], hashes.get(1).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[2], hashes.get(2).getBytes());

            Integer bits = invocation.getArgument(1);
            assertEquals(123, bits.intValue());

            throw new IllegalArgumentException("blabla");
        });

        try {
            bridge.getBtcTransactionConfirmations(new Object[]{
                    btcTxHash,
                    btcBlockHash,
                    merkleBranchBits,
                    merkleBranchHashes
            });
            fail();
        } catch (VMException e) {
            assertTrue(e.getMessage().contains("in getBtcTransactionConfirmations"));
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            verify(bridgeSupportMock, never()).getBtcTransactionConfirmations(any(), any(), any());
        }
    }

    @Test
    void getBtcTransactionConfirmations_gasCost() {
        Transaction txMock = mock(Transaction.class);
        doReturn(true).when(activationConfig).isActive(eq(RSKIP122), anyLong());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        Bridge bridge = spy(new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache));
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.isContractTx(any(Transaction.class))).thenReturn(false);

            byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
            byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
            byte[][] merkleBranchHashes = new byte[][]{
                    Sha256Hash.of(Hex.decode("11")).getBytes(),
                    Sha256Hash.of(Hex.decode("22")).getBytes(),
                    Sha256Hash.of(Hex.decode("33")).getBytes(),
            };
            BigInteger merkleBranchBits = BigInteger.valueOf(123);

            Object[] args = new Object[]{
                    btcTxHash,
                    btcBlockHash,
                    merkleBranchBits,
                    merkleBranchHashes
            };

            when(bridgeSupportMock.getBtcTransactionConfirmationsGetCost(eq(args))).thenReturn(1234L); // NOSONAR: eq is needed
            CallTransaction.Function fn = BridgeMethods.GET_BTC_TRANSACTION_CONFIRMATIONS.getFunction();

            byte[] data = fn.encode(args);

            assertEquals(2 * data.length + 1234L, bridge.getGasForData(data));
        }
    }

    @Test
    void getBtcBlockchainBlockHashAtDepth() throws BlockStoreException, IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Sha256Hash mockedResult = Sha256Hash.of(Hex.decode("aabbcc"));
        when(bridgeSupportMock.getBtcBlockchainBlockHashAtDepth(555)).thenReturn(mockedResult);

        assertEquals(mockedResult, Sha256Hash.wrap(bridge.getBtcBlockchainBlockHashAtDepth(new Object[]{BigInteger.valueOf(555)})));
    }

    @Test
    void testCallFromContract_beforeOrchid() {
        blockFactory = new BlockFactory(config.getActivationConfig());

        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, signatureCache);
        EVMAssembler assembler = new EVMAssembler();
        ProgramInvoke invoke = new ProgramInvokeMockImpl();

        // Save code on the sender's address so that the bridge
        // thinks its being called by a contract
        byte[] callerCode = assembler.assemble("0xaabb 0xccdd 0xeeff");
        invoke.getRepository().saveCode(new RskAddress(invoke.getOwnerAddress().getLast20Bytes()), callerCode);

        VM vm = new VM(config.getVmConfig(), precompiledContracts);

        // Encode a call to the bridge's getMinimumLockTxValue function
        // That means first pushing the corresponding encoded ABI storage to memory (MSTORE)
        // and then doing a DELEGATECALL to the corresponding address with the correct parameters
        String bridgeFunctionHex = ByteUtil.toHexString(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode());
        bridgeFunctionHex = String.format("0x%s%s", bridgeFunctionHex, String.join("", Collections.nCopies(32 * 2 - bridgeFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x0000000000000000000000000000000001000006 0x6000 DELEGATECALL", bridgeFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb"));

        // Run the program on the VM
        Program program = new Program(config.getVmConfig(), precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, tx, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertThrows(NullPointerException.class, () -> {
            for (int i = 0; i < numOps; i++) {
                vm.step(program);
            }
        });
    }

    @Test
    void testCallFromContract_afterOrchid() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        blockFactory = new BlockFactory(activationConfig);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig,
                signatureCache);

        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config,
                bridgeSupportFactory, signatureCache);
        EVMAssembler assembler = new EVMAssembler();
        ProgramInvoke invoke = new ProgramInvokeMockImpl();

        // Save code on the sender's address so that the bridge
        // thinks its being called by a contract
        byte[] callerCode = assembler.assemble("0xaabb 0xccdd 0xeeff");
        invoke.getRepository().saveCode(new RskAddress(invoke.getOwnerAddress().getLast20Bytes()), callerCode);

        VM vm = new VM(config.getVmConfig(), precompiledContracts);

        // Encode a call to the bridge's getMinimumLockTxValue function
        // That means first pushing the corresponding encoded ABI storage to memory (MSTORE)
        // and then doing a DELEGATECALL to the corresponding address with the correct parameters
        String bridgeFunctionHex = ByteUtil.toHexString(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode());
        bridgeFunctionHex = String.format("0x%s%s", bridgeFunctionHex, String.join("", Collections.nCopies(32 * 2 - bridgeFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x0000000000000000000000000000000001000006 0x6000 DELEGATECALL", bridgeFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb"));

        // Run the program on the VM
        Program program = new Program(config.getVmConfig(), precompiledContracts, blockFactory, activationConfig.forBlock(0), code, invoke, tx, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        try {
            for (int i = 0; i < numOps; i++) {
                vm.step(program);
            }
            fail();
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Non-local-call"));
        }
    }

    @Test
    void localCallOnlyMethodsDefinition() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        // To force initialization
        String foo = Bridge.UPDATE_COLLECTIONS.name;

        // Actual tests
        Arrays.asList(
                BridgeMethods.GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT,
                BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR,
                BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH,
                BridgeMethods.GET_BTC_TX_HASH_PROCESSED_HEIGHT,
                BridgeMethods.GET_FEDERATION_ADDRESS,
                BridgeMethods.GET_FEDERATION_CREATION_BLOCK_NUMBER,
                BridgeMethods.GET_FEDERATION_CREATION_TIME,
                BridgeMethods.GET_FEDERATION_SIZE,
                BridgeMethods.GET_FEDERATION_THRESHOLD,
                BridgeMethods.GET_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.GET_FEE_PER_KB,
                BridgeMethods.GET_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS,
                BridgeMethods.GET_LOCK_WHITELIST_SIZE,
                BridgeMethods.GET_MINIMUM_LOCK_TX_VALUE,
                BridgeMethods.GET_PENDING_FEDERATION_HASH,
                BridgeMethods.GET_PENDING_FEDERATION_SIZE,
                BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.GET_RETIRING_FEDERATION_ADDRESS,
                BridgeMethods.GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER,
                BridgeMethods.GET_RETIRING_FEDERATION_CREATION_TIME,
                BridgeMethods.GET_RETIRING_FEDERATION_SIZE,
                BridgeMethods.GET_RETIRING_FEDERATION_THRESHOLD,
                BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.GET_STATE_FOR_BTC_RELEASE_CLIENT,
                BridgeMethods.GET_STATE_FOR_DEBUGGING,
                BridgeMethods.IS_BTC_TX_HASH_ALREADY_PROCESSED
        ).forEach(m -> {
            assertTrue(m.onlyAllowsLocalCalls(bridge, new Object[0]));
        });
    }

    @Test
    void mineableMethodsDefinition() {
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, null, signatureCache);

        // To force initialization
        String foo = Bridge.UPDATE_COLLECTIONS.name;

        // Actual tests
        Arrays.asList(
                BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.ADD_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ADD_SIGNATURE,
                BridgeMethods.COMMIT_FEDERATION,
                BridgeMethods.CREATE_FEDERATION,
                BridgeMethods.RECEIVE_HEADERS,
                BridgeMethods.REGISTER_BTC_TRANSACTION,
                BridgeMethods.RELEASE_BTC,
                BridgeMethods.REMOVE_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ROLLBACK_FEDERATION,
                BridgeMethods.SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY,
                BridgeMethods.UPDATE_COLLECTIONS,
                BridgeMethods.VOTE_FEE_PER_KB,
                BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT
        ).stream().forEach(m -> {
            Assertions.assertFalse(m.onlyAllowsLocalCalls(bridge, new Object[0]));
        });
    }

    @Test
    void getBtcBlockchainBestChainHeight_beforeRskip220_isMineable() {
        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(RSKIP220)).thenReturn(true);

        Bridge bridge = getBridgeInstance(activationsMock);

        Assertions.assertFalse(BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.onlyAllowsLocalCalls(bridge, new Object[0]));
    }

    @Test
    void getBtcBlockchainBestChainHeight_afterRskip220_onlyAllowsLocalCalls() {
        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(RSKIP220)).thenReturn(false);

        Bridge bridge = getBridgeInstance(activationsMock);

        assertTrue(BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.onlyAllowsLocalCalls(bridge, new Object[0]));
    }

    @Test
    void receiveHeadersGasCost_beforeDynamicCost() {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getReceiveAddress()).thenReturn(RskAddress.nullAddress());
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                mock(BridgeSupportFactory.class), signatureCache);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        for (int numberOfHeaders = 0; numberOfHeaders < 10; numberOfHeaders++) {
            byte[][] headers = new byte[numberOfHeaders][];
            for (int i = 0; i < numberOfHeaders; i++) headers[i] = Hex.decode("00112233445566778899");

            byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

            assertEquals(22000L + 2 * data.length, bridge.getGasForData(data));
        }
    }

    @Test
    void receiveHeadersGasCost_afterDynamicCost() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getReceiveAddress()).thenReturn(RskAddress.nullAddress());
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                mock(BridgeSupportFactory.class), signatureCache);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        final long BASE_COST = 66_000L;
        for (int numberOfHeaders = 0; numberOfHeaders < 10; numberOfHeaders++) {
            byte[][] headers = new byte[numberOfHeaders][];
            for (int i = 0; i < numberOfHeaders; i++) headers[i] = Hex.decode("00112233445566778899");

            byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

            long cost = BASE_COST + 2 * data.length;
            if (numberOfHeaders > 1) {
                cost += 1650L * (numberOfHeaders - 1);
            }
            assertEquals(cost, bridge.getGasForData(data));
        }
    }

    @Test
    void receiveHeadersAccess_beforePublic_noAccessIfNotFromFederationMember() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        RskAddress sender = new RskAddress("2acc95758f8b5f583470ba265eb685a8f45fc9d5");
        Transaction txMock = mock(Transaction.class);
        when(txMock.getSender(any(SignatureCache.class))).thenReturn(sender);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getRetiringFederation()).thenReturn(Optional.empty());
        when(bridgeSupportMock.getActiveFederation()).thenReturn(FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants()));

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        byte[][] headers = new byte[][]{Hex.decode(
                "0000002006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910ff698ee112158f5573a90b7403cfba074addd61c547b3639c6afdcf52588eb8e2a1ef825cffff7f2000000000"
        )};

        byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

        try {
            bridge.execute(data);
            fail();
        } catch (VMException e) {
            assertTrue(e.getMessage().contains("The sender is not a member of the active"));
        }
        verify(bridgeSupportMock, never()).receiveHeaders(any(BtcBlock[].class));
    }

    @Test
    void receiveHeadersAccess_beforePublic_accessIfFromFederationMember() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        byte[] privKeyBytes = REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes();
        RskAddress sender = new RskAddress(ECKey.fromPrivate(privKeyBytes).getAddress());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getSender(any(SignatureCache.class))).thenReturn(sender);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getRetiringFederation()).thenReturn(Optional.empty());
        when(bridgeSupportMock.getActiveFederation()).thenReturn(FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants()));

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        byte[][] headers = new byte[][]{Hex.decode(
                "0000002006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910ff698ee112158f5573a90b7403cfba074addd61c547b3639c6afdcf52588eb8e2a1ef825cffff7f2000000000"
        )};

        byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

        assertNull(bridge.execute(data));
        verify(bridgeSupportMock, times(1)).receiveHeaders(any(BtcBlock[].class));
    }

    @Test
    void receiveHeadersAccess_afterPublic_accessFromAnyone() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        byte[][] headers = new byte[][]{Hex.decode(
                "0000002006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910ff698ee112158f5573a90b7403cfba074addd61c547b3639c6afdcf52588eb8e2a1ef825cffff7f2000000000"
        )};

        byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

        assertNull(bridge.execute(data));
        verify(bridgeSupportMock, times(1)).receiveHeaders(any(BtcBlock[].class));
    }

    @Test
    void bridgeSupportIsCreatedOnInit() {
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), null, null, null, null);

        Assertions.assertNotNull(TestUtils.getInternalState(bridge, "bridgeSupport"));
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    public static Genesis getGenesisInstance(RskSystemProperties config) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new TestGenesisLoader(trieStore, config.genesisInfo(), config.getNetworkConstants().getInitialNonce(), false, false, false).load();
    }

    private void executeAndCheckMethodWithAnyCallsAllowed() throws Exception {
        Transaction tx = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactoryMock, signatureCache);


        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.voteFeePerKbChange(tx, Coin.CENT)).thenReturn(1);

        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        byte[] data = BridgeMethods.VOTE_FEE_PER_KB.getFunction().encode(new Object[]{Coin.CENT.longValue()});
        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).voteFeePerKbChange(tx, Coin.CENT);
    }

    @Test
    void getBtcBlockchainInitialBlockHeight() throws IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getBtcBlockchainInitialBlockHeight()).thenReturn(1234);

        assertEquals(1234, bridge.getBtcBlockchainInitialBlockHeight(new Object[]{}).intValue());
    }

    private BtcTransaction createTransaction() {
        return createTransaction(BitcoinTestUtils.createHash(1));
    }

    private BtcTransaction createTransaction(Sha256Hash hash) {
        return new SimpleBtcTransaction(regtestParameters, hash);
    }

    private BtcTransaction createTransaction(int toPk, Coin value) {
        return createTransaction(toPk, value, new BtcECKey());
    }

    private BtcTransaction createTransaction(int toPk, Coin value, BtcECKey btcECKey) {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BtcTransaction input = new BtcTransaction(params);

        input.addOutput(Coin.COIN, btcECKey.toAddress(params));

        Address to = BtcECKey.fromPrivate(BigInteger.valueOf(toPk)).toAddress(params);

        BtcTransaction result = new BtcTransaction(params);
        result.addInput(input.getOutput(0));
        result.getInput(0).disconnect();
        result.addOutput(value, to);
        return result;
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }

    private void getGasForDataPaidTx(int expected, CallTransaction.Function function, Object... funcArgs) {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        org.ethereum.core.Transaction rskTx;
        if (function == null) {
            rskTx = CallTransaction.createRawTransaction(
                    0,
                    1,
                    1,
                BRIDGE_ADDRESS,
                    0,
                    new byte[]{1, 2, 3},
                    Constants.REGTEST_CHAIN_ID
            );
        } else {
            rskTx = CallTransaction.createCallTransaction(
                    0,
                    1,
                    1,
                BRIDGE_ADDRESS,
                    0,
                    function,
                    Constants.REGTEST_CHAIN_ID,
                    funcArgs
            );
        }

        rskTx.sign(ACTIVE_FEDERATION_KEYS.get(0).getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator();
        Block rskExecutionBlock = blockGenerator.createChildBlock(getGenesisInstance(config));
        for (int i = 0; i < 20; i++) {
            rskExecutionBlock = blockGenerator.createChildBlock(rskExecutionBlock);
        }

        Repository mockRepository = mock(Repository.class);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        assertEquals(expected, bridge.getGasForData(rskTx.getData()));
    }

    private void registerBtcTransactionWithHugeDeclaredSize(BtcTransaction tx) throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(BRIDGE_ADDRESS_TO_STRING))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(federatorECKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(regtestParameters),
                bridgeRegTestConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig,
                bridgeSupportFactory, signatureCache);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] serializedTx = tx.bitcoinSerialize();

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(serializedTx, 1, new byte[30]);

        assertNull(bridge.execute(data));
    }

    private Bridge getBridgeInstance(ActivationConfig.ForBlock activations) {
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(mock(BridgeSupport.class));

        when(activationConfig.forBlock(anyLong())).thenReturn(activations);
        Bridge bridge = new Bridge(BRIDGE_ADDRESS, constants, activationConfig, bridgeSupportFactoryMock, signatureCache);
        bridge.init(mock(Transaction.class), getGenesisBlock(), null, null, null, null);

        return bridge;
    }

    private static class HugeDeclaredSizeBtcTransaction extends BtcTransaction {

        private final boolean hackInputsSize;
        private final boolean hackOutputsSize;
        private final boolean hackWitnessPushCountSize;
        private final boolean hackWitnessPushSize;

        public HugeDeclaredSizeBtcTransaction(NetworkParameters params, boolean hackInputsSize, boolean hackOutputsSize, boolean hackWitnessPushCountSize, boolean hackWitnessPushSize) {
            super(params);
            BtcTransaction inputTx = new BtcTransaction(params);
            inputTx.addOutput(Coin.FIFTY_COINS, BtcECKey.fromPrivate(BigInteger.valueOf(123456)).toAddress(params));
            Address to = BtcECKey.fromPrivate(BigInteger.valueOf(1000)).toAddress(params);
            this.addInput(inputTx.getOutput(0));
            this.getInput(0).disconnect();
            TransactionWitness witness = new TransactionWitness(1);
            witness.setPush(0, new byte[]{0});
            this.setWitness(0, witness);
            this.addOutput(Coin.COIN, to);

            this.hackInputsSize = hackInputsSize;
            this.hackOutputsSize = hackOutputsSize;
            this.hackWitnessPushCountSize = hackWitnessPushCountSize;
            this.hackWitnessPushSize = hackWitnessPushSize;
        }

        @Override
        protected void bitcoinSerializeToStream(OutputStream stream, boolean serializeWitRequested) throws IOException {
            boolean serializeWit = serializeWitRequested && hasWitness();
            uint32ToByteStreamLE(getVersion(), stream);
            if (serializeWit) {
                stream.write(new byte[]{0, 1});
            }

            long inputsSize = hackInputsSize ? Integer.MAX_VALUE : getInputs().size();
            stream.write(new VarInt(inputsSize).encode());
            for (TransactionInput in : getInputs()) {
                in.bitcoinSerialize(stream);
            }
            long outputsSize = hackOutputsSize ? Integer.MAX_VALUE : getOutputs().size();
            stream.write(new VarInt(outputsSize).encode());
            for (TransactionOutput out : getOutputs()) {
                out.bitcoinSerialize(stream);
            }
            if (serializeWit) {
                for (int i = 0; i < getInputs().size(); i++) {
                    TransactionWitness witness = getWitness(i);
                    long pushCount = hackWitnessPushCountSize ? Integer.MAX_VALUE : witness.getPushCount();
                    stream.write(new VarInt(pushCount).encode());
                    for (int y = 0; y < witness.getPushCount(); y++) {
                        byte[] push = witness.getPush(y);
                        long pushLength = hackWitnessPushSize ? Integer.MAX_VALUE : push.length;
                        stream.write(new VarInt(pushLength).encode());
                        stream.write(push);
                    }
                }
            }
            uint32ToByteStreamLE(getLockTime(), stream);
        }
    }
}
