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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.db.RepositoryImpl;
import co.rsk.peg.bitcoin.SimpleBtcTransaction;
import co.rsk.test.World;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/8/2016.
 */
public class BridgeTest {

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static TestSystemProperties config = new TestSystemProperties();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        config.setBlockchainConfig(new RegTestConfig());
        bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmation() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.getReleaseTransactionSet().add(tx2, 2L);
        provider0.getReleaseTransactionSet().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();

        Transaction rskTx = Transaction.create(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        World world = new World();
        bridge.init(rskTx, world.getBlockChain().getBestBlock(), track, world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(3, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.getReleaseTransactionSet().add(tx2, 2L);
        provider0.getReleaseTransactionSet().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();

        World world = new World();
        List<Block> blocks = new BlockGenerator().getSimpleBlockChain(world.getBlockChain().getBestBlock(), 10);

        Transaction rskTx = Transaction.create(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        world.getBlockChain().getBlockStore().saveBlock(blocks.get(1), new BlockDifficulty(BigInteger.ONE), true);

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, blocks.get(9), track, world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(2, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(1, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void sendNoBlockHeader() throws IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        bridge.execute(Bridge.RECEIVE_HEADERS.encode());

        track.commit();
    }

    @Test
    public void sendOrphanBlockHeader() throws IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(networkParameters, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, Utils.encodeCompactBits(networkParameters.getMaxTarget()), 1, new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        Object[] objectArray = new Object[headers.length];

        for (int i = 0; i < headers.length; i++)
            objectArray[i] = headers[i].bitcoinSerialize();

        bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray}));

        track.commit();
    }

    @Test
    public void executeWithFunctionSignatureLengthTooShort() throws Exception{
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Assert.assertNull(bridge.execute(new byte[3]));
    }


    @Test
    public void executeWithInexistentFunction() throws Exception{
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Assert.assertNull(bridge.execute(new byte[4]));
    }


    @Test
    public void receiveHeadersWithNonParseableHeader() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        Object[] objectArray = new Object[1];
        objectArray[0] = new byte[60];

        byte[] data = Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray});

        Assert.assertNull(bridge.execute(data));

    }

    @Test
    public void registerBtcTransactionWithNonParseableTx() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithNonParseableMerkleeProof1() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[3]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithNonParseableMerkleeProof2() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[30]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void getFederationAddress() throws Exception {
        // Case with genesis federation
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] data = Bridge.GET_FEDERATION_ADDRESS.encode();

        Assert.assertArrayEquals(Bridge.GET_FEDERATION_ADDRESS.encodeOutputs(federation.getAddress().toString()), bridge.execute(data));
    }

    @Test
    public void getMinimumLockTxValue() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();


        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] data = Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode();

        Assert.assertArrayEquals(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encodeOutputs(bridgeConstants.getMinimumLockTxValue().value), bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseablePublicKey() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] federatorPublicKeySerialized = new byte[3];
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithEmptySignatureArray() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableSignature() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new byte[3]};
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableRskTx() throws Exception{
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new BtcECKey().sign(Sha256Hash.ZERO_HASH).encodeToDER()};
        byte[] rskTxHash = new byte[3];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void exceptionInUpdateCollection() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.updateCollections(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception onBlock", ex.getMessage());
        }
    }

    @Test
    public void exceptionInReleaseBtc() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.releaseBtc(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in releaseBtc", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetStateForBtcReleaseClient() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge. getStateForBtcReleaseClient(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getStateForBtcReleaseClient", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetStateForDebugging() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.getStateForDebugging(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getStateForDebugging", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetBtcBlockchainBestChainHeight() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.getBtcBlockchainBestChainHeight(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getBtcBlockchainBestChainHeight", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetBtcBlockchainBlockLocator() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.getBtcBlockchainBlockLocator(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getBtcBlockchainBlockLocator", ex.getMessage());
        }
    }

    private BtcTransaction createTransaction() {
        return new SimpleBtcTransaction(networkParameters, PegTestUtils.createHash());
    }

    @Test
    public void getGasForDataFreeTx() {
        BlockchainNetConfig blockchainNetConfigOriginal = config.getBlockchainConfig();
        config.setBlockchainConfig(new UnitTestBlockchainNetConfig());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                config, 0,
                1,
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                0,
                Bridge.UPDATE_COLLECTIONS);
        rskTx.sign(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0).getPrivKeyBytes());

        Block rskExecutionBlock = new BlockGenerator().createChildBlock(Genesis.getInstance(config));
        bridge.init(rskTx, rskExecutionBlock, null, null, null, null);
        Assert.assertEquals(0, bridge.getGasForData(rskTx.getData()));

        config.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void getGasForDataInvalidFunction() {
        getGasForDataPaidTx(23000, null);
    }

    @Test
    public void getGasForDataUpdateCollections() {
        getGasForDataPaidTx(48000 + 8, Bridge.UPDATE_COLLECTIONS);
    }

    @Test
    public void getGasForDataReceiveHeaders() {
        getGasForDataPaidTx(22000 + 8, Bridge.RECEIVE_HEADERS);
    }

    @Test
    public void getGasForDataRegisterBtcTransaction() {
        getGasForDataPaidTx(22000 + 228*2, Bridge.REGISTER_BTC_TRANSACTION, new byte[3], 1, new byte[3]);
    }

    @Test
    public void getGasForDataReleaseBtc() {
        getGasForDataPaidTx(23000 + 8, Bridge.RELEASE_BTC);
    }

    @Test
    public void getGasForDataAddSignature() {
        getGasForDataPaidTx(70000 + 548*2, Bridge.ADD_SIGNATURE, new byte[3], new byte[3][2], new byte[3]);
    }
    @Test
    public void getGasForDataGSFBRC() {
        getGasForDataPaidTx(4000 + 8, Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT);
    }

    @Test
    public void getGasForDataGSFD() {
        getGasForDataPaidTx(3_000_000 + 8, Bridge.GET_STATE_FOR_DEBUGGING);
    }

    @Test
    public void getGasForDataGBBBCH() {
        getGasForDataPaidTx(19000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
    }

    @Test
    public void getGasForDataGBBBL() {
        getGasForDataPaidTx(76000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR);
    }

    @Test
    public void getGasForDataGetFederationAddress() {
        getGasForDataPaidTx(11000 + 8, Bridge.GET_FEDERATION_ADDRESS);
    }

    @Test
    public void getGasForDataGetMinimumLockTxValue() {
        getGasForDataPaidTx(2000 + 8, Bridge.GET_MINIMUM_LOCK_TX_VALUE);
    }

    private void getGasForDataPaidTx(int expected, CallTransaction.Function function, Object... funcArgs) {
        BlockchainNetConfig blockchainNetConfigOriginal = config.getBlockchainConfig();
        config.setBlockchainConfig(new UnitTestBlockchainNetConfig());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        org.ethereum.core.Transaction rskTx;
        if (function==null) {
            rskTx = CallTransaction.createRawTransaction(
                    config, 0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    new byte[]{1,2,3});
        } else {
            rskTx = CallTransaction.createCallTransaction(
                    config, 0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    function,
                    funcArgs);
        }

        rskTx.sign(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0).getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator();
        Block rskExecutionBlock = blockGenerator.createChildBlock(Genesis.getInstance(config));
        for (int i = 0; i < 20; i++) {
            rskExecutionBlock = blockGenerator.createChildBlock(rskExecutionBlock);
        }
        bridge.init(rskTx, rskExecutionBlock, null, null, null, null);
        Assert.assertEquals(expected, bridge.getGasForData(rskTx.getData()));

        config.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void isBtcTxHashAlreadyProcessed_normalFlow() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Set<Sha256Hash> hashes = new HashSet<>();
        when(bridgeSupportMock.isBtcTxHashAlreadyProcessed(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.contains(invocation.getArgumentAt(0, Sha256Hash.class)));

        hashes.add(Sha256Hash.of("hash_1".getBytes()));
        hashes.add(Sha256Hash.of("hash_2".getBytes()));
        hashes.add(Sha256Hash.of("hash_3".getBytes()));
        hashes.add(Sha256Hash.of("hash_4".getBytes()));

        for (Sha256Hash hash : hashes) {
            Assert.assertTrue(bridge.isBtcTxHashAlreadyProcessed(new Object[]{hash.toString()}));
            verify(bridgeSupportMock).isBtcTxHashAlreadyProcessed(hash);
        }
        Assert.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
        Assert.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("yetanotheranything".getBytes()).toString()}));
    }

    @Test
    public void isBtcTxHashAlreadyProcessed_exception() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        boolean thrown = false;
        try {
            bridge.isBtcTxHashAlreadyProcessed(new Object[]{"notahash"});
        } catch (RuntimeException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        verify(bridgeSupportMock, never()).isBtcTxHashAlreadyProcessed(any());
    }

    @Test
    public void getBtcTxHashProcessedHeight_normalFlow() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Map<Sha256Hash, Long> hashes = new HashMap<>();
        when(bridgeSupportMock.getBtcTxHashProcessedHeight(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.get(invocation.getArgumentAt(0, Sha256Hash.class)));

        hashes.put(Sha256Hash.of("hash_1".getBytes()), 1L);
        hashes.put(Sha256Hash.of("hash_2".getBytes()), 2L);
        hashes.put(Sha256Hash.of("hash_3".getBytes()), 3L);
        hashes.put(Sha256Hash.of("hash_4".getBytes()), 4L);

        for (Map.Entry<Sha256Hash, Long> entry : hashes.entrySet()) {
            Assert.assertEquals(entry.getValue(), bridge.getBtcTxHashProcessedHeight(new Object[]{entry.getKey().toString()}));
            verify(bridgeSupportMock).getBtcTxHashProcessedHeight(entry.getKey());
        }
        Assert.assertNull(bridge.getBtcTxHashProcessedHeight(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
    }

    @Test
    public void getBtcTxHashProcessedHeight_exception() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        boolean thrown = false;
        try {
            bridge.getBtcTxHashProcessedHeight(new Object[]{"notahash"});
        } catch (RuntimeException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        verify(bridgeSupportMock, never()).getBtcTxHashProcessedHeight(any());
    }

    @Test
    public void getFederationSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getFederationThreshold() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationThreshold()).thenReturn(5678);

        Assert.assertEquals(5678, bridge.getFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    public void getFederationCreationTime() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5000));

        Assert.assertEquals(5000, bridge.getFederationCreationTime(new Object[]{}).intValue());
    }

    @Test
    public void getFederationCreationBlockNumber() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationCreationBlockNumber()).thenReturn(42L);

        Assert.assertThat(bridge.getFederationCreationBlockNumber(new Object[]{}), is(42L));
    }

    @Test
    public void getFederatorPublicKey() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.getArgumentAt(0, int.class)).toByteArray());

        Assert.assertTrue(Arrays.equals(new byte[]{10}, bridge.getFederatorPublicKey(new Object[]{BigInteger.valueOf(10)})));
        Assert.assertTrue(Arrays.equals(new byte[]{20}, bridge.getFederatorPublicKey(new Object[]{BigInteger.valueOf(20)})));
        Assert.assertTrue(Arrays.equals(new byte[]{1, 0}, bridge.getFederatorPublicKey(new Object[]{BigInteger.valueOf(256)})));
    }

    @Test
    public void getRetiringFederationSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getRetiringFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationThreshold() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationThreshold()).thenReturn(5678);

        Assert.assertEquals(5678, bridge.getRetiringFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationCreationTime() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5000));

        Assert.assertEquals(5000, bridge.getRetiringFederationCreationTime(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationCreationBlockNumber() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationCreationBlockNumber()).thenReturn(42L);

        Assert.assertThat(bridge.getRetiringFederationCreationBlockNumber(new Object[]{}), is(42L));
    }

    @Test
    public void getRetiringFederatorPublicKey() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.getArgumentAt(0, int.class)).toByteArray());

        Assert.assertTrue(Arrays.equals(new byte[]{10}, bridge.getRetiringFederatorPublicKey(new Object[]{BigInteger.valueOf(10)})));
        Assert.assertTrue(Arrays.equals(new byte[]{20}, bridge.getRetiringFederatorPublicKey(new Object[]{BigInteger.valueOf(20)})));
        Assert.assertTrue(Arrays.equals(new byte[]{1, 0}, bridge.getRetiringFederatorPublicKey(new Object[]{BigInteger.valueOf(256)})));
    }

    @Test
    public void getPendingFederationSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederationSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getPendingFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getPendingFederatorPublicKey() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.getArgumentAt(0, int.class)).toByteArray());

        Assert.assertTrue(Arrays.equals(new byte[]{10}, bridge.getPendingFederatorPublicKey(new Object[]{BigInteger.valueOf(10)})));
        Assert.assertTrue(Arrays.equals(new byte[]{20}, bridge.getPendingFederatorPublicKey(new Object[]{BigInteger.valueOf(20)})));
        Assert.assertTrue(Arrays.equals(new byte[]{1, 0}, bridge.getPendingFederatorPublicKey(new Object[]{BigInteger.valueOf(256)})));
    }

    @Test
    public void createFederation() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("create", new byte[][]{}))).thenReturn(123);

        Assert.assertEquals(123, bridge.createFederation(new Object[]{}).intValue());
    }

    @Test
    public void addFederatorPublicKey_ok() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("add", new byte[][] { Hex.decode("aabbccdd") })))
                .thenReturn(123);

        Assert.assertEquals(123, bridge.addFederatorPublicKey(new Object[]{Hex.decode("aabbccdd")}).intValue());
    }

    @Test
    public void addFederatorPublicKey_wrongParameterType() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assert.assertEquals(-10, bridge.addFederatorPublicKey(new Object[]{ "i'm not a byte array" }).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void commitFederation_ok() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("commit", new byte[][] { Hex.decode("01020304") }))).thenReturn(123);

        Assert.assertEquals(123, bridge.commitFederation(new Object[]{ Hex.decode("01020304") }).intValue());
    }

    @Test
    public void commitFederation_wrongParameterType() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assert.assertEquals(-10, bridge.commitFederation(new Object[]{ "i'm not a byte array" }).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void rollbackFederation() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("rollback", new byte[][]{}))).thenReturn(456);

        Assert.assertEquals(456, bridge.rollbackFederation(new Object[]{}).intValue());
    }

    @Test
    public void getLockWhitelistSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getLockWhitelistSize(new Object[]{}).intValue());
    }

    @Test
    public void getLockWhitelistAddress() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistAddress(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.getArgumentAt(0, int.class)).toString());

        Assert.assertEquals("10", bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(10)}));
        Assert.assertEquals("20", bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(20)}));
    }

    @Test
    public void addLockWhitelistAddress() throws IOException {
        Transaction mockedTransaction = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.addLockWhitelistAddress(mockedTransaction, "i-am-an-address", BigInteger.valueOf(Coin.COIN.getValue()))).thenReturn(1234);

        Assert.assertEquals(1234, bridge.addLockWhitelistAddress(new Object[]{ "i-am-an-address", BigInteger.valueOf(Coin.COIN.getValue())}).intValue());
    }

    @Test
    public void removeLockWhitelistAddress() throws IOException {
        Transaction mockedTransaction = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.removeLockWhitelistAddress(mockedTransaction, "i-am-an-address")).thenReturn(1234);

        Assert.assertEquals(1234, bridge.removeLockWhitelistAddress(new Object[]{ "i-am-an-address" }).intValue());
    }

    @Test
    public void getFeePerKb() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFeePerKb())
                .thenReturn(Coin.valueOf(12345678901234L));

        Assert.assertEquals(12345678901234L, bridge.getFeePerKb(new Object[]{}));
    }

    @Test
    public void voteFeePerKb_ok() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFeePerKbChange(txMock, Coin.valueOf(2)))
                .thenReturn(123);

        Assert.assertEquals(123, bridge.voteFeePerKbChange(new Object[]{BigInteger.valueOf(2)}).intValue());
    }

    @Test
    public void voteFeePerKb_wrongParameterType() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assert.assertEquals(-10, bridge.voteFeePerKbChange(new Object[]{ "i'm not a byte array" }).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void precompiledContractAddress() {
        Assert.assertArrayEquals(
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR));
        Assert.assertArrayEquals(
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                TypeConverter.stringHexToByteArray(PrecompiledContracts.BRIDGE_ADDR_STR));
    }
}
