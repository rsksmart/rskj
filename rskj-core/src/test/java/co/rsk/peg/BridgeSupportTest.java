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
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import co.rsk.db.RepositoryImpl;
import co.rsk.peg.simples.SimpleBlockChain;
import co.rsk.peg.simples.SimpleRskTransaction;
import co.rsk.peg.simples.SimpleWallet;
import co.rsk.test.builders.BlockChainBuilder;
import com.google.common.collect.Lists;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.config.net.TestNetConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.util.RLP;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.util.encoders.Hex;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/9/2016.
 */
@RunWith(PowerMockRunner.class)
public class BridgeSupportTest {
    private static final String contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    private static BlockchainNetConfig blockchainNetConfigOriginal;
    private static BridgeConstants bridgeConstants;
    private static NetworkParameters btcParams;

    private static final String TO_ADDRESS = "00000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcParams = bridgeConstants.getBtcParams();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void testInitialChainHeadWithoutBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());
        Assert.assertEquals(0, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(_networkParameters.getGenesisBlock(), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void testInitialChainHeadWithBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(new TestNetConfig());

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeTestNetConstants.getInstance(), Collections.emptyList());
        Assert.assertEquals(1229760, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());

        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void testGetBtcBlockchainBlockLocatorWithoutBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());
        Assert.assertEquals(0, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(_networkParameters.getGenesisBlock(), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(_networkParameters.getGenesisBlock().getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(_networkParameters, _networkParameters.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(6, locator.size());
        Assert.assertEquals(blocks.get(9).getHash(), locator.get(0));
        Assert.assertEquals(blocks.get(8).getHash(), locator.get(1));
        Assert.assertEquals(blocks.get(7).getHash(), locator.get(2));
        Assert.assertEquals(blocks.get(5).getHash(), locator.get(3));
        Assert.assertEquals(blocks.get(1).getHash(), locator.get(4));
        Assert.assertEquals(_networkParameters.getGenesisBlock().getHash(), locator.get(5));

        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }


    @Test
    public void testGetBtcBlockchainBlockLocatorWithBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        List<BtcBlock> checkpoints = createBtcBlocks(_networkParameters, _networkParameters.getGenesisBlock(), 10);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList()) {
            @Override
            InputStream getCheckPoints() {
                return getCheckpoints(_networkParameters, checkpoints);
            }
        };
        Assert.assertEquals(10, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(checkpoints.get(9), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(checkpoints.get(9).getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(_networkParameters, checkpoints.get(9), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(6, locator.size());
        Assert.assertEquals(blocks.get(9).getHash(), locator.get(0));
        Assert.assertEquals(blocks.get(8).getHash(), locator.get(1));
        Assert.assertEquals(blocks.get(7).getHash(), locator.get(2));
        Assert.assertEquals(blocks.get(5).getHash(), locator.get(3));
        Assert.assertEquals(blocks.get(1).getHash(), locator.get(4));
        Assert.assertEquals(checkpoints.get(9).getHash(), locator.get(5));

        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    private List<BtcBlock> createBtcBlocks(NetworkParameters _networkParameters, BtcBlock parent, int numberOfBlocksToCreate) {
        List<BtcBlock> list = new ArrayList<>();
        for (int i = 0; i < numberOfBlocksToCreate; i++) {
            BtcBlock block = new BtcBlock(_networkParameters, 2l, parent.getHash(), Sha256Hash.ZERO_HASH, parent.getTimeSeconds()+1, parent.getDifficultyTarget(), 0, new ArrayList<BtcTransaction>());
            block.solve();
            list.add(block);
            parent = block;
        }
        return list;
    }

    private InputStream getCheckpoints(NetworkParameters _networkParameters, List<BtcBlock> checkpoints) {
        try {
            ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
            MessageDigest digest = Sha256Hash.newDigest();
            final DigestOutputStream digestOutputStream = new DigestOutputStream(baOutputStream, digest);
            digestOutputStream.on(false);
            final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
            StoredBlock storedBlock = new StoredBlock(_networkParameters.getGenesisBlock(), _networkParameters.getGenesisBlock().getWork(), 0);
            try {
                dataOutputStream.writeBytes("CHECKPOINTS 1");
                dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
                digestOutputStream.on(true);
                dataOutputStream.writeInt(checkpoints.size());
                ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
                for (BtcBlock block : checkpoints) {
                    storedBlock = storedBlock.build(block);
                    storedBlock.serializeCompact(buffer);
                    dataOutputStream.write(buffer.array());
                    buffer.position(0);
                }
            }
            finally {
                dataOutputStream.close();
                digestOutputStream.close();
                baOutputStream.close();
            }
            return new ByteArrayInputStream(baOutputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void callUpdateCollectionsFundsEnoughForJustTheSmallerTx() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.valueOf(30,0), new BtcECKey().toAddress(btcParams));
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addOutput(Coin.valueOf(20,0), new BtcECKey().toAddress(btcParams));
        BtcTransaction tx3 = new BtcTransaction(btcParams);
        tx3.addOutput(Coin.valueOf(10,0), new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getRskTxsWaitingForConfirmations().put(hash2, tx2);
        provider0.getRskTxsWaitingForConfirmations().put(hash3, tx3);
        provider0.getActiveFederationBtcUTXOs().add(new UTXO(
                PegTestUtils.createHash(),
                1,
                Coin.valueOf(12,0),
                0,
                false,
                ScriptBuilder.createOutputScript(federation.getAddress())
        ));

        provider0.save();

        track.commit();

        track = repository.startTracking();

        List<Block> blocks = BlockGenerator.getInstance().getSimpleBlockChain(BlockGenerator.getInstance().getGenesisBlock(), 10);
        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);
        TransactionReceipt receipt2 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx2 = new SimpleRskTransaction(hash2.getBytes());
        receipt2.setTransaction(rskTx2);
        TransactionInfo ti2 = new TransactionInfo(receipt2, blocks.get(1).getHash(), 1);
        TransactionReceipt receipt3 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx3 = new SimpleRskTransaction(hash3.getBytes());
        receipt3.setTransaction(rskTx3);
        TransactionInfo ti3 = new TransactionInfo(receipt3, blocks.get(1).getHash(), 2);

        List<TransactionInfo> tis = Lists.newArrayList(ti1, ti2, ti3);

        BlockChainBuilder builder = new BlockChainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).setGenesis(BlockGenerator.getInstance().getGenesisBlock()).build();

        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();
        Transaction tx = Transaction.create(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(2, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(1, provider.getRskTxsWaitingForSignatures().size());
        // Check value sent to user is 10 BTC minus fee
        Assert.assertEquals(Coin.valueOf(999962800l), provider.getRskTxsWaitingForSignatures().values().iterator().next().getOutput(0).getValue());
    }

    @Test
    public void callUpdateCollectionsThrowsCouldNotAdjustDownwards() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.valueOf(37500), new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getActiveFederationBtcUTXOs().add(new UTXO(
                PegTestUtils.createHash(),
                1,
                Coin.valueOf(1000000),
                0,
                false,
                ScriptBuilder.createOutputScript(federation.getAddress())
        ));

        provider0.save();

        track.commit();

        track = repository.startTracking();

        List<Block> blocks = BlockGenerator.getInstance().getSimpleBlockChain(BlockGenerator.getInstance().getGenesisBlock(), 10);
        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);

        List<TransactionInfo> tis = Lists.newArrayList(ti1);
        BlockChainBuilder builder = new BlockChainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).build();


        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();
        Transaction tx = Transaction.create(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(1, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void callUpdateCollectionsThrowsExceededMaxTransactionSize() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(7), new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        for (int i = 0; i < 2000; i++) {
            provider0.getActiveFederationBtcUTXOs().add(new UTXO(
                    PegTestUtils.createHash(),
                    1,
                    Coin.CENT,
                    0,
                    false,
                    ScriptBuilder.createOutputScript(federation.getAddress())
            ));
        }

        provider0.save();

        track.commit();

        track = repository.startTracking();

        List<Block> blocks = BlockGenerator.getInstance().getSimpleBlockChain(BlockGenerator.getInstance().getGenesisBlock(), 10);
        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);

        List<TransactionInfo> tis = Lists.newArrayList(ti1);
        BlockChainBuilder builder = new BlockChainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).build();

        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();
        Transaction tx = Transaction.create(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(1, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void callUpdateCollectionsChangeGetsOutOfDust() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();

        Map<byte[], BigInteger> preMineMap = new HashMap<byte[], BigInteger>();
        preMineMap.put(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), Denomination.satoshisToWeis(BigInteger.valueOf(21000000)));

        Genesis genesisBlock = (Genesis) BlockGenerator.getInstance().getNewGenesisBlock(0, preMineMap);

        List<Block> blocks = BlockGenerator.getInstance().getSimpleBlockChain(genesisBlock, 10);

        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);

        List<TransactionInfo> tis = Lists.newArrayList(ti1);
        BlockChainBuilder builder = new BlockChainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).setGenesis(genesisBlock).build();

        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();

        Repository repository = blockchain.getRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getActiveFederationBtcUTXOs().add(new UTXO(PegTestUtils.createHash(), 1, Coin.COIN.add(Coin.valueOf(100)), 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));

        provider0.save();

        track.commit();

        track = repository.startTracking();
        Transaction tx = Transaction.create(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(0, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(1, provider.getRskTxsWaitingForSignatures().size());
        Assert.assertEquals(Denomination.satoshisToWeis(BigInteger.valueOf(21000000-2600)), repository.getBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR)));
        Assert.assertEquals(Denomination.satoshisToWeis(BigInteger.valueOf(2600)), repository.getBalance(RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBurnAddress()));
    }

    @PrepareForTest({ BridgeUtils.class })
    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmationsAndFunds() throws IOException, BlockStoreException {
        // Bridge constants and btc context
        BridgeConstants bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context context = new Context(bridgeConstants.getBtcParams());

        // Fake wallet returned every time
        PowerMockito.mockStatic(BridgeUtils.class);
        PowerMockito.when(BridgeUtils.getFederationSpendWallet(any(Context.class), any(Federation.class), any(List.class))).thenReturn(new SimpleWallet(context));

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
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        List<Block> blocks = BlockGenerator.getInstance().getSimpleBlockChain(BlockGenerator.getInstance().getGenesisBlock(), 10);
        TransactionReceipt receipt = new TransactionReceipt();
        org.ethereum.core.Transaction tx = new SimpleRskTransaction(hash1.getBytes());
        receipt.setTransaction(tx);
        TransactionInfo ti = new TransactionInfo(receipt, blocks.get(1).getHash(), 0);
        List<TransactionInfo> tis = new ArrayList<>();
        tis.add(ti);

        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).build();

        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();
        Transaction rskTx = Transaction.create(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, rskCurrentBlock, rskReceiptStore, rskBlockStore, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.updateCollections(rskTx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertFalse(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertEquals(2, provider2.getRskTxsWaitingForConfirmations().size());
        Assert.assertFalse(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(1, provider2.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void sendOrphanBlockHeader() throws IOException, BlockStoreException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void addBlockHeaderToBlockchain() throws IOException, BlockStoreException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNotNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void addSignatureToMissingTransaction() throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, (Block) null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.addSignature(1, federation.getPublicKeys().get(0), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void addSignatureFromInvalidFederator() throws Exception {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, (Block) null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.addSignature(1, new BtcECKey(), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void addSignatureWithInvalidSignature() throws Exception {
        addSignatureFromValidFederator(Lists.newArrayList(new BtcECKey()), 1, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithLessSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 0, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithMoreSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 2, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureNonCanonicalSignature() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, false, false, "InvalidParameters");
    }

    @Test
    public void addSignatureTwice() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, true, true, "PartiallySigned");
    }

    @Test
    public void addSignatureOneSignature() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, true, false, "PartiallySigned");
    }

    @Test
    public void addSignatureTwoSignatures() throws Exception {
        List<BtcECKey> federatorPrivateKeys = ((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys();
        List<BtcECKey> keys = Lists.newArrayList(federatorPrivateKeys.get(0), federatorPrivateKeys.get(1));
        addSignatureFromValidFederator(keys, 1, true, false, "FullySigned");
    }

    /**
     * Helper method to test addSignature() with a valid federatorPublicKey parameter and both valid/invalid signatures
     * @param privateKeysToSignWith keys used to sign the tx. Federator key when we want to produce a valid signature, a random key when we want to produce an invalid signature
     * @param numberOfInputsToSign There is just 1 input. 1 when testing the happy case, other values to test attacks/bugs.
     * @param signatureCanonical Signature should be canonical. true when testing the happy case, false to test attacks/bugs.
     * @param signTwice Sign again with the same key
     * @param expectedResult "InvalidParameters", "PartiallySigned" or "FullySigned"
     */
    private void addSignatureFromValidFederator(List<BtcECKey> privateKeysToSignWith, int numberOfInputsToSign, boolean signatureCanonical, boolean signTwice, String expectedResult) throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl();

        final Sha3Hash sha3Hash = PegTestUtils.createHash3();

        Repository track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);
        UTXO utxo = new UTXO(prevTx.getHash(), 0, prevOut.getValue(), 0, false, prevOut.getScriptPubKey());
        provider.getActiveFederationBtcUTXOs().add(utxo);

        BtcTransaction t = new BtcTransaction(btcParams);
        TransactionOutput output = new TransactionOutput(btcParams, t, Coin.COIN, new BtcECKey().toAddress(btcParams));
        t.addOutput(output);
        t.addInput(prevOut).setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
        provider.getRskTxsWaitingForSignatures().put(sha3Hash, t);
        provider.save();
        track.commit();

        track = repository.startTracking();
        List<LogInfo> logs = new ArrayList<>();
        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, (Block) null, null, null, BridgeRegTestConstants.getInstance(), logs);

        Script inputScript = t.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sighash = t.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        BtcECKey.ECDSASignature sig = privateKeysToSignWith.get(0).sign(sighash);
        if (!signatureCanonical) {
            sig = new BtcECKey.ECDSASignature(sig.r, BtcECKey.CURVE.getN().subtract(sig.s));
        }
        byte[] derEncodedSig = sig.encodeToDER();

        List derEncodedSigs = new ArrayList();
        for (int i = 0; i < numberOfInputsToSign; i++) {
            derEncodedSigs.add(derEncodedSig);
        }
        bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeysToSignWith.get(0)), derEncodedSigs, sha3Hash.getBytes());
        if (signTwice) {
            // Create another valid signature with the same private key
            ECDSASigner signer = new ECDSASigner();
            ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKeysToSignWith.get(0).getPrivKey(), BtcECKey.CURVE);
            signer.init(true, privKey);
            BigInteger[] components = signer.generateSignature(sighash.getBytes());
            BtcECKey.ECDSASignature sig2 = new BtcECKey.ECDSASignature(components[0], components[1]).toCanonicalised();
            bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeysToSignWith.get(0)), Lists.newArrayList(sig2.encodeToDER()), sha3Hash.getBytes());
        }
        if (privateKeysToSignWith.size()>1) {
            BtcECKey.ECDSASignature sig2 = privateKeysToSignWith.get(1).sign(sighash);
            byte[] derEncodedSig2 = sig2.encodeToDER();
            List derEncodedSigs2 = new ArrayList();
            for (int i = 0; i < numberOfInputsToSign; i++) {
                derEncodedSigs2.add(derEncodedSig2);
            }
            bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeysToSignWith.get(1)), derEncodedSigs2, sha3Hash.getBytes());
        }
        bridgeSupport.save();
        track.commit();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        if ("FullySigned".equals(expectedResult)) {
            Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
            Assert.assertThat(logs, is(not(empty())));
            Assert.assertThat(logs, hasSize(1));
            LogInfo releaseTxEvent = logs.get(0);
            Assert.assertThat(releaseTxEvent.getTopics(), hasSize(1));
            Assert.assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
            BtcTransaction releaseTx = new BtcTransaction(bridgeConstants.getBtcParams(), RLP.decode2(releaseTxEvent.getData()).get(0).getRLPData());
            Script retrievedScriptSig = releaseTx.getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(2).data.length > 0);
            Assert.assertTrue(provider.getActiveFederationBtcUTXOs().isEmpty());
        } else {
            Script retrievedScriptSig = provider.getRskTxsWaitingForSignatures().get(sha3Hash).getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            boolean expectSignatureToBePersisted = false; // for "InvalidParameters"
            if ("PartiallySigned".equals(expectedResult)) {
                expectSignatureToBePersisted = true;
            }
            Assert.assertEquals(expectSignatureToBePersisted, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(false, retrievedScriptSig.getChunks().get(2).data.length > 0);
            Assert.assertFalse(provider.getActiveFederationBtcUTXOs().isEmpty());
        }
    }

    private BtcECKey findPublicKeySignedBy(List<BtcECKey> pubs, BtcECKey pk) {
        for (BtcECKey pub : pubs) {
            if (Arrays.equals(pk.getPubKey(), pub.getPubKey())) {
                return pub;
            }
        }
        return null;
    }

    @Test
    public void releaseBtcWithDustOutput() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void releaseBtc() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertFalse(provider.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertFalse(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void releaseBtcFromContract() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());
        track.saveCode(tx.getSender(), new byte[] {0x1});
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null,null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        try {
            bridgeSupport.releaseBtc(tx);
        } catch (Program.OutOfGasException e) {
            return;
        }
        Assert.fail();
    }

    @Test
    public void registerBtcTransactionOfAlreadyProcessedTransaction() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider.getBtcTxHashesAlreadyProcessed().put(tx.getHash(), 1L);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx, 0, null);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getActiveFederationBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionNotInMerkleTree() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx, 0, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getActiveFederationBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNegativeHeight() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx, -1, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getActiveFederationBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNotEnoughtHeight() throws BlockStoreException, AddressFormatException, IOException {
        BlockchainNetConfig blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(_networkParameters, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx, 1, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getActiveFederationBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());

        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void registerBtcTransactionTxNotLockNorReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN, address);
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));


        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx, 1, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(0, provider2.getActiveFederationBtcUTXOs().size());

        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl();
        repository.addBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()));
        Repository track = repository.startTracking();
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN, address);
        Address address2 = federation.getAddress();
        tx.addOutput(Coin.COIN, address2);

        // Create previous tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);
        // Create tx input
        tx.addInput(prevOut);
        // Create tx input base script sig
        Script scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        // Create sighash
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getPublicKeys());
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        // Sign by federator 0
        BtcECKey.ECDSASignature sig0 = bridgeConstants.getFederatorPrivateKeys().get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, federation.getPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = bridgeConstants.getFederatorPrivateKeys().get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, federation.getPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);
        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx, 1, pmt);
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        Assert.assertEquals(BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()), repository.getBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR)));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(1, provider2.getActiveFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN, provider2.getActiveFederationBtcUTXOs().get(0).getValue());

        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(1, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    @Test
    public void registerBtcTransactionMigrationTx() throws BlockStoreException, AddressFormatException, IOException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters parameters = bridgeConstants.getBtcParams();

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation activeFederation = new Federation(activeFederationKeys, Instant.ofEpochMilli(2000L), 2L, parameters);

        List<BtcECKey> retiringFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation retiringFederation = new Federation(retiringFederationKeys, Instant.ofEpochMilli(1000L), 1L, parameters);

        Repository repository = new RepositoryImpl();
        repository.addBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()));
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        BtcTransaction tx = new BtcTransaction(parameters);
        Address activeFederationAddress = activeFederation.getAddress();
        tx.addOutput(Coin.COIN, activeFederationAddress);

        // Create previous tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, retiringFederation.getAddress());
        prevTx.addOutput(prevOut);
        // Create tx input
        tx.addInput(prevOut);
        // Create tx input base script sig
        Script scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(retiringFederation);
        // Create sighash
        Script redeemScript = ScriptBuilder.createRedeemScript(retiringFederation.getNumberOfSignaturesRequired(), retiringFederation.getPublicKeys());
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        // Sign by federator 0
        BtcECKey.ECDSASignature sig0 = retiringFederationKeys.get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, retiringFederation.getPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = retiringFederationKeys.get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, retiringFederation.getPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);


        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);
        provider.setActiveFederation(activeFederation);
        provider.setRetiringFederation(retiringFederation);
        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);
        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx, 1, pmt);
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        List<UTXO> activeFederationBtcUTXOs = provider.getActiveFederationBtcUTXOs();
        List<Coin> activeFederationBtcCoins = activeFederationBtcUTXOs.stream().map(UTXO::getValue).collect(Collectors.toList());
        Assert.assertThat(activeFederationBtcUTXOs, hasSize(1));
        Assert.assertThat(activeFederationBtcCoins, hasItem(Coin.COIN));
    }

    @Test
    public void registerBtcTransactionLockTxWhitelisted() throws BlockStoreException, AddressFormatException, IOException {
        BridgeConstants bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        NetworkParameters parameters = bridgeConstants.getBtcParams();

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(federation1Keys, Instant.ofEpochMilli(1000L), 0L, parameters);

        List<BtcECKey> federation2Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")),
        });
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation2 = new Federation(federation2Keys, Instant.ofEpochMilli(2000L), 0L, parameters);

        Repository repository = new RepositoryImpl();
        repository.addBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()));
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(this.btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(this.btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(this.btcParams);
        tx3.addOutput(Coin.COIN.multiply(2), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(3), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey3));

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);
        provider.setActiveFederation(federation1);
        provider.setRetiringFederation(federation2);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.add(srcKey1.toAddress(parameters));
        whitelist.add(srcKey2.toAddress(parameters));
        whitelist.add(srcKey3.toAddress(parameters));

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1, 1, pmt);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx2, 1, pmt);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx3, 1, pmt);
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        BigInteger amountToHaveBeenCreditedToSrc1 = Denomination.SBTC.value().multiply(BigInteger.valueOf(5));
        BigInteger amountToHaveBeenCreditedToSrc2 = Denomination.SBTC.value().multiply(BigInteger.valueOf(10));
        BigInteger amountToHaveBeenCreditedToSrc3 = Denomination.SBTC.value().multiply(BigInteger.valueOf(5));
        BigInteger totalAmountExpectedToHaveBeenLocked = amountToHaveBeenCreditedToSrc1
                .add(amountToHaveBeenCreditedToSrc2)
                .add(amountToHaveBeenCreditedToSrc3);
        byte[] srcKey1RskAddress = org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress();
        byte[] srcKey2RskAddress = org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress();
        byte[] srcKey3RskAddress = org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress();

        Assert.assertEquals(amountToHaveBeenCreditedToSrc1, repository.getBalance(srcKey1RskAddress));
        Assert.assertEquals(amountToHaveBeenCreditedToSrc2, repository.getBalance(srcKey2RskAddress));
        Assert.assertEquals(amountToHaveBeenCreditedToSrc3, repository.getBalance(srcKey3RskAddress));
        Assert.assertEquals(BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()).subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR)));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(2, provider2.getActiveFederationBtcUTXOs().size());
        Assert.assertEquals(2, provider2.getRetiringFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(5), provider2.getActiveFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(2), provider2.getActiveFederationBtcUTXOs().get(1).getValue());
        Assert.assertEquals(Coin.COIN.multiply(10), provider2.getRetiringFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(3), provider2.getRetiringFederationBtcUTXOs().get(1).getValue());

        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(3, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    @Test
    public void registerBtcTransactionLockTxNotWhitelisted() throws BlockStoreException, AddressFormatException, IOException {
        BridgeConstants bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        NetworkParameters parameters = bridgeConstants.getBtcParams();

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(federation1Keys, Instant.ofEpochMilli(1000L), 0L, parameters);

        List<BtcECKey> federation2Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")),
        });
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation2 = new Federation(federation2Keys, Instant.ofEpochMilli(2000L), 0L, parameters);

        Repository repository = new RepositoryImpl();
        repository.addBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()));
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(this.btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(this.btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(this.btcParams);
        tx3.addOutput(Coin.COIN.multiply(3), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey3));

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);
        provider.setActiveFederation(federation1);
        provider.setRetiringFederation(federation2);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        Transaction rskTx1 = getMockedRskTxWithHash("aa");
        Transaction rskTx2 = getMockedRskTxWithHash("bb");
        Transaction rskTx3 = getMockedRskTxWithHash("cc");

        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(rskTx1, tx1, 1, pmt);
        bridgeSupport.registerBtcTransaction(rskTx2, tx2, 1, pmt);
        bridgeSupport.registerBtcTransaction(rskTx3, tx3, 1, pmt);
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        byte[] srcKey1RskAddress = org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress();
        byte[] srcKey2RskAddress = org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress();
        byte[] srcKey3RskAddress = org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress();

        Assert.assertEquals(0, repository.getBalance(srcKey1RskAddress).intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey2RskAddress).intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey3RskAddress).intValue());
        Assert.assertEquals(BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()), repository.getBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR)));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(2, provider2.getActiveFederationBtcUTXOs().size());
        Assert.assertEquals(2, provider2.getRetiringFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(5), provider2.getActiveFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(3), provider2.getActiveFederationBtcUTXOs().get(1).getValue());
        Assert.assertEquals(Coin.COIN.multiply(10), provider2.getRetiringFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(4), provider2.getRetiringFederationBtcUTXOs().get(1).getValue());

        Assert.assertEquals(3, provider2.getRskTxsWaitingForConfirmations().size());
        BtcTransaction tx;
        TransactionOutput output;
        Address outputAddress;

        tx = provider2.getRskTxsWaitingForConfirmations().get(new Sha3Hash(rskTx1.getHash()));
        assertTxIsOfValueToSpecificAddress(tx, srcKey1, 5);

        tx = provider2.getRskTxsWaitingForConfirmations().get(new Sha3Hash(rskTx2.getHash()));
        assertTxIsOfValueToSpecificAddress(tx, srcKey2, 10);

        tx = provider2.getRskTxsWaitingForConfirmations().get(new Sha3Hash(rskTx3.getHash()));
        assertTxIsOfValueToSpecificAddress(tx, srcKey3, 7);

        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(3, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    private void assertTxIsOfValueToSpecificAddress(BtcTransaction tx, BtcECKey key, int value) {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        Assert.assertNotNull(tx);
        Assert.assertEquals(0, tx.getInputs().size());
        Assert.assertEquals(1, tx.getOutputs().size());
        TransactionOutput output = tx.getOutputs().get(0);
        Address outputAddress = new Script(output.getScriptBytes()).getToAddress(params);
        Assert.assertEquals(outputAddress, key.toAddress(params));
        Assert.assertEquals(output.getValue(), Coin.COIN.multiply(value));
    }

    @Test
    public void testHasEnoughConfirmations() throws Exception {
        Assert.assertFalse(hasEnoughConfirmations(10));
        Assert.assertTrue(hasEnoughConfirmations(20));
    }

    @Test
    public void isBtcTxHashAlreadyProcessed() throws IOException, BlockStoreException {
        BridgeSupport bridgeSupport = new BridgeSupport(
                null,
                null,
                getBridgeStorageProviderMockWithProcessedHashes(),
                null,
                null);

        for (int i = 0; i < 10; i++) {
            Assert.assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of(("hash_" + i).getBytes())));
        }
        Assert.assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of("anything".getBytes())));
    }

    @Test
    public void getBtcTxHashProcessedHeight() throws IOException, BlockStoreException {
        BridgeSupport bridgeSupport = new BridgeSupport(
                null,
                null,
                getBridgeStorageProviderMockWithProcessedHashes(),
                null,
                null);

        for (int i = 0; i < 10; i++) {
            Assert.assertEquals((long) i, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of(("hash_" + i).getBytes())).longValue());
        }
        Assert.assertEquals(-1L, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of("anything".getBytes())).longValue());
    }

    @Test
    public void getFederationMethods_genesis() throws IOException {
        Federation activeFederation = new Federation(
                getTestFederationPublicKeys(3),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = new Federation(
                getTestFederationPublicKeys(6),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(true, activeFederation, genesisFederation, null, null, null, null);

        Assert.assertEquals(6, bridgeSupport.getFederationSize().intValue());
        Assert.assertEquals(4, bridgeSupport.getFederationThreshold().intValue());
        Assert.assertEquals(genesisFederation.getAddress().toString(), bridgeSupport.getFederationAddress().toString());
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(6);
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
        }
    }

    @Test
    public void getFederationMethods_active() throws IOException {
        Federation activeFederation = new Federation(
                getTestFederationPublicKeys(3),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = new Federation(
                getTestFederationPublicKeys(6),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, activeFederation, genesisFederation, null, null, null, null);

        Assert.assertEquals(3, bridgeSupport.getFederationSize().intValue());
        Assert.assertEquals(2, bridgeSupport.getFederationThreshold().intValue());
        Assert.assertEquals(activeFederation.getAddress().toString(), bridgeSupport.getFederationAddress().toString());
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(3);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
        }
    }

    @Test
    public void getRetiringFederationMethods_none() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null, null, null);

        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationSize().intValue());
        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationThreshold().intValue());
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKey(0));
    }

    @Test
    public void getRetiringFederationMethods_present() throws IOException {
        Federation mockedRetiringFederation = new Federation(
                getTestFederationPublicKeys(4),
                Instant.ofEpochMilli(2000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, mockedRetiringFederation, null, null, null);

        Assert.assertEquals(4, bridgeSupport.getRetiringFederationSize().intValue());
        Assert.assertEquals(3, bridgeSupport.getRetiringFederationThreshold().intValue());
        Assert.assertEquals(2000, bridgeSupport.getRetiringFederationCreationTime().toEpochMilli());
        Assert.assertEquals(mockedRetiringFederation.getAddress().toString(), bridgeSupport.getRetiringFederationAddress().toString());
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(4);
        for (int i = 0; i < 4; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getRetiringFederatorPublicKey(i)));
        }
    }

    @Test
    public void getPendingFederationMethods_none() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null,  null, null);

        Assert.assertEquals(-1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertNull(bridgeSupport.getPendingFederatorPublicKey(0));
    }

    @Test
    public void getPendingFederationMethods_present() throws IOException {
        PendingFederation mockedPendingFederation = new PendingFederation(
                getTestFederationPublicKeys(5)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, mockedPendingFederation, null, null);

        Assert.assertEquals(5, bridgeSupport.getPendingFederationSize().intValue());
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(5);
        for (int i = 0; i < 5; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getPendingFederatorPublicKey(i)));
        }
    }

    @Test
    public void voteFederationChange_methodNotAllowed() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ABICallSpec spec = new ABICallSpec("a-random-method", new byte[][]{});
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE, bridgeSupport.voteFederationChange(mock(Transaction.class), spec));
    }

    @Test
    public void voteFederationChange_notAuthorized() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ABICallSpec spec = new ABICallSpec("create", new byte[][]{});
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.getSender()).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(12L)).getAddress());
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE, bridgeSupport.voteFederationChange(mockedTx, spec));
    }

    private class VotingMocksProvider {
        private TxSender voter;
        private ABICallElection election;
        private ABICallSpec winner;
        private ABICallSpec spec;
        private Transaction tx;

        public VotingMocksProvider(String function, byte[][] arguments, boolean mockVoteResult) {
            byte[] voterBytes = ECKey.fromPublicOnly(Hex.decode(
                    // Public key hex of an authorized voter in regtest, taken from BridgeRegTestConstants
                    "04dde17c5fab31ffc53c91c2390136c325bb8690dc135b0840075dd7b86910d8ab9e88baad0c32f3eea8833446a6bc5ff1cd2efa99ecb17801bcb65fc16fc7d991"
            )).getAddress();
            voter = new TxSender(voterBytes);

            tx = mock(Transaction.class);
            when(tx.getSender()).thenReturn(voterBytes);

            spec = new ABICallSpec(function, arguments);

            election = mock(ABICallElection.class);
            if (mockVoteResult)
                when(election.vote(spec, voter)).thenReturn(true);

            when(election.getWinner()).then((InvocationOnMock m) -> this.getWinner());
        }

        public TxSender getVoter() { return voter; }

        public ABICallElection getElection() { return election; }

        public ABICallSpec getSpec() { return spec; }

        public Transaction getTx() { return tx; }

        public ABICallSpec getWinner() { return winner; }
        public void setWinner(ABICallSpec winner) { this.winner = winner; }

        public int execute(BridgeSupport bridgeSupport) {
            return bridgeSupport.voteFederationChange(tx, spec);
        }
    }

    @Test
    public void createFederation_ok() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("create", new byte[][]{}, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(
                new PendingFederation(Collections.emptyList()).getHash().getBytes(),
                bridgeSupport.getPendingFederationHash())
        );
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();
    }

    @Test
    public void createFederation_pendingExists() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("create", new byte[][]{}, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                new PendingFederation(Collections.emptyList()),
                mocksProvider.getElection(),
                null
        );

        Assert.assertTrue(Arrays.equals(
                new PendingFederation(Collections.emptyList()).getHash().getBytes(),
                bridgeSupport.getPendingFederationHash()
        ));
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(
                new PendingFederation(Collections.emptyList()).getHash().getBytes(),
                bridgeSupport.getPendingFederationHash()
        ));
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void createFederation_withExistingRetiringFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("create", new byte[][]{}, false);

        Federation mockedRetiringFederation = new Federation(
                getTestFederationPublicKeys(4),
                Instant.ofEpochMilli(2000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                mockedRetiringFederation,
                null,
                mocksProvider.getElection(),
                null
        );
        ((BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider")).getRetiringFederationBtcUTXOs().add(mock(UTXO.class));

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_okNoKeys() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(Collections.emptyList());
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKey(0)));
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    public void addFederatorPublicKey_okKeys() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(2, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKey(0)));
        Assert.assertTrue(Arrays.equals(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"), bridgeSupport.getPendingFederatorPublicKey(1)));
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    public void addFederatorPublicKey_noPendingFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_keyExists() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_invalidKey() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("aabbccdd")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE.intValue(), mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void rollbackFederation_ok() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("rollback", new byte[][]{}, true);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        // Vote with no winner
        Assert.assertNotNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNotNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();
    }

    @Test
    public void rollbackFederation_noPendingFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("rollback", new byte[][]{}, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void commitFederation_ok() throws IOException {
        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49")),
        }));

        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                pendingFederation.getHash().getBytes()
        }, true);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getTimestamp()).thenReturn(5005L);

        Federation expectedFederation = new Federation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49")),
        }), Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                executionBlock
        );
        BridgeStorageProvider provider = (BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider");

        // Mock some utxos in the currently active federation
        for (int i = 0; i < 5; i++) {
            UTXO utxoMock = mock(UTXO.class);
            when(utxoMock.getIndex()).thenReturn((long)i);
            when(utxoMock.getValue()).thenReturn(Coin.valueOf((i+1)*1000));
            provider.getActiveFederationBtcUTXOs().add(utxoMock);
        }

        // Currently active federation
        Federation oldActiveFederation = provider.getActiveFederation();

        // Vote with no winner
        Assert.assertNotNull(provider.getPendingFederation());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNotNull(provider.getPendingFederation());

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));

        Assert.assertNull(provider.getPendingFederation());

        Federation retiringFederation = provider.getRetiringFederation();
        Federation activeFederation = provider.getActiveFederation();

        Assert.assertEquals(expectedFederation, activeFederation);
        Assert.assertEquals(retiringFederation, oldActiveFederation);

        Assert.assertEquals(0, provider.getActiveFederationBtcUTXOs().size());
        Assert.assertEquals(5, provider.getRetiringFederationBtcUTXOs().size());
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals((long) i, provider.getRetiringFederationBtcUTXOs().get(i).getIndex());
            Assert.assertEquals(Coin.valueOf((i+1)*1000), provider.getRetiringFederationBtcUTXOs().get(i).getValue());
        }
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();
    }

    @Test
    public void commitFederation_noPendingFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                new Sha3Hash(HashUtil.sha3(Hex.decode("aabbcc"))).getBytes()
        }, true);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void commitFederation_incompleteFederation() throws IOException {
        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));

        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                new Sha3Hash(HashUtil.sha3(Hex.decode("aabbcc"))).getBytes()
        }, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void commitFederation_hashMismatch() throws IOException {
        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12"))
        }));

        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                new Sha3Hash(HashUtil.sha3(Hex.decode("aabbcc"))).getBytes()
        }, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        Assert.assertEquals(-3, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }
//
//    @Test
//    public void commitFederation_hashMismatch() throws IOException {
//        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
//                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
//                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
//        }));
//        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
//                false,
//                null,
//                null,
//                null,
//                pendingFederation,
//                null,
//                null
//        );
//
//        Sha3Hash hash = new Sha3Hash(HashUtil.sha3(Hex.decode("aabbcc")));
//        Assert.assertEquals(-3, bridgeSupport.commitFederation(false, hash).intValue());
//    }

    @PrepareForTest({ BridgeUtils.class })
    @Test
    public void getActiveFederationWallet() throws IOException {
        Federation expectedFederation = new Federation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }), Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                expectedFederation,
                null,
                null,
                null,
                null,
                null
        );
        Context expectedContext = mock(Context.class);
        Whitebox.setInternalState(bridgeSupport, "btcContext", expectedContext);
        BridgeStorageProvider provider = (BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider");
        Object expectedUtxos = provider.getActiveFederationBtcUTXOs();

        final Wallet expectedWallet = mock(Wallet.class);
        PowerMockito.mockStatic(BridgeUtils.class);
        PowerMockito.when(BridgeUtils.getFederationSpendWallet(any(), any(), any())).then((InvocationOnMock m) -> {
            Assert.assertEquals(m.getArgumentAt(0, Context.class), expectedContext);
            Assert.assertEquals(m.getArgumentAt(1, Federation.class), expectedFederation);
            Assert.assertEquals(m.getArgumentAt(2, Object.class), expectedUtxos);
            return expectedWallet;
        });

        Assert.assertSame(expectedWallet, bridgeSupport.getActiveFederationWallet());
    }

    @PrepareForTest({ BridgeUtils.class })
    @Test
    public void getRetiringFederationWallet_nonEmpty() throws IOException {
        Federation expectedFederation = new Federation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }), Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                expectedFederation,
                null,
                null,
                null
        );
        Context expectedContext = mock(Context.class);
        Whitebox.setInternalState(bridgeSupport, "btcContext", expectedContext);
        BridgeStorageProvider provider = (BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider");
        Object expectedUtxos = provider.getRetiringFederationBtcUTXOs();

        final Wallet expectedWallet = mock(Wallet.class);
        PowerMockito.mockStatic(BridgeUtils.class);
        PowerMockito.when(BridgeUtils.getFederationSpendWallet(any(), any(), any())).then((InvocationOnMock m) -> {
            Assert.assertEquals(m.getArgumentAt(0, Context.class), expectedContext);
            Assert.assertEquals(m.getArgumentAt(1, Federation.class), expectedFederation);
            Assert.assertEquals(m.getArgumentAt(2, Object.class), expectedUtxos);
            return expectedWallet;
        });

        Assert.assertSame(expectedWallet, bridgeSupport.getRetiringFederationWallet());
    }

    @Test
    public void getLockWhitelistMethods() throws IOException {
        NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.getSize()).thenReturn(4);
        List<Address> addresses = Arrays.stream(new Integer[]{2,3,4,5}).map(i ->
            new Address(parameters, BtcECKey.fromPrivate(BigInteger.valueOf(i)).getPubKeyHash())
        ).collect(Collectors.toList());
        when(mockedWhitelist.getAddresses()).thenReturn(addresses);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(4, bridgeSupport.getLockWhitelistSize().intValue());
        Assert.assertNull(bridgeSupport.getLockWhitelistAddress(-1));
        Assert.assertNull(bridgeSupport.getLockWhitelistAddress(4));
        Assert.assertNull(bridgeSupport.getLockWhitelistAddress(5));
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(addresses.get(i).toBase58(), bridgeSupport.getLockWhitelistAddress(i));
        }
    }

    @Test
    public void addLockWhitelistAddress_ok() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        when(mockedWhitelist.add(any(Address.class))).then((InvocationOnMock m) -> {
            Address address = m.getArgumentAt(0, Address.class);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return true;
        });

        Assert.assertEquals(1, bridgeSupport.addLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
    }

    @Test
    public void addLockWhitelistAddress_addFails() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        when(mockedWhitelist.add(any(Address.class))).then((InvocationOnMock m) -> {
            Address address = m.getArgumentAt(0, Address.class);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return false;
        });

        Assert.assertEquals(-1, bridgeSupport.addLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
    }

    @Test
    public void addLockWhitelistAddress_notAuthorized() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = Hex.decode("aabbcc");
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), bridgeSupport.addLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
        verify(mockedWhitelist, never()).add(any());
    }

    @Test
    public void addLockWhitelistAddress_invalidAddress() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(-2, bridgeSupport.addLockWhitelistAddress(mockedTx, "i-am-invalid").intValue());
        verify(mockedWhitelist, never()).add(any());
    }

    @Test
    public void removeLockWhitelistAddress_ok() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        when(mockedWhitelist.remove(any(Address.class))).then((InvocationOnMock m) -> {
            Address address = m.getArgumentAt(0, Address.class);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return true;
        });

        Assert.assertEquals(1, bridgeSupport.removeLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
    }

    @Test
    public void removeLockWhitelistAddress_removeFails() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        when(mockedWhitelist.remove(any(Address.class))).then((InvocationOnMock m) -> {
            Address address = m.getArgumentAt(0, Address.class);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return false;
        });

        Assert.assertEquals(-1, bridgeSupport.removeLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
    }

    @Test
    public void removeLockWhitelistAddress_notAuthorized() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = Hex.decode("aabbcc");
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), bridgeSupport.removeLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
        verify(mockedWhitelist, never()).remove(any());
    }

    @Test
    public void removeLockWhitelistAddress_invalidAddress() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        when(mockedTx.getSender()).thenReturn(senderBytes);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(-2, bridgeSupport.removeLockWhitelistAddress(mockedTx, "i-am-invalid").intValue());
        verify(mockedWhitelist, never()).remove(any());
    }

    private BridgeStorageProvider getBridgeStorageProviderMockWithProcessedHashes() throws IOException {
        Map<Sha256Hash, Long> mockedHashes = new HashMap<>();
        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);
        when(providerMock.getBtcTxHashesAlreadyProcessed()).thenReturn(mockedHashes);

        for (int i = 0; i < 10; i++) {
            mockedHashes.put(Sha256Hash.of(("hash_" + i).getBytes()), (long) i);
        }

        return providerMock;
    }

    private BridgeSupport getBridgeSupportWithMocksForFederationTests(boolean genesis, Federation mockedActiveFederation, Federation mockedGenesisFederation, Federation mockedRetiringFederation, PendingFederation mockedPendingFederation, ABICallElection mockedFederationElection, Block executionBlock) throws IOException {
        BridgeConstants constantsMock = mock(BridgeConstants.class);
        when(constantsMock.getGenesisFederation()).thenReturn(mockedGenesisFederation);
        when(constantsMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        when(constantsMock.getFederationChangeAuthorizer()).thenReturn(BridgeRegTestConstants.getInstance().getFederationChangeAuthorizer());

        class FederationHolder {
            private PendingFederation pendingFederation;
            private Federation activeFederation;
            private Federation retiringFederation;
            private ABICallElection federationElection;

            public List<UTXO> retiringUTXOs = new ArrayList<>();
            public List<UTXO> activeUTXOs = new ArrayList<>();

            PendingFederation getPendingFederation() { return pendingFederation; }
            void setPendingFederation(PendingFederation pendingFederation) { this.pendingFederation = pendingFederation; }

            Federation getActiveFederation() { return activeFederation; }
            void setActiveFederation(Federation activeFederation) { this.activeFederation = activeFederation; }

            Federation getRetiringFederation() { return retiringFederation; }
            void setRetiringFederation(Federation retiringFederation) { this.retiringFederation = retiringFederation; }

            public ABICallElection getFederationElection() { return federationElection; }
            public void setFederationElection(ABICallElection federationElection) { this.federationElection = federationElection; }
        }

        final FederationHolder holder = new FederationHolder();
        holder.setPendingFederation(mockedPendingFederation);

        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);

        when(providerMock.getRetiringFederationBtcUTXOs()).then((InvocationOnMock m) -> holder.retiringUTXOs);
        when(providerMock.getActiveFederationBtcUTXOs()).then((InvocationOnMock m) -> holder.activeUTXOs);

        holder.setActiveFederation(genesis ? null : mockedActiveFederation);
        holder.setRetiringFederation(mockedRetiringFederation);
        when(providerMock.getActiveFederation()).then((InvocationOnMock m) -> holder.getActiveFederation());
        when(providerMock.getRetiringFederation()).then((InvocationOnMock m) -> holder.getRetiringFederation());
        when(providerMock.getPendingFederation()).then((InvocationOnMock m) -> holder.getPendingFederation());
        when(providerMock.getFederationElection(any())).then((InvocationOnMock m) -> {
            if (mockedFederationElection != null) {
                holder.setFederationElection(mockedFederationElection);
            }

            if (holder.getFederationElection() == null) {
                AddressBasedAuthorizer auth = m.getArgumentAt(0, AddressBasedAuthorizer.class);
                holder.setFederationElection(new ABICallElection(auth));
            }

            return holder.getFederationElection();
        });
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setActiveFederation(m.getArgumentAt(0, Federation.class));
            return null;
        }).when(providerMock).setActiveFederation(any());
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setRetiringFederation(m.getArgumentAt(0, Federation.class));
            return null;
        }).when(providerMock).setRetiringFederation(any());
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setPendingFederation(m.getArgumentAt(0, PendingFederation.class));
            return null;
        }).when(providerMock).setPendingFederation(any());

        BridgeSupport result = new BridgeSupport(
            null,
            null,
            providerMock,
            null,
            null
        );

        Whitebox.setInternalState(result, "bridgeConstants", constantsMock);
        Whitebox.setInternalState(result, "rskExecutionBlock", executionBlock);

        return result;
    }

    private BridgeSupport getBridgeSupportWithMocksForWhitelistTests(LockWhitelist mockedWhitelist) throws IOException {
        BridgeConstants constantsMock = mock(BridgeConstants.class);
        when(constantsMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        when(constantsMock.getLockWhitelistChangeAuthorizer()).thenReturn(BridgeRegTestConstants.getInstance().getLockWhitelistChangeAuthorizer());

        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);
        when(providerMock.getLockWhitelist()).thenReturn(mockedWhitelist);

        BridgeSupport result = new BridgeSupport(
                null,
                null,
                providerMock,
                null,
                null
        );

        Whitebox.setInternalState(result, "bridgeConstants", constantsMock);

        return result;
    }

    private List<BtcECKey> getTestFederationPublicKeys(int amount) {
        List<BtcECKey> result = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            result.add(BtcECKey.fromPrivate(BigInteger.valueOf((i+1) * 100)));
        }
        result.sort(BtcECKey.PUBKEY_COMPARATOR);
        return result;
    }

    public boolean hasEnoughConfirmations(long currentBlockNumber) throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        byte[] blockHash = new byte[32];
        new SecureRandom().nextBytes(blockHash);
        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash);

        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.get(any(), any(), any())).thenReturn(transactionInfo);

        org.ethereum.core.Block includedBlock = mock(org.ethereum.core.Block.class);
        when(includedBlock.getNumber()).thenReturn(Long.valueOf(10));

        org.ethereum.db.BlockStore blockStore = mock(org.ethereum.db.BlockStore.class);
        when(blockStore.getBlockByHash(any())).thenReturn(includedBlock);

        org.ethereum.core.Block currentBlock = mock(org.ethereum.core.Block.class);
        when(currentBlock.getNumber()).thenReturn(Long.valueOf(currentBlockNumber));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, currentBlock, receiptStore, blockStore, BridgeRegTestConstants.getInstance(), Collections.emptyList());

        Sha3Hash txHash = Sha3Hash.ZERO_HASH;

        return bridgeSupport.hasEnoughConfirmations(txHash);
    }

    private BtcTransaction createTransaction() {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(new TransactionInput(btcParams, btcTx, new byte[0]));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams)));
        return btcTx;
        //new SimpleBtcTransaction(btcParams, PegTestUtils.createHash());
    }

    private Transaction getMockedRskTxWithHash(String s) {
        byte[] hash = SHA3Helper.sha3(s);
        return new SimpleRskTransaction(hash);
    }
}
