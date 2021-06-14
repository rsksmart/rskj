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
import co.rsk.db.MutableTrieImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.db.StateRootHandler;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.BigIntegers;
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
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP126;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Created by ajlopez on 29/07/2016.
 */
public class BlockExecutorTest {
    private static final byte[] EMPTY_TRIE_HASH = sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    private static final TestSystemProperties CONFIG = new TestSystemProperties();
    private static final BlockFactory BLOCK_FACTORY = new BlockFactory(CONFIG.getActivationConfig());

    private Blockchain blockchain;
    private BlockExecutor executor;
    private TrieStore trieStore;
    private RepositorySnapshot repository;

    @Before
    public void setUp() {
        RskTestFactory objects = new RskTestFactory(CONFIG);
        blockchain = objects.getBlockchain();
        executor = objects.getBlockExecutor();
        trieStore = objects.getTrieStore();
        repository = objects.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());
    }

    @Test
    public void executeBlockWithoutTransaction() {
        Block parent = blockchain.getBestBlock();
        Block block = new BlockGenerator().createChildBlock(parent);

        BlockResult result = executor.execute(block, parent.getHeader(), false);

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

        BlockResult result = executor.execute(block, parent.getHeader(), false);

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

        BlockResult result = executor.execute(block, parent.getHeader(), false);

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

        BlockResult result = executor.execute(block, parent.getHeader(), false);

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
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore());

        BlockResult result = executor.execute(block, parent.getHeader(), false);
        executor.executeAndFill(block, parent.getHeader());

        byte[] calculatedReceiptsRoot = BlockHashesHelper.calculateReceiptsTrieRoot(result.getTransactionReceipts(), true);
        Assert.assertArrayEquals(calculatedReceiptsRoot, block.getReceiptsRoot());
        Assert.assertArrayEquals(result.getFinalState().getHash().getBytes(), block.getStateRoot());
        Assert.assertEquals(result.getGasUsed(), block.getGasUsed());
        Assert.assertEquals(result.getPaidFees(), block.getFeesPaidToMiner());
        Assert.assertArrayEquals(BlockExecutor.calculateLogsBloom(result.getTransactionReceipts()), block.getLogBloom());

        Assert.assertEquals(3000000, new BigInteger(1, block.getGasLimit()).longValue());
    }

    @Test
    public void executeAndFillBlockWithTxToExcludeBecauseSenderHasNoBalance() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.ZERO);

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore);

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

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFill(block, genesis.getHeader());

        // Check tx2 was excluded
        Assert.assertEquals(1, block.getTransactionsList().size());
        Assert.assertEquals(tx, block.getTransactionsList().get(0));
        Assert.assertArrayEquals(
                calculateTxTrieRoot(Collections.singletonList(tx), block.getNumber()),
                block.getTxTrieRoot()
        );

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

        BlockExecutor executor = buildBlockExecutor(trieStore);

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

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, txs, uncles, 1, null);

        BlockResult result = executor.execute(block, genesis.getHeader(), false);

        Assert.assertSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
    }

    @Test
    public void validateStateRootWithRskip126DisabledAndValidStateRoot() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(trieStore);

        Block block = new BlockGenerator().getBlock(1);
        block.setStateRoot(trie.getHash().getBytes());

        BlockResult blockResult = new BlockResult(block, Collections.emptyList(), Collections.emptyList(), 0,
                Coin.ZERO, trie);

        RskSystemProperties cfg = spy(CONFIG);

        ActivationConfig activationConfig = spy(cfg.getActivationConfig());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP126), anyLong());
        doReturn(activationConfig).when(cfg).getActivationConfig();

        BlockExecutor executor = buildBlockExecutor(trieStore, cfg);

        Assert.assertTrue(executor.validateStateRoot(block.getHeader(), blockResult));
    }

    @Test
    public void validateStateRootWithRskip126DisabledAndInvalidStateRoot() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(trieStore);

        Block block = new BlockGenerator().getBlock(1);
        block.setStateRoot(new byte[] { 1, 2, 3, 4 });

        BlockResult blockResult = new BlockResult(block, Collections.emptyList(), Collections.emptyList(), 0,
                Coin.ZERO, trie);

        RskSystemProperties cfg = spy(CONFIG);

        ActivationConfig activationConfig = spy(cfg.getActivationConfig());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP126), anyLong());
        doReturn(activationConfig).when(cfg).getActivationConfig();

        BlockExecutor executor = buildBlockExecutor(trieStore, cfg);

        Assert.assertTrue(executor.validateStateRoot(block.getHeader(), blockResult));
    }

    @Test
    public void validateBlock() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore());

        Assert.assertTrue(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadStateRoot() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore());

        byte[] stateRoot = block.getStateRoot();
        stateRoot[0] = (byte) ((stateRoot[0] + 1) % 256);

        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadReceiptsRoot() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore());

        byte[] receiptsRoot = block.getReceiptsRoot();
        receiptsRoot[0] = (byte) ((receiptsRoot[0] + 1) % 256);

        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadGasUsed() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore());

        block.getHeader().setGasUsed(0);

        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadPaidFees() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore());

        block.getHeader().setPaidFees(Coin.ZERO);

        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    @Test
    public void invalidBlockBadLogsBloom() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = buildBlockExecutor(objects.getTrieStore());

        byte[] logBloom = block.getLogBloom();
        logBloom[0] = (byte) ((logBloom[0] + 1) % 256);

        Assert.assertFalse(executor.executeAndValidate(block, parent.getHeader()));
    }

    private static TestObjects generateBlockWithOneTransaction() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = buildBlockExecutor(trieStore);

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

        Block block = new BlockGenerator().createChildBlock(genesis, txs, uncles, 1, null);

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
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
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
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
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
        BlockExecutor executor = buildBlockExecutor(trieStore);
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

        BlockResult result = executor.execute(block, parent.getHeader(), false);

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

        BlockExecutor executor = buildBlockExecutor(trieStore);

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
        Block block = new BlockGenerator().createChildBlock(genesis, txs, uncles, 1, null);

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

    private static BlockExecutor buildBlockExecutor(TrieStore store) {
        return buildBlockExecutor(store, CONFIG);
    }

    private static BlockExecutor buildBlockExecutor(TrieStore store, RskSystemProperties config) {
        StateRootHandler stateRootHandler = new StateRootHandler(
                config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>());

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                config.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig());

        return new BlockExecutor(
                config.getActivationConfig(),
                new RepositoryLocator(store, stateRootHandler),
                stateRootHandler,
                new TransactionExecutorFactory(
                        config,
                        null,
                        null,
                        BLOCK_FACTORY,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(config, bridgeSupportFactory),
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
