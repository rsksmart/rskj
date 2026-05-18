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

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP126;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP144;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderV2;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionReceipt;
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
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.typesafe.config.ConfigValueFactory;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.db.MutableTrieImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.union.UnionBridgeStorageIndexKey;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;

/**
 * Created by ajlopez on 29/07/2016.
 */
public class BlockExecutorTest {
    private static final byte[] EMPTY_TRIE_HASH = sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    private static final boolean RSKIP_126_IS_ACTIVE = true;
    private static final long MIN_SEQUENTIAL_SET_GAS_LIMIT = Constants.regtest().getMinSequentialSetGasLimit();

    private final TestSystemProperties config = new TestSystemProperties();
    private final ActivationConfig activationConfig = spy(config.getActivationConfig());
    private final BlockFactory BLOCK_FACTORY = new BlockFactory(activationConfig);

    @TempDir
    public Path tempDir;

    private Blockchain blockchain;
    private TrieStore trieStore;
    private RepositorySnapshot repository;

    public static Account createAccount(String seed, Repository repository, Coin balance) {
        Account account = createAccount(seed);
        repository.createAccount(account.getAddress());
        repository.addBalance(account.getAddress(), balance);
        return account;
    }

    public static Account createAccount(String seed) {
        byte[] privateKeyBytes = HashUtil.keccak256(seed.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        return new Account(key);
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
        digest.update(Bytes.of(input));
        return digest.digest();
    }

    @BeforeEach
    public void setUp() {
        RskTestFactory objects = new RskTestFactory(tempDir, config);
        blockchain = objects.getBlockchain();
        trieStore = objects.getTrieStore();
        repository = objects.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithoutTransaction(Boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        Block parent = blockchain.getBestBlock();
        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(parent);
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false, true);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getTransactionReceipts());
        Assertions.assertTrue(result.getTransactionReceipts().isEmpty());
        Assertions.assertArrayEquals(repository.getRoot(), parent.getStateRoot());
        Assertions.assertArrayEquals(repository.getRoot(), result.getFinalState().getHash().getBytes());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithOneTransaction(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        executor.setRegisterProgramResults(false);
        Block block = getBlockWithOneTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();
        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false, true);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getTransactionReceipts());
        Assertions.assertFalse(result.getTransactionReceipts().isEmpty());
        Assertions.assertEquals(1, result.getTransactionReceipts().size());

        Assertions.assertNull(executor.getProgramResult(tx.getHash()));

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assertions.assertEquals(tx, receipt.getTransaction());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assertions.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assertions.assertEquals(21000, result.getGasUsed());
        Assertions.assertEquals(21000, result.getPaidFees().asBigInteger().intValueExact());

        Assertions.assertFalse(Arrays.equals(repository.getRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assertions.assertEquals(256, calculatedLogsBloom.length);
        Assertions.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account);

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account);

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithOneTransactionAndCollectingProgramResults(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        executor.setRegisterProgramResults(true);
        Block block = getBlockWithOneTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();
        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false, true);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getTransactionReceipts());
        Assertions.assertFalse(result.getTransactionReceipts().isEmpty());
        Assertions.assertEquals(1, result.getTransactionReceipts().size());

        Assertions.assertNotNull(executor.getProgramResult(tx.getHash()));
        executor.setRegisterProgramResults(false);

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assertions.assertEquals(tx, receipt.getTransaction());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assertions.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assertions.assertEquals(21000, result.getGasUsed());
        Assertions.assertEquals(21000, result.getPaidFees().asBigInteger().intValueExact());

        Assertions.assertFalse(Arrays.equals(repository.getRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assertions.assertEquals(256, calculatedLogsBloom.length);
        Assertions.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account);

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account);

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithTwoTransactions(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block block = getBlockWithTwoTransactions(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx1 = block.getTransactionsList().get(0);
        Transaction tx2 = block.getTransactionsList().get(1);
        RskAddress account = tx1.getSender();
        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false, true);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertNotNull(result);

        Assertions.assertNotNull(result.getTransactionReceipts());
        Assertions.assertFalse(result.getTransactionReceipts().isEmpty());
        Assertions.assertEquals(2, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assertions.assertEquals(tx1, receipt.getTransaction());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assertions.assertEquals(21000, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assertions.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        receipt = result.getTransactionReceipts().get(1);
        Assertions.assertEquals(tx2, receipt.getTransaction());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assertions.assertEquals(42000, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assertions.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assertions.assertEquals(42000, result.getGasUsed());
        Assertions.assertEquals(42000, result.getPaidFees().asBigInteger().intValueExact());

        // here is the problem: in the prior code repository root would never be
        // overwritten by childs
        // while the new code does overwrite the root.
        // Which semantic is correct ? I don't know

        Assertions.assertFalse(Arrays.equals(parent.getStateRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assertions.assertEquals(256, calculatedLogsBloom.length);
        Assertions.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account);

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(60000), accountState.getBalance().asBigInteger());

        // here is the papa. my commit changes stateroot while previous commit did not.

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account);

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(60000 - 42000 - 20), accountState.getBalance().asBigInteger());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeAndFillBlockWithNoSavingToStore(boolean activeRskip144) {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        TrieStore trieStore = objects.getTrieStore();
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        executor.executeAndFill(block, parent.getHeader());

        Assertions.assertEquals(Optional.empty(), trieStore.retrieve(block.getStateRoot()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithSavingToStore(boolean activeRskip144) {
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        TrieStore trieStore = objects.getTrieStore();
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        BlockResult result = executor.execute(null, 0, block, parent.getHeader(), false, false, true);

        Assertions.assertEquals(trieStore.retrieve(block.getStateRoot()), Optional.of(result.getFinalState()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeAndFillBlockWithOneTransaction(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false, true);
        executor.executeAndFill(block, parent.getHeader());

        byte[] calculatedReceiptsRoot = BlockHashesHelper.calculateReceiptsTrieRoot(result.getTransactionReceipts(),
                true);
        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertArrayEquals(calculatedReceiptsRoot, block.getReceiptsRoot());
        Assertions.assertArrayEquals(result.getFinalState().getHash().getBytes(), block.getStateRoot());
        Assertions.assertEquals(result.getGasUsed(), block.getGasUsed());
        Assertions.assertEquals(result.getPaidFees(), block.getFeesPaidToMiner());
        Assertions.assertArrayEquals(BlockExecutor.calculateLogsBloom(result.getTransactionReceipts()),
                block.getLogBloom());

        Assertions.assertEquals(3000000, new BigInteger(1, block.getGasLimit()).longValue());
    }

    private Block createBlockWithExcludedTransaction(boolean withRemasc, boolean activeRskip144) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.ZERO);

        track.commit();

        Assertions.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        Transaction tx3 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
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
                .chainId(config.getNetworkConstants().getChainId())
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

        Assertions.assertEquals(tx, block.getTransactionsList().get(0));
        Assertions.assertArrayEquals(
                calculateTxTrieRoot(expectedTxList, block.getNumber()),
                block.getTxTrieRoot());

        return block;
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeAndFillBlockWithTxToExcludeBecauseSenderHasNoBalance(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        Block block = createBlockWithExcludedTransaction(false, activeRskip144);

        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        // Check tx2 was excluded

        Assertions.assertEquals(1, block.getTransactionsList().size());

        Assertions.assertEquals(3141592, new BigInteger(1, block.getGasLimit()).longValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithTxThatMakesBlockInvalidSenderHasNoBalance(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.ZERO);

        track.commit();

        Assertions.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        Transaction tx3 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
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
                .chainId(config.getNetworkConstants().getChainId())
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

        BlockResult result = executor.executeForMining(block, genesis.getHeader(), false, false, true);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeSequentiallyATransactionAndGasShouldBeSubtractedCorrectly(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }

        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        long expectedAccumulatedGas = 21000L;

        Block block = getBlockWithNIndependentTransactions(1, BigInteger.valueOf(expectedAccumulatedGas), false);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());
        short[] expectedEdges = expectedEdgesForIndependentTransactions(block, 1, expectedAccumulatedGas);

        Assertions.assertEquals(block.getTransactionsList(), blockResult.getExecutedTransactions());
        Assertions.assertEquals(expectedAccumulatedGas, blockResult.getGasUsed());
        Assertions.assertArrayEquals(expectedEdges, blockResult.getTxEdges());

        List<TransactionReceipt> transactionReceipts = blockResult.getTransactionReceipts();
        for (TransactionReceipt receipt : transactionReceipts) {
            Assertions.assertEquals(expectedAccumulatedGas, GasCost.toGas(receipt.getCumulativeGas()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeSequentiallyTenIndependentTxsAndThemShouldGoInBothSublists(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }
        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        long txGasLimit = 21000L;
        short[] expectedEdges;
        Block parent = blockchain.getBestBlock();
        int numberOfTxs = 12;

        Block block = getBlockWithNIndependentTransactions(numberOfTxs, BigInteger.valueOf(txGasLimit), false);
        expectedEdges = expectedEdgesForIndependentTransactions(block, numberOfTxs, txGasLimit);
        List<Transaction> txs = block.getTransactionsList();
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        Assertions.assertEquals(txs.size(), blockResult.getExecutedTransactions().size());
        Assertions.assertTrue(txs.containsAll(blockResult.getExecutedTransactions()));
        Assertions.assertArrayEquals(expectedEdges, blockResult.getTxEdges());
        Assertions.assertEquals(txGasLimit * numberOfTxs, blockResult.getGasUsed());

        List<TransactionReceipt> transactionReceipts = blockResult.getTransactionReceipts();
        long accumulatedGasUsed = 0L;
        short i = 0;
        short edgeIndex = 0;
        for (TransactionReceipt receipt : transactionReceipts) {
            boolean isFromADifferentSublist = (edgeIndex < expectedEdges.length) && (i == expectedEdges[edgeIndex]);
            if (isFromADifferentSublist) {
                edgeIndex++;
                accumulatedGasUsed = 0L;
            }

            accumulatedGasUsed += txGasLimit;
            Assertions.assertEquals(accumulatedGasUsed, GasCost.toGas(receipt.getCumulativeGas()));
            i++;
        }

        Assertions.assertEquals(i, transactionReceipts.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void gasUsedShouldNeverSurprassBlockGasLimit(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        int gasLimit = 21000;
        int transactionNumberToFillSequentialSublist = (int) (BlockUtils.getSublistGasLimit(parent, true,
                MIN_SEQUENTIAL_SET_GAS_LIMIT) / gasLimit);
        int transactionNumberToFillParallelSublist = (int) (BlockUtils.getSublistGasLimit(parent, false,
                MIN_SEQUENTIAL_SET_GAS_LIMIT) / gasLimit);
        int totalNumberOfParallelSublists = Constants.getTransactionExecutionThreads();
        int totalTxs = (transactionNumberToFillParallelSublist * totalNumberOfParallelSublists)
                + transactionNumberToFillSequentialSublist + 1;
        Block block = getBlockWithNIndependentTransactions(totalTxs, BigInteger.valueOf(gasLimit), false);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());
        Assertions.assertFalse(block.getGasUsed() > GasCost.toGas(block.getGasLimit()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void whenParallelSublistsAreFullTheLastTxShouldGoToSequential(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }
        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        int gasLimit = 21000;
        int numberOfTransactions = (int) (BlockUtils.getSublistGasLimit(parent, false, MIN_SEQUENTIAL_SET_GAS_LIMIT)
                / gasLimit);
        int transactionsInSequential = 1;
        Block block = getBlockWithNIndependentTransactions(
                numberOfTransactions * Constants.getTransactionExecutionThreads() + transactionsInSequential,
                BigInteger.valueOf(gasLimit), false);
        short[] expectedEdges = expectedEdgesForIndependentTransactions(
                block,
                numberOfTransactions * Constants.getTransactionExecutionThreads() + transactionsInSequential,
                gasLimit
        );
        List<Transaction> transactionsList = block.getTransactionsList();
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        Assertions.assertArrayEquals(expectedEdges, blockResult.getTxEdges());
        Assertions.assertEquals(transactionsList.size(), blockResult.getExecutedTransactions().size());
        Assertions.assertTrue(transactionsList.containsAll(blockResult.getExecutedTransactions()));

        List<TransactionReceipt> transactionReceipts = blockResult.getTransactionReceipts();
        long accumulatedGasUsed = 0L;
        short i = 0;
        short edgeIndex = 0;
        for (TransactionReceipt receipt : transactionReceipts) {
            accumulatedGasUsed += gasLimit;

            if ((edgeIndex < expectedEdges.length) && (i == expectedEdges[edgeIndex])) {
                edgeIndex++;
                accumulatedGasUsed = gasLimit;
            }
            Assertions.assertEquals(accumulatedGasUsed, GasCost.toGas(receipt.getCumulativeGas()));
            i++;
        }

        Assertions.assertEquals(i, transactionReceipts.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeATxInSequentialAndBlockResultShouldTrackTheGasUsedInTheBlock(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }
        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        int gasLimit = 21000;
        int transactionNumberToFillParallelSublist = (int) (BlockUtils.getSublistGasLimit(parent, false,
                MIN_SEQUENTIAL_SET_GAS_LIMIT) / gasLimit);
        int transactionsInSequential = 1;
        int totalTxsNumber = transactionNumberToFillParallelSublist * Constants.getTransactionExecutionThreads()
                + transactionsInSequential;
        Block block = getBlockWithNIndependentTransactions(totalTxsNumber, BigInteger.valueOf(gasLimit), false);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        Assertions.assertEquals((long) gasLimit * totalTxsNumber, blockResult.getGasUsed());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void withTheSublistsFullTheLastTransactionShouldNotFit(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }
        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        int gasLimit = 21000;
        int transactionNumberToFillSequentialSublist = (int) (BlockUtils.getSublistGasLimit(parent, true,
                MIN_SEQUENTIAL_SET_GAS_LIMIT) / gasLimit);
        int transactionNumberToFillParallelSublist = (int) (BlockUtils.getSublistGasLimit(parent, false,
                MIN_SEQUENTIAL_SET_GAS_LIMIT) / gasLimit);
        int totalNumberOfSublists = Constants.getTransactionExecutionThreads();
        int totalTxs = (transactionNumberToFillParallelSublist * totalNumberOfSublists)
                + transactionNumberToFillSequentialSublist + 1;
        Block block = getBlockWithNIndependentTransactions(totalTxs, BigInteger.valueOf(gasLimit), false);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());
        Assertions.assertEquals(totalTxs - 1, blockResult.getExecutedTransactions().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void withSequentialSublistFullRemascTxShouldFit(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }

        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        int gasLimit = 21000;
        int transactionNumberToFillSequentialSublist = (int) (BlockUtils.getSublistGasLimit(parent, true,
                MIN_SEQUENTIAL_SET_GAS_LIMIT) / gasLimit);
        int transactionNumberToFillParallelSublist = (int) (BlockUtils.getSublistGasLimit(parent, false,
                MIN_SEQUENTIAL_SET_GAS_LIMIT) / gasLimit);
        int totalNumberOfParallelSublists = Constants.getTransactionExecutionThreads();
        int expectedNumberOfTx = (transactionNumberToFillParallelSublist * totalNumberOfParallelSublists)
                + transactionNumberToFillSequentialSublist + 1;

        Block block = getBlockWithNIndependentTransactions(expectedNumberOfTx, BigInteger.valueOf(gasLimit), true);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());
        Assertions.assertEquals(expectedNumberOfTx, blockResult.getExecutedTransactions().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeParallelBlocksWithDifferentSubsets(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }

        doReturn(true).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        Block block1 = getBlockWithTenTransactions(new short[] { 4, 8 });
        BlockResult result1 = executor.execute(null, 0, block1, parent.getHeader(), true, false, true);

        Block block2 = getBlockWithTenTransactions(new short[] { 5 });
        BlockResult result2 = executor.execute(null, 0, block2, parent.getHeader(), true, false, true);

        Assertions.assertArrayEquals(result2.getFinalState().getHash().getBytes(),
                result1.getFinalState().getHash().getBytes());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeParallelBlockAgainstSequentialBlock(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }

        doReturn(true).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        Block pBlock = getBlockWithTenTransactions(new short[] { 4, 8 });
        BlockResult parallelResult = executor.execute(null, 0, pBlock, parent.getHeader(), true, false, true);

        Block sBlock = getBlockWithTenTransactions(null);
        BlockResult seqResult = executor.executeForMining(sBlock, parent.getHeader(), true, false, true);

        Assertions.assertEquals(pBlock.getTransactionsList().size(), parallelResult.getExecutedTransactions().size());
        Assertions.assertArrayEquals(seqResult.getFinalState().getHash().getBytes(),
                parallelResult.getFinalState().getHash().getBytes());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeInvalidParallelBlockDueToCollision(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        Block pBlock = getBlockWithTwoDependentTransactions(new short[] { 1, 2 });
        BlockResult result = executor.execute(null, 0, pBlock, parent.getHeader(), true, false, true);
        Assertions.assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void whenExecuteATxWithGasLimitExceedingSublistGasLimitShouldNotBeInlcuded(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();

        Repository track = repository.startTracking();
        Account sender = createAccount("sender", track, Coin.valueOf(6000000));
        Account receiver = createAccount("receiver", track, Coin.valueOf(6000000));

        track.commit();
        parent.setStateRoot(repository.getRoot());
        long sequentialSublistGasLimit = BlockUtils.getSublistGasLimit(parent, true, MIN_SEQUENTIAL_SET_GAS_LIMIT);

        List<Transaction> txs = new LinkedList<>();
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(sequentialSublistGasLimit + 1))
                .destination(receiver.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        txs.add(tx);
        List<BlockHeader> uncles = new ArrayList<>();

        Block block = new BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlockUsingCoinbase(
                        parent,
                        txs,
                        uncles,
                        1,
                        null,
                        parent.getGasLimit(),
                        parent.getCoinbase(),
                        new short[] { 1 });

        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());
        Assertions.assertEquals(0, blockResult.getExecutedTransactions().size());

        BlockResult result = executor.execute(null, 0, block, parent.getHeader(), true, false, true);
        Assertions.assertEquals(0, result.getExecutedTransactions().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void ifThereIsACollisionBetweenParallelAndSequentialSublistsTxShouldNotBeConsidered(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        Block pBlock = getBlockWithTwoDependentTransactions(new short[] { 1 });
        BlockResult result = executor.execute(null, 0, pBlock, parent.getHeader(), true, false, true);
        Assertions.assertTrue(pBlock.getTransactionsList().containsAll(result.getExecutedTransactions()));
        Assertions.assertEquals(pBlock.getTransactionsList().size(), result.getExecutedTransactions().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeParallelBlockTwice(boolean activeRskip144) {
        if (!activeRskip144) {
            return;
        }

        doReturn(true).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = blockchain.getBestBlock();
        Block block1 = getBlockWithTenTransactions(new short[] { 4, 8 });
        BlockResult result1 = executor.execute(null, 0, block1, parent.getHeader(), true, false, true);

        Block block2 = getBlockWithTenTransactions(new short[] { 4, 8 });
        BlockResult result2 = executor.execute(null, 0, block2, parent.getHeader(), true, false, true);

        Assertions.assertArrayEquals(result2.getFinalState().getHash().getBytes(),
                result1.getFinalState().getHash().getBytes());
        Assertions.assertArrayEquals(block1.getHash().getBytes(), block2.getHash().getBytes());
    }

    private void testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(int txAmount, Boolean activeRskip144) {
        Block block = getBlockWithNIndependentTransactions(txAmount, BigInteger.valueOf(21000), true);

        assertBlockResultHasTxEdgesAndRemascAtLastPosition(block, txAmount, activeRskip144);
    }

    private void assertBlockResultHasTxEdgesAndRemascAtLastPosition(Block block, int txAmount, Boolean activeRskip144) {
        Block parent = blockchain.getBestBlock();
        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);
        BlockResult blockResult = executor.executeAndFill(block, parent.getHeader());

        int expectedTxSize = txAmount + 1;
        short[] expectedSublistsEdges = expectedEdgesForIndependentTransactions(block, txAmount, 21000L);

        Assertions.assertArrayEquals(expectedSublistsEdges, blockResult.getTxEdges());
        Assertions.assertEquals(expectedTxSize, blockResult.getExecutedTransactions().size());
        Assertions.assertTrue(
                blockResult.getExecutedTransactions().get(txAmount).isRemascTransaction(txAmount, expectedTxSize));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void blockWithOnlyRemascShouldGoToSequentialSublist(boolean activeRskip144) {
        if (!activeRskip144)
            return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(0, activeRskip144);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void blockWithOneTxRemascShouldGoToSequentialSublist(boolean activeRskip144) {
        if (!activeRskip144)
            return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(1, activeRskip144);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void blockWithManyTxsRemascShouldGoToSequentialSublist(boolean activeRskip144) {
        if (!activeRskip144)
            return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(2, activeRskip144);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void blockWithMoreThanThreadsTxsRemascShouldGoToSequentialSublist(boolean activeRskip144) {
        if (!activeRskip144)
            return;
        testBlockWithTxTxEdgesMatchAndRemascTxIsAtLastPosition(3, activeRskip144);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void blockWithExcludedTransactionHasRemascInSequentialSublist(boolean activeRskip144) {
        if (!activeRskip144)
            return;
        Block block = createBlockWithExcludedTransaction(true, activeRskip144);
        assertBlockResultHasTxEdgesAndRemascAtLastPosition(block, 0, activeRskip144);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void validateStateRootWithRskip126DisabledAndValidStateRoot(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(trieStore);

        Block block = new BlockGenerator(Constants.regtest(), activationConfig).getBlock(1);
        block.setStateRoot(trie.getHash().getBytes());

        BlockResult blockResult = new BlockResult(block, Collections.emptyList(), Collections.emptyList(), new short[0],
                0,
                Coin.ZERO, trie);

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, false);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertTrue(executor.validateStateRoot(block.getHeader(), blockResult));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void validateStateRootWithRskip126DisabledAndInvalidStateRoot(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(trieStore);

        Block block = new BlockGenerator(Constants.regtest(), activationConfig).getBlock(1);
        block.setStateRoot(new byte[] { 1, 2, 3, 4 });

        BlockResult blockResult = new BlockResult(block, Collections.emptyList(), Collections.emptyList(), new short[0],
                0,
                Coin.ZERO, trie);

        // RskSystemProperties cfg = spy(CONFIG);

        // ActivationConfig activationConfig = spy(cfg.getActivationConfig());
        boolean rskip126IsActive = false;
        // doReturn(activationConfig).when(cfg).getActivationConfig();

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, rskip126IsActive);

        short[] expectedEdges = activeRskip144 ? new short[0] : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertTrue(executor.validateStateRoot(block.getHeader(), blockResult));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void validateBlock(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertTrue(executor.executeAndValidate(block, parent.getHeader()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void invalidBlockBadStateRoot(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        byte[] stateRoot = block.getStateRoot();
        stateRoot[0] = (byte) ((stateRoot[0] + 1) % 256);
        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void invalidBlockBadReceiptsRoot(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        byte[] receiptsRoot = block.getReceiptsRoot();
        receiptsRoot[0] = (byte) ((receiptsRoot[0] + 1) % 256);
        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void invalidBlockBadGasUsed(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        block.getHeader().setGasUsed(0);
        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void invalidBlockBadPaidFees(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        block.getHeader().setPaidFees(Coin.ZERO);
        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void invalidBlockBadLogsBloom(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        TestObjects objects = generateBlockWithOneTransaction(activeRskip144, RSKIP_126_IS_ACTIVE);
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore(), activeRskip144, RSKIP_126_IS_ACTIVE);

        byte[] logBloom = block.getLogBloom();
        logBloom[0] = (byte) ((logBloom[0] + 1) % 256);
        short[] expectedEdges = activeRskip144 ? expectedEdgesForIndependentTransactions(block) : null;

        Assertions.assertArrayEquals(expectedEdges, block.getHeader().getTxExecutionSublistsEdges());
        Assertions.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    private TestObjects generateBlockWithOneTransaction(Boolean activeRskip144, boolean rskip126IsActive) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assertions.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, rskip126IsActive);

        Transaction tx1 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx1.sign(account.getEcKey().getPrivKeyBytes());
        Transaction tx = tx1;
        List<Transaction> txs = Collections.singletonList(tx);

        List<BlockHeader> uncles = new ArrayList<>();

        // getGenesisBlock() modifies the repository, adding some pre-mined accounts
        // Not nice for a getter, but it is what it is :(
        Block genesis = BlockChainImplTest.getGenesisBlock(trieStore);
        genesis.setStateRoot(repository.getRoot());

        // Returns the root state prior block execution but after loading
        // some sample accounts (account/account2) and the premined accounts
        // in genesis.
        byte[] rootPriorExecution = repository.getRoot();

        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(genesis, txs, uncles,
                1, null);

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
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        List<Transaction> txs = Collections.singletonList(
                tx);

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(bestBlock, txs, uncles, 1,
                null);
    }

    private Block getBlockWithTwoTransactions() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(60000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assertions.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        Transaction tx = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()).add(BigInteger.ONE))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        Transaction tx1 = Transaction
                .builder()
                .nonce(repository.getNonce(account.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(account2.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx1.sign(account.getEcKey().getPrivKeyBytes());
        List<Transaction> txs = Arrays.asList(
                tx1,
                tx);

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(bestBlock, txs, uncles, 1,
                null);
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
                    .chainId(config.getNetworkConstants().getChainId())
                    .value(BigInteger.TEN)
                    .build();
            tx.sign(accounts.get(i).getEcKey().getPrivKeyBytes());
            txs.add(tx);
        }
        List<BlockHeader> uncles = new ArrayList<>();
        byte[] gasLimit = getParallelTestGasLimit(bestBlock);

        return new BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlockUsingCoinbase(
                        bestBlock,
                        txs,
                        uncles,
                        1,
                        null,
                        gasLimit,
                        bestBlock.getCoinbase(),
                        edges);
    }

    /// ///////////////////////////////////////////
    // Testing strange Txs
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
                    .chainId(config.getNetworkConstants().getChainId())
                    .value(BigInteger.TEN)
                    .build();
            tx.sign(accounts.get(i).getEcKey().getPrivKeyBytes());
            txs.add(tx);
        }
        List<BlockHeader> uncles = new ArrayList<>();
        byte[] gasLimit = getParallelTestGasLimit(bestBlock);

        return new BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlockUsingCoinbase(
                        bestBlock,
                        txs,
                        uncles,
                        1,
                        null,
                        gasLimit,
                        bestBlock.getCoinbase(),
                        edges);
    }

    private byte[] getParallelTestGasLimit(Block parent) {
        long parentGasLimit = GasCost.toGas(parent.getGasLimit());
        long desiredGasLimit = parentGasLimit;
        if (desiredGasLimit <= MIN_SEQUENTIAL_SET_GAS_LIMIT) {
            desiredGasLimit = MIN_SEQUENTIAL_SET_GAS_LIMIT + 1_000_000L;
        }
        return BigInteger.valueOf(desiredGasLimit).toByteArray();
    }

    private short[] expectedEdgesForIndependentTransactions(Block block, int txCount, long txGasLimit) {
        long parallelSublistGasLimit = BlockUtils.getSublistGasLimit(block, false, MIN_SEQUENTIAL_SET_GAS_LIMIT);
        int sublistCount = Constants.getTransactionExecutionThreads();

        if (parallelSublistGasLimit <= 0 || txCount <= 0 || txGasLimit <= 0) {
            return new short[0];
        }

        long[] gasUsedBySublist = new long[sublistCount];
        int[] txsBySublist = new int[sublistCount];

        for (int i = 0; i < txCount; i++) {
            int candidate = -1;
            long lowestGasUsed = Long.MAX_VALUE;

            for (int sublistIndex = 0; sublistIndex < sublistCount; sublistIndex++) {
                long updatedGasUsed = gasUsedBySublist[sublistIndex] + txGasLimit;
                if (updatedGasUsed <= parallelSublistGasLimit && gasUsedBySublist[sublistIndex] < lowestGasUsed) {
                    candidate = sublistIndex;
                    lowestGasUsed = gasUsedBySublist[sublistIndex];
                }
            }

            if (candidate == -1) {
                break;
            }

            gasUsedBySublist[candidate] += txGasLimit;
            txsBySublist[candidate]++;
        }

        List<Short> edges = new ArrayList<>();
        short cumulative = 0;
        for (int count : txsBySublist) {
            if (count == 0) {
                continue;
            }
            cumulative += (short) count;
            edges.add(cumulative);
        }

        short[] result = new short[edges.size()];
        for (int i = 0; i < edges.size(); i++) {
            result[i] = edges.get(i);
        }
        return result;
    }

    private short[] expectedEdgesForIndependentTransactions(Block block) {
        if (block.getTransactionsList().isEmpty()) {
            return new short[0];
        }

        long txGasLimit = GasCost.toGas(block.getTransactionsList().get(0).getGasLimit());
        return expectedEdgesForIndependentTransactions(block, block.getTransactionsList().size(), txGasLimit);
    }

    private Block getBlockWithNIndependentTransactions(int numberOfTxs, BigInteger txGasLimit, boolean withRemasc) {
        int nAccounts = numberOfTxs * 2;
        Repository track = repository.startTracking();
        List<Account> accounts = new LinkedList<>();

        for (int i = 0; i < nAccounts; i++) {
            accounts.add(createAccount("accounttest" + i, track, Coin.valueOf(600000)));
        }
        track.commit();
        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        List<Transaction> txs = new LinkedList<>();

        for (int i = 0; i < numberOfTxs; i++) {
            Transaction tx = Transaction.builder()
                    .nonce(BigInteger.ZERO)
                    .gasPrice(BigInteger.ONE)
                    .gasLimit(txGasLimit)
                    .destination(accounts.get(i + numberOfTxs).getAddress())
                    .chainId(config.getNetworkConstants().getChainId())
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
                .createChildBlockUsingCoinbase(
                        bestBlock,
                        txs,
                        uncles,
                        1,
                        null,
                        bestBlock.getGasLimit(),
                        bestBlock.getCoinbase(),
                        null);
    }

    /// //////////////////////////////////////////
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlocksWithOneStrangeTransactions1(Boolean activeRskip144) {
        // will fail to create an address that is not 20 bytes long
        Assertions.assertThrows(RuntimeException.class,
                () -> generateBlockWithOneStrangeTransaction(0, activeRskip144));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlocksWithOneStrangeTransactions2(Boolean activeRskip144) {
        // will fail to create an address that is not 20 bytes long
        Assertions.assertThrows(RuntimeException.class,
                () -> generateBlockWithOneStrangeTransaction(1, activeRskip144));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlocksWithOneStrangeTransactions3(Boolean activeRskip144) {
        // the wrongly-encoded value parameter will be re-encoded with the correct
        // serialization and won't fail
        executeBlockWithOneStrangeTransaction(false, false, generateBlockWithOneStrangeTransaction(2, activeRskip144),
                activeRskip144);
    }

    private void executeBlockWithOneStrangeTransaction(
            boolean mustFailValidation,
            boolean mustFailExecution,
            TestObjects objects,
            Boolean activeRskip144) {
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

        Assertions.assertEquals(validator.isValid(block), !mustFailValidation);
        if (mustFailValidation) {
            // If it fails validation, is it important if it fails or not execution? I don't
            // think so.
            return;
        }

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false, true);

        Assertions.assertNotNull(result);
        if (mustFailExecution) {
            Assertions.assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
            return;
        }

        Assertions.assertNotNull(result.getTransactionReceipts());
        Assertions.assertFalse(result.getTransactionReceipts().isEmpty());
        Assertions.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assertions.assertEquals(tx, receipt.getTransaction());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assertions.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());

        Assertions.assertEquals(21000, result.getGasUsed());
        Assertions.assertEquals(Coin.valueOf(21000), result.getPaidFees());

        Assertions.assertFalse(Arrays.equals(repository.getRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assertions.assertEquals(256, calculatedLogsBloom.length);
        Assertions.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account.getAddress());

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account.getAddress());

        Assertions.assertNotNull(accountState);
        Assertions.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    public TestObjects generateBlockWithOneStrangeTransaction(int strangeTransactionType, Boolean activeRskip144) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assertions.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore, activeRskip144, RSKIP_126_IS_ACTIVE);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = createStrangeTransaction(
                account,
                account2,
                BigInteger.TEN,
                repository.getNonce(account.getAddress()),
                strangeTransactionType);
        txs.add(tx);

        List<BlockHeader> uncles = new ArrayList<>();

        Block genesis = BlockChainImplTest.getGenesisBlock(trieStore);
        genesis.setStateRoot(repository.getRoot());
        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(genesis, txs, uncles,
                1, null);

        executor.executeAndFillReal(block, genesis.getHeader()); // Forces all transactions included
        repository.save();

        return new TestObjects(trieStore, block, genesis, tx, account);
    }

    private byte[] calculateTxTrieRoot(List<Transaction> transactions, long blockNumber) {
        return BlockHashesHelper.getTxTrieRoot(
                transactions,
                config.getActivationConfig().isActive(ConsensusRule.RSKIP126, blockNumber));
    }

    private BlockExecutor buildBlockExecutor(TrieStore store, Boolean activeRskip144, boolean rskip126IsActive) {
        return buildBlockExecutor(store, config, activeRskip144, rskip126IsActive);
    }

    private BlockExecutor buildBlockExecutor(TrieStore store, RskSystemProperties config, Boolean activeRskip144,
            Boolean activeRskip126) {
        RskSystemProperties cfg = spy(config);
        doReturn(activationConfig).when(cfg).getActivationConfig();
        doReturn(activeRskip144).when(activationConfig).isActive(eq(RSKIP144), anyLong());
        doReturn(activeRskip126).when(activationConfig).isActive(eq(RSKIP126), anyLong());

        StateRootHandler stateRootHandler = new StateRootHandler(cfg.getActivationConfig(),
                new StateRootsStoreImpl(new HashMapDB()));

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                cfg.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, cfg.getNetworkConstants().getBridgeConstants(), cfg.getActivationConfig(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        return new BlockExecutor(
                new RepositoryLocator(store, stateRootHandler),
                new TransactionExecutorFactory(
                        cfg,
                        null,
                        null,
                        BLOCK_FACTORY,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(cfg, bridgeSupportFactory, signatureCache),
                        signatureCache),
                cfg);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithContractEmittingLogsUsingDsl(boolean isRSKIP144Activated) throws Exception {
        TestSystemProperties config = new TestSystemProperties(
                rawConfig -> rawConfig.withValue("blockchain.config.consensusRules.rskip144",
                        ConfigValueFactory.fromAnyRef(isRSKIP144Activated ? 1 : -1)));

        // Use DSL to set up the blockchain with log-emitting contract
        DslParser parser = DslParser.fromResource("dsl/log_indexes_pte.txt");
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Get the blocks created by DSL
        Block deployBlock = world.getBlockByName("b01");
        Block callBlock = world.getBlockByName("b02"); // Now contains all three transactions

        Assertions.assertNotNull(deployBlock);
        Assertions.assertNotNull(callBlock);

        // Verify the deployment transaction was successful
        TransactionReceipt deployReceipt = world.getTransactionReceiptByName("txDeploy");
        Assertions.assertNotNull(deployReceipt);
        Assertions.assertTrue(deployReceipt.isSuccessful());

        // Verify all three transactions are in the same block
        Assertions.assertEquals(3, callBlock.getTransactionsList().size(), "Block b02 should contain 3 transactions");

        // Verify the first function call transaction and its logs
        TransactionReceipt callReceipt = world.getTransactionReceiptByName("txCallEmit");
        Assertions.assertNotNull(callReceipt);
        Assertions.assertTrue(callReceipt.isSuccessful());

        // Most importantly: verify that logs were emitted
        Assertions.assertNotNull(callReceipt.getLogInfoList());
        Assertions.assertFalse(callReceipt.getLogInfoList().isEmpty());
        Assertions.assertEquals(2, callReceipt.getLogInfoList().size(), "Should emit exactly 2 logs");

        // Verify the second function call transaction and its logs
        TransactionReceipt callReceipt2 = world.getTransactionReceiptByName("txCallEmit2");
        Assertions.assertNotNull(callReceipt2);
        Assertions.assertTrue(callReceipt2.isSuccessful());
        Assertions.assertNotNull(callReceipt2.getLogInfoList());
        Assertions.assertFalse(callReceipt2.getLogInfoList().isEmpty());
        Assertions.assertEquals(2, callReceipt2.getLogInfoList().size(), "Second call should emit exactly 2 logs");

        // Verify the third function call transaction and its logs
        TransactionReceipt callReceipt3 = world.getTransactionReceiptByName("txCallEmit3");
        Assertions.assertNotNull(callReceipt3);
        Assertions.assertTrue(callReceipt3.isSuccessful());
        Assertions.assertNotNull(callReceipt3.getLogInfoList());
        Assertions.assertFalse(callReceipt3.getLogInfoList().isEmpty());
        Assertions.assertEquals(2, callReceipt3.getLogInfoList().size(), "Third call should emit exactly 2 logs");

        // Verify logs from first call contain the expected contract address
        org.ethereum.vm.LogInfo logInfo1 = callReceipt.getLogInfoList().get(0);
        org.ethereum.vm.LogInfo logInfo2 = callReceipt.getLogInfoList().get(1);

        // Verify logs from second call contain the expected contract address
        org.ethereum.vm.LogInfo logInfo3 = callReceipt2.getLogInfoList().get(0);
        org.ethereum.vm.LogInfo logInfo4 = callReceipt2.getLogInfoList().get(1);

        // Verify logs from third call contain the expected contract address
        org.ethereum.vm.LogInfo logInfo5 = callReceipt3.getLogInfoList().get(0);
        org.ethereum.vm.LogInfo logInfo6 = callReceipt3.getLogInfoList().get(1);

        // Verify that all logs from the same block have correct sequential log indexes
        // Since all transactions are in the same block, log indexes should be
        // sequential across all transactions:
        // Transaction 1 (txCallEmit) should have logs at indexes 0, 1
        // Transaction 2 (txCallEmit2) should have logs at indexes 2, 3
        // Transaction 3 (txCallEmit3) should have logs at indexes 4, 5
        Assertions.assertEquals(0, logInfo1.getLogIndex(), "First log should have index 0");
        Assertions.assertEquals(1, logInfo2.getLogIndex(), "Second log should have index 1");
        Assertions.assertEquals(2, logInfo3.getLogIndex(), "Third log should have index 2");
        Assertions.assertEquals(3, logInfo4.getLogIndex(), "Fourth log should have index 3");
        Assertions.assertEquals(4, logInfo5.getLogIndex(), "Fifth log should have index 4");
        Assertions.assertEquals(5, logInfo6.getLogIndex(), "Sixth log should have index 5");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithLogEmittingContractAndVerifyLogIndexes(boolean isRSKIP144Activated) throws Exception {
        TestSystemProperties config = new TestSystemProperties(
                rawConfig -> rawConfig.withValue("blockchain.config.consensusRules.rskip144",
                        ConfigValueFactory.fromAnyRef(isRSKIP144Activated ? 1 : -1)));

        // Now use DSL to create a block with log-emitting contract for log index
        // verification
        DslParser parser = DslParser.fromResource("dsl/log_indexes_pte.txt");
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Get the blocks created by DSL
        Block deployBlock = world.getBlockByName("b01");
        Block callBlock1 = world.getBlockByName("b02");
        Block callBlock2 = world.getBlockByName("b03");

        Assertions.assertNotNull(deployBlock);
        Assertions.assertNotNull(callBlock1);
        Assertions.assertNotNull(callBlock2);

        BlockResult result = world.getBlockExecutor().execute(null, 0, callBlock2, callBlock1.getHeader(), false, false,
                false);

        List<TransactionReceipt> transactionReceipts = result.getTransactionReceipts();

        Assertions.assertNotNull(transactionReceipts);

        Assertions.assertEquals(0, transactionReceipts.get(0).getLogInfoList().get(0).getLogIndex(),
                "First log should have index 0");
        Assertions.assertEquals(1, transactionReceipts.get(0).getLogInfoList().get(1).getLogIndex(),
                "Second log should have index 1");
        Assertions.assertEquals(2, transactionReceipts.get(1).getLogInfoList().get(0).getLogIndex(),
                "Third log should have index 2");
        Assertions.assertEquals(3, transactionReceipts.get(1).getLogInfoList().get(1).getLogIndex(),
                "Fourth log should have index 3");
        Assertions.assertEquals(4, transactionReceipts.get(2).getLogInfoList().get(0).getLogIndex(),
                "Fifth log should have index 4");
        Assertions.assertEquals(5, transactionReceipts.get(2).getLogInfoList().get(1).getLogIndex(),
                "Sixth log should have index 5");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void executeBlockWithBaseEventFromRepository(boolean activeRskip535) {
        // given
        doReturn(activeRskip535).when(activationConfig).isActive(eq(ConsensusRule.RSKIP535), anyLong());

        Block parent = blockchain.getBestBlock();
        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(parent);

        // Only test BlockHeaderV2 when RSKIP535 is active, as that's when it's created
        if (activeRskip535) {
            // Ensure we have a BlockHeaderV2
            Assertions.assertInstanceOf(BlockHeaderV2.class, block.getHeader(),
                    "Block should have BlockHeaderV2 header when RSKIP535 is active");

            // Create a real repository with the expected baseEvent value
            byte[] expectedBaseEvent = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 };
            Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
            repository.startTracking();

            // Set the baseEvent in the repository at the bridge address
            RskAddress bridgeAddress = PrecompiledContracts.BRIDGE_ADDR;
            DataWord baseEventKey = UnionBridgeStorageIndexKey.BASE_EVENT.getKey();
            repository.addStorageBytes(bridgeAddress, baseEventKey, expectedBaseEvent);
            repository.commit();

            // Update the parent block's state root to include our repository changes
            parent.setStateRoot(repository.getRoot());

            // when
            BlockExecutor executor = buildBlockExecutor(trieStore, true, RSKIP_126_IS_ACTIVE);

            // then
            BlockResult result = executor.executeAndFill(block, parent.getHeader());
            Assertions.assertNotNull(result);
            Assertions.assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
            Assertions.assertArrayEquals(expectedBaseEvent, result.getBlock().getBaseEvent(),
                    "BaseEvent should be set from repository when RSKIP535 is active");
        } else {
            // when RSKIP535 is not active, we should have a different header type
            Assertions.assertFalse(block.getHeader() instanceof BlockHeaderV2,
                    "Block should not have BlockHeaderV2 header when RSKIP535 is not active");

            BlockExecutor executor = buildBlockExecutor(trieStore, true, RSKIP_126_IS_ACTIVE);
            BlockResult result = executor.executeAndFill(block, parent.getHeader());

            // then
            Assertions.assertNotNull(result);
            Assertions.assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
            Assertions.assertNull(result.getBlock().getBaseEvent());
        }
    }

    @Test
    void executeBlockWithBaseEventFromRepository_WhenRepositoryIsNull() {
        // given
        // Create a block with BlockHeaderV2
        Block parent = blockchain.getBestBlock();
        Block block = new BlockGenerator(Constants.regtest(), activationConfig).createChildBlock(parent);

        // Create a real repository but don't set any baseEvent
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        repository.startTracking();
        repository.commit();
        parent.setStateRoot(repository.getRoot());

        BlockExecutor executor = buildBlockExecutor(trieStore, true, RSKIP_126_IS_ACTIVE);

        // when
        BlockResult result = executor.executeAndFill(block, parent.getHeader());

        // then
        Assertions.assertNotNull(result);
        Assertions.assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
        Assertions.assertArrayEquals(EMPTY_BYTE_ARRAY, result.getBlock().getBaseEvent(),
                "BaseEvent should be set to empty array when repository has no baseEvent");
    }

    public static class TestObjects {
        private final TrieStore trieStore;
        private final Block block;
        private final Block parent;
        private final Transaction transaction;
        private final Account account;
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
