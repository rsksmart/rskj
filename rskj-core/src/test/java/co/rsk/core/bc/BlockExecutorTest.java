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

package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.*;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.RLP;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP126;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP144;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Created by ajlopez on 29/07/2016.
 */
@RunWith(Parameterized.class)
public class BlockExecutorTest {
    private static final byte[] EMPTY_TRIE_HASH = sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    private static final TestSystemProperties CONFIG = new TestSystemProperties();
    private static final ActivationConfig activationConfig = spy(CONFIG.getActivationConfig());
    private static final BlockFactory BLOCK_FACTORY = new BlockFactory(activationConfig);
    public static final boolean RSKIP_126_IS_ACTIVE = true;

    private Blockchain blockchain;
    private BlockExecutor executor;
    private TrieStore trieStore;
    private RepositorySnapshot repository;

    private final Boolean activeRskip144;
    private RskSystemProperties cfg;

    public BlockExecutorTest(Boolean activeRskip144) {
        this.activeRskip144 = activeRskip144;
    }

    @Parameterized.Parameters
    public static Collection params() {
        return Arrays.asList(new Object[][] {
                {true},
                {false}
        });
    }

    @Before
    public void setUp() {
        cfg = spy(CONFIG);
        doReturn(activationConfig).when(cfg).getActivationConfig();
        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        RskTestFactory objects = new RskTestFactory(CONFIG);
        blockchain = objects.getBlockchain();
        trieStore = objects.getTrieStore();
        repository = objects.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());
        executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
    }

    @Test
    public void executeBlockWithoutTransaction() {
        Block parent = blockchain.getBestBlock();
        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(parent);

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertTrue(result.getTransactionReceipts().isEmpty());
        Assert.assertArrayEquals(repository.getRoot(), parent.getStateRoot());
        Assert.assertArrayEquals(repository.getRoot(), result.getFinalState().getHash().getBytes());
    }

    @Test
    public void executeBlockWithOneTransaction() {
        executor.setRegisterProgramResults(false);
        Block block = getBlockWithOneTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        Assert.assertNull(executor.getProgramResult(tx.getHash()));

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(21000, result.getGasUsed());
        Assert.assertEquals(21000, result.getPaidFees().asBigInteger().intValueExact());

        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assert.assertEquals(256, calculatedLogsBloom.length);
        Assert.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    @Test
    public void executeBlockWithOneTransactionAndCollectingProgramResults() {
        executor.setRegisterProgramResults(true);
        Block block = getBlockWithOneTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        Assert.assertNotNull(executor.getProgramResult(tx.getHash()));
        executor.setRegisterProgramResults(false);

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(21000, result.getGasUsed());
        Assert.assertEquals(21000, result.getPaidFees().asBigInteger().intValueExact());

        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assert.assertEquals(256, calculatedLogsBloom.length);
        Assert.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    @Test
    public void executeBlockWithTwoTransactions() {
        Block block = getBlockWithTwoTransactions(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx1 = block.getTransactionsList().get(0);
        Transaction tx2 = block.getTransactionsList().get(1);
        RskAddress account = tx1.getSender();

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertNotNull(result);

        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(2, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx1, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        receipt = result.getTransactionReceipts().get(1);
        Assert.assertEquals(tx2, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(42000, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(42000, result.getGasUsed());
        Assert.assertEquals(42000, result.getPaidFees().asBigInteger().intValueExact());

        //here is the problem: in the prior code repository root would never be overwritten by childs
        //while the new code does overwrite the root.
        //Which semantic is correct ? I don't know

        Assert.assertFalse(Arrays.equals(parent.getStateRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assert.assertEquals(256, calculatedLogsBloom.length);
        Assert.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(60000), accountState.getBalance().asBigInteger());

        // here is the papa. my commit changes stateroot while previous commit did not.

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(60000 - 42000 - 20), accountState.getBalance().asBigInteger());
    }

    @Test
    public void executeAndFillBlockWithOneTransaction() {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false);
        executor.executeAndFill(block, parent.getHeader());

        byte[] calculatedReceiptsRoot = BlockHashesHelper.calculateReceiptsTrieRoot(result.getTransactionReceipts(), true);
        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertArrayEquals(calculatedReceiptsRoot, block.getReceiptsRoot());
        Assert.assertArrayEquals(result.getFinalState().getHash().getBytes(), block.getStateRoot());
        Assert.assertEquals(result.getGasUsed(), block.getGasUsed());
        Assert.assertEquals(result.getPaidFees(), block.getFeesPaidToMiner());
        Assert.assertArrayEquals(BlockExecutor.calculateLogsBloom(result.getTransactionReceipts()), block.getLogBloom());

        Assert.assertEquals(3000000, new BigInteger(1, block.getGasLimit()).longValue());
    }

    private Block createBlockWithExcludedTransaction(boolean withRemasc) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.ZERO);

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        Transaction tx3 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx3.sign(account.getEcKey().getPrivKeyBytes());
        Transaction tx = tx3;
        Transaction tx1 = Transaction
                .builder()
                .nonce(repository.getNonce(account3.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx1.sign(account3.getEcKey().getPrivKeyBytes());
        Transaction tx2 = tx1;
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        txs.add(tx2);


        List<Transaction> expectedTxList = new ArrayList<Transaction>();
        expectedTxList.add(tx);

        if (withRemasc) {
            Transaction remascTx = new RemascTransaction(1L);
            txs.add(remascTx);
            expectedTxList.add(remascTx);
        }

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), activationConfig);
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFill(block, genesis.getHeader());

        Assert.assertEquals(tx, block.getTransactionsList().get(0));
        Assert.assertArrayEquals(
                calculateTxTrieRoot(expectedTxList, block.getNumber()),
                block.getTxTrieRoot()
        );

        return block;
    }

    @Test
    public void executeAndFillBlockWithTxToExcludeBecauseSenderHasNoBalance() {
        Block block = createBlockWithExcludedTransaction(false);

        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        // Check tx2 was excluded
        Assert.assertEquals(1, block.getTransactionsList().size());

        Assert.assertEquals(3141592, new BigInteger(1, block.getGasLimit()).longValue());
    }

    @Test
    public void executeBlockWithTxThatMakesBlockInvalidSenderHasNoBalance() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.ZERO);

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        Transaction tx3 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx3.sign(account.getEcKey().getPrivKeyBytes());
        Transaction tx = tx3;
        Transaction tx1 = Transaction
                .builder()
                .nonce(repository.getNonce(account3.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx1.sign(account3.getEcKey().getPrivKeyBytes());
        Transaction tx2 = tx1;
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        txs.add(tx2);

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), activationConfig);
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, txs, uncles, 1, null);

        BlockResult result = executor.executeForMining(block, genesis.getHeader(), false, false);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
    }

    @Test
    public void executeSequentiallyATransactionAndGasShouldBeSubtractedCorrectly() {
        if (!activeRskip144) {
            return;
        }

        short[] expectedEdges = new short[]{1};
        Block parent = blockchain.getBestBlock();
        long expectedAccumulatedGas = 21000L;

        Block block = getBlockWithNIndependentTransactions(1, BigInteger.valueOf(expectedAccumulatedGas), false);
        List<Transaction> txs = block.getTransactionsList();
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        Assert.assertEquals(txs, blockResult.getExecutedTransactions());
        Assert.assertEquals(expectedAccumulatedGas, blockResult.getGasUsed());
        Assert.assertArrayEquals(expectedEdges, blockResult.getTxEdges());

        List<TransactionReceipt> transactionReceipts = blockResult.getTransactionReceipts();
        for (TransactionReceipt receipt: transactionReceipts) {
            Assert.assertEquals(expectedAccumulatedGas, GasCost.toGas(receipt.getCumulativeGas()));
        }
    }

    @Test
    public void executeSequentiallyTenIndependentTxsAndThemShouldGoInBothSublists() {
        if (!activeRskip144) {
            return;
        }

        long expectedGasUsed = 0L;
        long expectedAccumulatedGas = 21000L;
        int txNumber = 12;
        short[] expectedEdges = new short[]{3, 6, 9, 12};
        Block parent = blockchain.getBestBlock();
        Block block = getBlockWithNIndependentTransactions(txNumber, BigInteger.valueOf(expectedAccumulatedGas), false);
        List<Transaction> txs = block.getTransactionsList();
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        Assert.assertEquals(txs.size(), blockResult.getExecutedTransactions().size());
        Assert.assertTrue(txs.containsAll(blockResult.getExecutedTransactions()));
        Assert.assertArrayEquals(expectedEdges, blockResult.getTxEdges());
        Assert.assertEquals(expectedAccumulatedGas*txNumber, blockResult.getGasUsed());

        List<TransactionReceipt> transactionReceipts = blockResult.getTransactionReceipts();
        long accumulatedGasUsed = 0L;
        short i = 0;
        short edgeIndex = 0;
        for (TransactionReceipt receipt: transactionReceipts) {
            if ((edgeIndex < expectedEdges.length) && (i == expectedEdges[edgeIndex])) {
                edgeIndex++;
                accumulatedGasUsed = expectedGasUsed;
            }

            accumulatedGasUsed += expectedAccumulatedGas;
            Assert.assertEquals(accumulatedGasUsed, GasCost.toGas(receipt.getCumulativeGas()));
            i++;
        }

        Assert.assertEquals(i, transactionReceipts.size());
    }

    @Test
    public void executeBigIndependentTxsSequentiallyTheLastOneShouldGoToSequential() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        long blockGasLimit = GasCost.toGas(parent.getGasLimit());
        int gasLimit = 21000;
        int transactionNumber = (int) (blockGasLimit/gasLimit);
        short[] expectedEdges = new short[]{(short) transactionNumber, (short) (transactionNumber*2), (short) (transactionNumber*3), (short) (transactionNumber*4)};
        int transactionsInSequential = 1;
        Block block = getBlockWithNIndependentTransactions(transactionNumber * Constants.getTransactionExecutionThreads() + transactionsInSequential, BigInteger.valueOf(gasLimit), false);
        List<Transaction> transactionsList = block.getTransactionsList();
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        Assert.assertArrayEquals(expectedEdges, blockResult.getTxEdges());
        Assert.assertEquals(transactionsList.size(), blockResult.getExecutedTransactions().size());
        Assert.assertTrue(transactionsList.containsAll(blockResult.getExecutedTransactions()));

        List<TransactionReceipt> transactionReceipts = blockResult.getTransactionReceipts();
        long accumulatedGasUsed = 0L;
        short i = 0;
        short edgeIndex = 0;
        for (TransactionReceipt receipt: transactionReceipts) {
            accumulatedGasUsed += gasLimit;

            if ((edgeIndex < expectedEdges.length) && (i == expectedEdges[edgeIndex])) {
                edgeIndex++;
                accumulatedGasUsed = gasLimit;
            }
            Assert.assertEquals(accumulatedGasUsed, GasCost.toGas(receipt.getCumulativeGas()));
            i++;
        }

        Assert.assertEquals(i, transactionReceipts.size());
    }

    @Test
    public void executeATxInSequentialAndBlockResultShouldTrackTheGasUsedInTheBlock() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        long blockGasLimit = GasCost.toGas(parent.getGasLimit());
        int gasLimit = 21000;
        int transactionNumberToFillParallelSublist = (int) (blockGasLimit / gasLimit);
        int transactionsInSequential = 1;
        int totalTxsNumber = transactionNumberToFillParallelSublist * Constants.getTransactionExecutionThreads() + transactionsInSequential;
        Block block = getBlockWithNIndependentTransactions(totalTxsNumber, BigInteger.valueOf(gasLimit), false);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        Assert.assertEquals(gasLimit*totalTxsNumber, blockResult.getGasUsed());
    }

    @Test
    public void withTheSublistsFullTheLastTransactionShouldNotFit() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        long blockGasLimit = GasCost.toGas(parent.getGasLimit());
        int gasLimit = 21000;
        int transactionNumberToFillParallelSublist = (int) (blockGasLimit / gasLimit);
        int totalNumberOfSublists = Constants.getTransactionExecutionThreads() + 1;
        int totalTxs = (transactionNumberToFillParallelSublist) * totalNumberOfSublists + 1;
        Block block = getBlockWithNIndependentTransactions(totalTxs, BigInteger.valueOf(gasLimit), false);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());
        Assert.assertEquals(totalTxs, blockResult.getExecutedTransactions().size() + 1);
    }

    @Test
    public void withSequentialSublistFullRemascTxShouldFit() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        long blockGasLimit = GasCost.toGas(parent.getGasLimit());
        int gasLimit = 21000;
        int transactionNumberToFillASublist = (int) (blockGasLimit / gasLimit);
        int totalNumberOfSublists = Constants.getTransactionExecutionThreads() + 1;
        int expectedNumberOfTx = transactionNumberToFillASublist* totalNumberOfSublists + 1;
        Block block = getBlockWithNIndependentTransactions(transactionNumberToFillASublist * totalNumberOfSublists, BigInteger.valueOf(gasLimit), true);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());
        Assert.assertEquals(expectedNumberOfTx, blockResult.getExecutedTransactions().size());
    }

    @Test
    public void executeParallelBlocksWithDifferentSubsets() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        Block block1 = getBlockWithTenTransactions(new short[]{2, 4, 6, 8});
        BlockResult result1 = executor.execute(null, 0, block1, parent.getHeader(), true, false);


        Block block2 = getBlockWithTenTransactions(new short[]{5});
        BlockResult result2 = executor.execute(null, 0, block2, parent.getHeader(), true, false);

        Assert.assertArrayEquals(result2.getFinalState().getHash().getBytes(), result1.getFinalState().getHash().getBytes());
    }

    @Test
    public void executeParallelBlockAgainstSequentialBlock() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        Block pBlock = getBlockWithTenTransactions(new short[]{2, 4, 6, 8});
        BlockResult parallelResult = executor.execute(null, 0, pBlock, parent.getHeader(), true, false);


        Block sBlock = getBlockWithTenTransactions(null);
        BlockResult seqResult = executor.executeForMining(sBlock, parent.getHeader(), true, false);

        Assert.assertEquals(pBlock.getTransactionsList().size(), parallelResult.getExecutedTransactions().size());
        Assert.assertArrayEquals(seqResult.getFinalState().getHash().getBytes(), parallelResult.getFinalState().getHash().getBytes());
    }

    @Test
    public void executeInvalidParallelBlockDueToCollision() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        Block pBlock = getBlockWithTwoDependentTransactions(new short[]{1, 2});
        BlockResult result = executor.execute(null, 0, pBlock, parent.getHeader(), true, false);
        Assert.assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
    }

    @Test
    public void ifThereIsACollisionBetweenParallelAndSequentialSublistsItShouldNotBeConsidered() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        Block pBlock = getBlockWithTwoDependentTransactions(new short[]{1});
        BlockResult result = executor.execute(null, 0, pBlock, parent.getHeader(), true, false);
        Assert.assertTrue(pBlock.getTransactionsList().containsAll(result.getExecutedTransactions()));
        Assert.assertEquals(pBlock.getTransactionsList().size(), result.getExecutedTransactions().size());
    }

    @Test
    public void executeParallelBlockTwice() {
        if (!activeRskip144) {
            return;
        }
        Block parent = blockchain.getBestBlock();
        Block block1 = getBlockWithTenTransactions(new short[]{2, 4, 6, 8});
        BlockResult result1 = executor.execute(null, 0, block1, parent.getHeader(), true, false);


        Block block2 = getBlockWithTenTransactions(new short[]{2, 4, 6, 8});
        BlockResult result2 = executor.execute(null, 0, block2, parent.getHeader(), true, false);

        Assert.assertArrayEquals(result2.getFinalState().getHash().getBytes(), result1.getFinalState().getHash().getBytes());
        Assert.assertArrayEquals(block1.getHash().getBytes(), block2.getHash().getBytes());
    }

    private void testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition (int txAmount, short [] expectedSublistsEdges) {
        Block block = getBlockWithNIndependentTransactions(txAmount, BigInteger.valueOf(21000), true);

        assertBlockResultHasTxEdgesAndRemascAtLastPosition(block, txAmount, expectedSublistsEdges);
    }

    private void assertBlockResultHasTxEdgesAndRemascAtLastPosition (Block block, int txAmount, short [] expectedSublistsEdges) {
        Block parent = blockchain.getBestBlock();
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        int expectedTxSize = txAmount + 1;

        Assert.assertArrayEquals(expectedSublistsEdges, blockResult.getTxEdges());
        Assert.assertEquals(expectedTxSize, blockResult.getExecutedTransactions().size());
        Assert.assertTrue(blockResult.getExecutedTransactions().get(txAmount).isRemascTransaction(txAmount, expectedTxSize));
    }

    @Test
    public void blockWithOnlyRemascShouldGoToSequentialSublist () {
        if (!activeRskip144) return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(0, new short[]{});
    }

    @Test
    public void blockWithOneTxRemascShouldGoToSequentialSublist () {
        if (!activeRskip144) return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(1, new short[]{ 1 });
    }

    @Test
    public void blockWithManyTxsRemascShouldGoToSequentialSublist () {
        if (!activeRskip144) return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(3, new short[]{ 1, 2, 3 });
    }

    @Test
    public void blockWithMoreThanThreadsTxsRemascShouldGoToSequentialSublist () {
        if (!activeRskip144) return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(5, new short[]{ 2, 3, 4, 5 });
    }

    @Test
    public void blockWithExcludedTransactionHasRemascInSequentialSublist () {
        if (!activeRskip144) return;
        Block block = createBlockWithExcludedTransaction(true);
        assertBlockResultHasTxEdgesAndRemascAtLastPosition(block, 0, new short[]{});
    }

    @Test
    public void validateStateRootWithRskip126DisabledAndValidStateRoot() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(trieStore);

        Block block = new BlockGenerator(Constants.regtest(), activationConfig).getBlock(1);
        block.setStateRoot(trie.getHash().getBytes());

        BlockResult blockResult = new BlockResult(block, Collections.emptyList(), Collections.emptyList(), new short[0], 0,
                Coin.ZERO, trie);

//        RskSystemProperties cfg = spy(CONFIG);

        ActivationConfig activationConfig = spy(cfg.getActivationConfig());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP126), anyLong());
        doReturn(activationConfig).when(cfg).getActivationConfig();

        BlockExecutor executor = buildBlockExecutor(trieStore, cfg, activeRskip144, false);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertTrue(executor.validateStateRoot(block.getHeader(), blockResult));
    }

    @Test
    public void validateStateRootWithRskip126DisabledAndInvalidStateRoot() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(trieStore);

        Block block = new BlockGenerator(Constants.regtest(), activationConfig).getBlock(1);
        block.setStateRoot(new byte[] { 1, 2, 3, 4 });

        BlockResult blockResult = new BlockResult(block, Collections.emptyList(), Collections.emptyList(), new short[0], 0,
                Coin.ZERO, trie);

//        RskSystemProperties cfg = spy(CONFIG);

//        ActivationConfig activationConfig = spy(cfg.getActivationConfig());
        boolean rskip126IsActive = false;
//        doReturn(activationConfig).when(cfg).getActivationConfig();

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, rskip126IsActive);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertTrue(executor.validateStateRoot(block.getHeader(), blockResult));
    }

    @Test
    public void validateBlock() {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertTrue(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadStateRoot() {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        byte[] stateRoot = block.getStateRoot();
        stateRoot[0] = (byte) ((stateRoot[0] + 1) % 256);
        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadReceiptsRoot() {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        byte[] receiptsRoot = block.getReceiptsRoot();
        receiptsRoot[0] = (byte) ((receiptsRoot[0] + 1) % 256);
        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadGasUsed() {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        block.getHeader().setGasUsed(0);
        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadPaidFees() {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        block.getHeader().setPaidFees(Coin.ZERO);
        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadLogsBloom() {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        byte[] logBloom = block.getLogBloom();
        logBloom[0] = (byte) ((logBloom[0] + 1) % 256);
        short[] expectedEdges = activeRskip144 ? new short[]{(short) block.getTransactionsList().size()} : null;

        Assert.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    private static TestObjects generateBlockWithOneTransaction(Boolean activeRskip144, boolean rskip126IsActive) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, rskip126IsActive);

        Transaction tx1 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx1.sign(account.getEcKey().getPrivKeyBytes());
        Transaction tx = tx1;
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        List<BlockHeader> uncles = new ArrayList<>();

        // getGenesisBlock() modifies the repository, adding some pre-mined accounts
        // Not nice for a getter, but it is what it is :(
        Block genesis = BlockChainImplTest.getGenesisBlock(trieStore);
        genesis.setStateRoot(repository.getRoot());

        // Returns the root state prior block execution but after loading
        // some sample accounts (account/account2) and the premined accounts
        // in genesis.
        byte[] rootPriorExecution = repository.getRoot();

        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFill(block, genesis.getHeader());
        repository.save();

        return new TestObjects(trieStore, block, genesis, tx, account, rootPriorExecution);
    }

    private Block getBlockWithOneTransaction() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        Transaction tx = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        List<Transaction> txs = Collections.singletonList(
                tx
        );

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private Block getBlockWithTwoTransactions() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(60000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        Transaction tx = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()).add(BigInteger.ONE))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        Transaction tx1 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(CONFIG.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx1.sign(account.getEcKey().getPrivKeyBytes());
        List<Transaction> txs = Arrays.asList(
                tx1,
                tx
        );

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private Block getBlockWithTwoDependentTransactions(short[] edges) {
        int nTxs = 2;

        Repository track = repository.startTracking();
        List<Account> accounts = new LinkedList<>();

        for (int i = 0; i < nTxs; i++) {
            accounts.add(createAccount("accounttest" + i, track, Coin.valueOf(60000)));
        }
        track.commit();
        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        List<Transaction> txs = new LinkedList<>();

        for (int i = 0; i < nTxs; i++) {
            Transaction tx = Transaction.builder()
                    .nonce(BigInteger.ZERO)
                    .gasPrice(BigInteger.ONE)
                    .gasLimit(BigInteger.valueOf(21000))
                    .destination(accounts.get((i + 1) % 2).getAddress())
                    .chainId(CONFIG.getNetworkConstants().getChainId())
                    .value(BigInteger.TEN)
                    .build();
            tx.sign(accounts.get(i).getEcKey().getPrivKeyBytes());
            txs.add(tx);
        }
        List<BlockHeader> uncles = new ArrayList<>();

        return new BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlock(
                        bestBlock,
                        txs,
                        uncles,
                        1,
                        null,
                        bestBlock.getGasLimit(),
                        bestBlock.getCoinbase(),
                        edges
                );
    }

    private Block getBlockWithTenTransactions(short[] edges) {
        int nTxs = 10;
        int nAccounts = nTxs * 2;
        Repository track = repository.startTracking();
        List<Account> accounts = new LinkedList<>();

        for (int i = 0; i < nAccounts; i++) {
            accounts.add(createAccount("accounttest" + i, track, Coin.valueOf(60000)));
        }
        track.commit();
        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        List<Transaction> txs = new LinkedList<>();

        for (int i = 0; i < nTxs; i++) {
            Transaction tx = Transaction.builder()
                    .nonce(BigInteger.ZERO)
                    .gasPrice(BigInteger.ONE)
                    .gasLimit(BigInteger.valueOf(21000))
                    .destination(accounts.get(i + nTxs).getAddress())
                    .chainId(CONFIG.getNetworkConstants().getChainId())
                    .value(BigInteger.TEN)
                    .build();
            tx.sign(accounts.get(i).getEcKey().getPrivKeyBytes());
            txs.add(tx);
        }
        List<BlockHeader> uncles = new ArrayList<>();

        return new BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlock(
                        bestBlock,
                        txs,
                        uncles,
                        1,
                        null,
                        bestBlock.getGasLimit(),
                        bestBlock.getCoinbase(),
                        edges
                );
    }

    private Block getBlockWithNIndependentTransactions(int txNumber, BigInteger txGasLimit, boolean withRemasc) {
        int nAccounts = txNumber * 2;
        Repository track = repository.startTracking();
        List<Account> accounts = new LinkedList<>();

        for (int i = 0; i < nAccounts; i++) {
            accounts.add(createAccount("accounttest" + i, track, Coin.valueOf(600000)));
        }
        track.commit();
        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        List<Transaction> txs = new LinkedList<>();

        for (int i = 0; i < txNumber; i++) {
            Transaction tx = Transaction.builder()
                    .nonce(BigInteger.ZERO)
                    .gasPrice(BigInteger.ONE)
                    .gasLimit(txGasLimit)
                    .destination(accounts.get(i + txNumber).getAddress())
                    .chainId(CONFIG.getNetworkConstants().getChainId())
                    .value(BigInteger.TEN)
                    .build();
            tx.sign(accounts.get(i).getEcKey().getPrivKeyBytes());
            txs.add(tx);
        }

        if (withRemasc) {
            txs.add(new RemascTransaction(1L));
        }

        List<BlockHeader> uncles = new ArrayList<>();

        return new BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlock(
                        bestBlock,
                        txs,
                        uncles,
                        1,
                        null,
                        bestBlock.getGasLimit(),
                        bestBlock.getCoinbase(),
                        null
                );
    }

    public static Account createAccount(String seed, Repository repository, Coin balance) {
        Account account = createAccount(seed);
        repository.createAccount(account.getAddress());
        repository.addBalance(account.getAddress(), balance);
        return account;
    }

    public static Account createAccount(String seed) {
        byte[] privateKeyBytes = HashUtil.keccak256(seed.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account = new Account(key);
        return account;
    }

    //////////////////////////////////////////////
    // Testing strange Txs
    /////////////////////////////////////////////
    @Test(expected = RuntimeException.class)
    public void executeBlocksWithOneStrangeTransactions1() {
        // will fail to create an address that is not 20 bytes long
        executeBlockWithOneStrangeTransaction(true, false, generateBlockWithOneStrangeTransaction(0));
    }

    @Test(expected = RuntimeException.class)
    public void executeBlocksWithOneStrangeTransactions2() {
        // will fail to create an address that is not 20 bytes long
        executeBlockWithOneStrangeTransaction(true, true, generateBlockWithOneStrangeTransaction(1));
    }

    @Test
    public void executeBlocksWithOneStrangeTransactions3() {
        // the wrongly-encoded value parameter will be re-encoded with the correct serialization and won't fail
        executeBlockWithOneStrangeTransaction(false, false, generateBlockWithOneStrangeTransaction(2));
    }

    private void executeBlockWithOneStrangeTransaction(
            boolean mustFailValidation,
            boolean mustFailExecution,
            TestObjects objects) {
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        TrieStore trieStore = objects.getTrieStore();
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Repository repository = new MutableRepository(trieStore,
                trieStore.retrieve(objects.getParent().getStateRoot()).get());
        Transaction tx = objects.getTransaction();
        Account account = objects.getAccount();

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        // Only adding one rule
        validatorBuilder.addBlockTxsFieldsValidationRule();
        BlockValidatorImpl validator = validatorBuilder.build();

        Assert.assertEquals(validator.isValid(block), !mustFailValidation);
        if (mustFailValidation) {
            // If it fails validation, is it important if it fails or not execution? I don't think so.
            return;
        }

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false);

        Assert.assertNotNull(result);
        if (mustFailExecution) {
            Assert.assertEquals(result, BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT);
            return;
        }

        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());

        Assert.assertEquals(21000, result.getGasUsed());
        Assert.assertEquals(Coin.valueOf(21000), result.getPaidFees());

        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assert.assertEquals(256, calculatedLogsBloom.length);
        Assert.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    public TestObjects generateBlockWithOneStrangeTransaction(int strangeTransactionType) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = createStrangeTransaction(
                account,
                account2,
                BigInteger.TEN,
                repository.getNonce(account.getAddress()),
                strangeTransactionType
        );
        txs.add(tx);

        List<BlockHeader> uncles = new ArrayList<>();

        Block genesis = BlockChainImplTest.getGenesisBlock(trieStore);
        genesis.setStateRoot(repository.getRoot());
        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFillReal(block, genesis.getHeader()); // Forces all transactions included
        repository.save();

        return new TestObjects(trieStore, block, genesis, tx, account);
    }

    private byte[] calculateTxTrieRoot(List<Transaction> transactions, long blockNumber) {
        return BlockHashesHelper.getTxTrieRoot(
                transactions,
                CONFIG.getActivationConfig().isActive(ConsensusRule.RSKIP126, blockNumber)
        );
    }

    private static Transaction createStrangeTransaction(
            Account sender, Account receiver,
            BigInteger value, BigInteger nonce, int strangeTransactionType) {
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        byte[] to = receiver.getAddress().getBytes();
        byte[] gasLimitData = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21000));
        byte[] valueData = BigIntegers.asUnsignedByteArray(value);

        if (strangeTransactionType == 0) {
            to = new byte[1]; // one zero
            to[0] = 127;
        } else if (strangeTransactionType == 1) {
            to = new byte[1024];
            java.util.Arrays.fill(to, (byte) -1); // fill with 0xff
        } else {
            // Bad encoding for value
            byte[] newValueData = new byte[1024];
            System.arraycopy(valueData, 0, newValueData, 1024 - valueData.length, valueData.length);
            valueData = newValueData;
        }

        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(BigInteger.ONE)
                .gasLimit(gasLimitData)
                .destination(to)
                .value(valueData)
                .build(); // no data
        tx.sign(privateKeyBytes);
        return tx;
    }

    private static byte[] sha3(byte[] input) {
        Keccak256 digest = new Keccak256();
        digest.update(input);
        return digest.digest();
    }

    private static BlockExecutor buildBlockExecutor(TrieStore store, Boolean activeRskip144, boolean rskip126IsActive) {
        return buildBlockExecutor(store, CONFIG, activeRskip144, rskip126IsActive);
    }

    private static BlockExecutor buildBlockExecutor(TrieStore store, RskSystemProperties config, Boolean activeRskip144, Boolean activeRskip126) {
        RskSystemProperties cfg = spy(config);
        doReturn(activationConfig).when(cfg).getActivationConfig();
        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        doReturn(activeRskip126).when(activationConfig).isActive(eq(RSKIP126), anyLong());


        StateRootHandler stateRootHandler = new StateRootHandler(cfg.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                cfg.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, cfg.getNetworkConstants().getBridgeConstants(), cfg.getActivationConfig());

        return new BlockExecutor(
                cfg.getActivationConfig(),
                new RepositoryLocator(store, stateRootHandler),
                new TransactionExecutorFactory(
                        cfg,
                        null,
                        null,
                        BLOCK_FACTORY,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(cfg, bridgeSupportFactory),
                        new BlockTxSignatureCache(new ReceivedTxSignatureCache())
                )
        );
    }

    public static class TestObjects {
        private TrieStore trieStore;
        private Block block;
        private Block parent;
        private Transaction transaction;
        private Account account;
        byte[] rootPriorExecution;


        public TestObjects(TrieStore trieStore, Block block, Block parent, Transaction transaction, Account account) {
            this.trieStore = trieStore;
            this.block = block;
            this.parent = parent;
            this.transaction = transaction;
            this.account = account;
        }

        public TestObjects(
                TrieStore trieStore,
                Block block,
                Block parent,
                Transaction transaction,
                Account account,
                byte[] rootPriorExecution) {
            this.trieStore = trieStore;
            this.block = block;
            this.parent = parent;
            this.transaction = transaction;
            this.account = account;
            this.rootPriorExecution = rootPriorExecution;
        }

        public TrieStore getTrieStore() {
            return this.trieStore;
        }

        public Block getBlock() {
            return this.block;
        }

        public Block getParent() {
            return this.parent;
        }

        public Transaction getTransaction() {
            return this.transaction;
        }

        public Account getAccount() {
            return this.account;
        }
    }

    public static class SimpleEthereumListener extends TestCompositeEthereumListener {
        private Block latestBlock;
        private Block bestBlock;

        @Override
        public void trace(String output) {
        }

        @Override
        public void onNodeDiscovered(Node node) {

        }

        @Override
        public void onHandShakePeer(Channel channel, HelloMessage helloMessage) {

        }

        @Override
        public void onEthStatusUpdated(Channel channel, StatusMessage status) {

        }

        @Override
        public void onRecvMessage(Channel channel, Message message) {

        }

        @Override
        public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
            bestBlock = block;
        }

        public Block getBestBlock() {
            return bestBlock;
        }

        @Override
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            latestBlock = block;
        }

        public Block getLatestBlock() {
            return latestBlock;
        }

        @Override
        public void onPeerDisconnect(String host, long port) {

        }

        @Override
        public void onPendingTransactionsReceived(List<Transaction> transactions) {

        }

        @Override
        public void onTransactionPoolChanged(TransactionPool transactionPool) {

        }

        @Override
        public void onNoConnections() {

        }

        @Override
        public void onPeerAddedToSyncPool(Channel peer) {

        }

        @Override
        public void onLongSyncDone() {

        }

        @Override
        public void onLongSyncStarted() {

        }
    }
}
