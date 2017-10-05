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
import co.rsk.crypto.Sha3Hash;
import co.rsk.db.RepositoryImpl;
import co.rsk.peg.bitcoin.SimpleBtcTransaction;
import co.rsk.peg.simples.SimpleRskTransaction;
import co.rsk.test.World;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 6/8/2016.
 */
public class BridgeTest {


    private static NetworkParameters networkParameters = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

    private static BlockchainNetConfig blockchainNetConfigOriginal;
    private static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void callUpdateCollectionsWithoutTransactions() throws IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmation() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getRskTxsWaitingForConfirmations().put(hash2, tx2);
        provider0.getRskTxsWaitingForConfirmations().put(hash3, tx3);

        provider0.save();

        track.commit();

        track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        World world = new World();
        bridge.init(null, world.getBlockChain().getBestBlock(), track, world.getBlockChain().getBlockStore(), world.getBlockChain().getReceiptStore(), null);

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertFalse(provider.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getRskTxsWaitingForConfirmations().put(hash2, tx2);
        provider0.getRskTxsWaitingForConfirmations().put(hash3, tx3);

        provider0.save();

        track.commit();

        track = repository.startTracking();

        World world = new World();
        List<Block> blocks = BlockGenerator.getSimpleBlockChain(world.getBlockChain().getBestBlock(), 10);
        TransactionReceipt receipt = new TransactionReceipt();
        org.ethereum.core.Transaction tx = new SimpleRskTransaction(hash1.getBytes());
        receipt.setTransaction(tx);
        TransactionInfo ti = new TransactionInfo(receipt, blocks.get(1).getHash(), 0);
        List<TransactionInfo> tis = new ArrayList<>();
        tis.add(ti);

        Blockchain blockchain = world.getBlockChain();

        for (Block b: blocks)
            blockchain.tryToConnect(b);

        ReceiptStore receiptStore = world.getBlockChain().getReceiptStore();

        for (TransactionInfo txi: tis)
            receiptStore.add(txi.getBlockHash(), txi.getIndex(), txi.getReceipt());

        world.getBlockChain().getBlockStore().saveBlock(blocks.get(1), BigInteger.ONE, true);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, world.getBlockChain().getBlockStore().getBestBlock(), track, world.getBlockChain().getBlockStore(), world.getBlockChain().getReceiptStore(), null);

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertFalse(provider.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void sendNoBlockHeader() throws IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        bridge.execute(Bridge.RECEIVE_HEADERS.encode());

        track.commit();
    }

    @Test
    public void sendOrphanBlockHeader() throws IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
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
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        Assert.assertNull(bridge.execute(new byte[3]));
    }


    @Test
    public void executeWithInexistentFunction() throws Exception{
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        Assert.assertNull(bridge.execute(new byte[4]));
    }


    @Test
    public void receiveHeadersWithNonParseableHeader() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        Object[] objectArray = new Object[1];
        objectArray[0] = new byte[60];

        byte[] data = Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray});

        Assert.assertNull(bridge.execute(data));

    }

    @Test
    public void registerBtcTransactionWithNonParseableTx() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithNonParseableMerkleeProof1() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
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
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[30]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void getFederationAddress() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] data = Bridge.GET_FEDERATION_ADDRESS.encode();

        Assert.assertArrayEquals(Bridge.GET_FEDERATION_ADDRESS.encodeOutputs(bridgeConstants.getFederationAddress().toString()), bridge.execute(data));
    }

    @Test
    public void getMinimumLockTxValue() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();


        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] data = Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode();

        Assert.assertArrayEquals(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encodeOutputs(bridgeConstants.getMinimumLockTxValue().value), bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseablePublicKey() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] federatorPublicKeySerialized = new byte[3];
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithEmptySignatureArray() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, null, track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableSignature() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, BlockGenerator.getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new byte[3]};
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableRskTx() throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, BlockGenerator.getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new BtcECKey().sign(Sha256Hash.ZERO_HASH).encodeToDER()};
        byte[] rskTxHash = new byte[3];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void exceptionInUpdateCollection() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

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
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

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
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

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
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

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
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

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
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.getBtcBlockchainBlockLocator(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getBtcBlockchainBlockLocator", ex.getMessage());
        }
    }

//    @Test
//    public void exceptionInGetBtcTxHashesAlreadyProcessed() {
//        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
//
//        try {
//            bridge.getBtcTxHashesAlreadyProcessed(null);
//            Assert.fail();
//        }
//        catch (RuntimeException ex) {
//            Assert.assertEquals("Exception in getBtcTxHashesAlreadyProcessed", ex.getMessage());
//        }
//    }

    private BtcTransaction createTransaction() {
        return new SimpleBtcTransaction(networkParameters, PegTestUtils.createHash());
    }

    @Test
    public void getGasForDataFreeTx() {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new UnitTestBlockchainNetConfig());

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                0,
                1,
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                0,
                Bridge.UPDATE_COLLECTIONS);
        rskTx.sign(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0).getPrivKeyBytes());

        org.ethereum.core.Block rskExecutionBlock = BlockGenerator.createChildBlock(Genesis.getInstance(SystemProperties.CONFIG));
        bridge.init(rskTx, rskExecutionBlock, null, null, null, null);
        Assert.assertEquals(0, bridge.getGasForData(rskTx.getData()));

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void getGasForDataInvalidFunction() {
        getGasForDataPaidTx(50000, null);
    }

    @Test
    public void getGasForDataUpdateCollections() {
        getGasForDataPaidTx(50009, Bridge.UPDATE_COLLECTIONS);
    }

    @Test
    public void getGasForDataReceiveHeaders() {
        getGasForDataPaidTx(50010, Bridge.RECEIVE_HEADERS);
    }

    @Test
    public void getGasForDataRegisterBtcTransaction() {
        getGasForDataPaidTx(50459, Bridge.REGISTER_BTC_TRANSACTION, new byte[3], 1, new byte[3]);
    }

    @Test
    public void getGasForDataReleaseBtc() {
        getGasForDataPaidTx(50012, Bridge.RELEASE_BTC);
    }

    @Test
    public void getGasForDataAddSignature() {
        getGasForDataPaidTx(51101, Bridge.ADD_SIGNATURE, new byte[3], new byte[3][2], new byte[3]);
    }
    @Test
    public void getGasForDataGSFBRC() {
        getGasForDataPaidTx(50014, Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT);
    }

    @Test
    public void getGasForDataGSFD() {
        getGasForDataPaidTx(50015, Bridge.GET_STATE_FOR_DEBUGGING);
    }

    @Test
    public void getGasForDataGBBBCH() {
        getGasForDataPaidTx(50016, Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
    }

    @Test
    public void getGasForDataGBBBL() {
        getGasForDataPaidTx(50017, Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR);
    }

//    @Test
//    public void getGasForDataGBTHAP() {
//        getGasForDataPaidTx(50018, Bridge.GET_BTC_TX_HASHES_ALREADY_PROCESSED);
//    }

    @Test
    public void getGasForDataGetFederationAddress() {
        getGasForDataPaidTx(50019, Bridge.GET_FEDERATION_ADDRESS);
    }

    @Test
    public void getGasForDataGetMinimumLockTxValue() {
        getGasForDataPaidTx(50020, Bridge.GET_MINIMUM_LOCK_TX_VALUE);
    }



    private void getGasForDataPaidTx(int expected, CallTransaction.Function function, Object... funcArgs) {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new UnitTestBlockchainNetConfig());

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        org.ethereum.core.Transaction rskTx;
        if (function==null) {
            rskTx = CallTransaction.createRawTransaction(
                    0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    new byte[]{1,2,3});
        } else {
            rskTx = CallTransaction.createCallTransaction(
                    0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    function,
                    funcArgs);
        }

        rskTx.sign(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0).getPrivKeyBytes());

        org.ethereum.core.Block rskExecutionBlock = BlockGenerator.createChildBlock(Genesis.getInstance(SystemProperties.CONFIG));
        for (int i = 0; i < 20; i++) {
            rskExecutionBlock = BlockGenerator.createChildBlock(rskExecutionBlock);
        }
        bridge.init(rskTx, rskExecutionBlock, null, null, null, null);
        Assert.assertEquals(expected, bridge.getGasForData(rskTx.getData()));

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }



}
