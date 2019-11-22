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
import org.bouncycastle.util.encoders.Hex;
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

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 29/07/2016.
 */
public class BlockExecutorTest {
    public static final byte[] EMPTY_TRIE_HASH = sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    private static final TestSystemProperties config = new TestSystemProperties();
    private static final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private static final long GAS_PER_TRANSACTION = 21000;

    private Blockchain blockchain;
    private BlockExecutor executor;
    private TrieStore trieStore;
    private RepositorySnapshot repository;

    @Before
    public void setUp() {
        RskTestFactory objects = new RskTestFactory(config);
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
        Block block = getBlockWithOneTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();

        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(GAS_PER_TRANSACTION, result.getGasUsed());
        Assert.assertEquals(GAS_PER_TRANSACTION, result.getPaidFees().asBigInteger().intValueExact());

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
        Assert.assertEquals(BigInteger.valueOf(30000 - GAS_PER_TRANSACTION - 10), accountState.getBalance().asBigInteger());
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
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(GAS_PER_TRANSACTION, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        receipt = result.getTransactionReceipts().get(1);
        Assert.assertEquals(tx2, receipt.getTransaction());
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(2*GAS_PER_TRANSACTION, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(2*GAS_PER_TRANSACTION, result.getGasUsed());
        Assert.assertEquals(2*GAS_PER_TRANSACTION, result.getPaidFees().asBigInteger().intValueExact());

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
    public void executeBlockWithTwoTransactionsInParallel() {
        Block block = getBlockWithTwoTransactionsDifferentsAccounts(); // this changes the best block
        Block parent = blockchain.getBestBlock();
        // split the 2 transactions is 2 different partitions (ie 2 concurrent threads)
        block.setPartitionEnds(new int[]{0});

        Transaction tx1 = block.getTransactionsList().get(0);
        Transaction tx2 = block.getTransactionsList().get(1);
        RskAddress account = tx1.getSender();
        RskAddress account2 = tx1.getReceiveAddress();
        RskAddress account3 = tx2.getSender();
        RskAddress account4 = tx2.getReceiveAddress();

        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);

        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(2, result.getTransactionReceipts().size());

        TransactionReceipt receipt1 = null;
        TransactionReceipt receipt2 = null;
        for (TransactionReceipt transactionReceipt : result.getTransactionReceipts()) {
            if (transactionReceipt.getTransaction().getHash().equals(tx1.getHash())) {
                receipt1 = transactionReceipt;
            } else if (transactionReceipt.getTransaction().getHash().equals(tx2.getHash())) {
                receipt2 = transactionReceipt;
            }
        }
        Assert.assertNotNull(receipt1);
        Assert.assertNotNull(receipt2);
        Assert.assertEquals(tx1, receipt1.getTransaction());
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt1.getGasUsed()).longValue());
        // Assert.assertEquals(GAS_PER_TRANSACTION, BigIntegers.fromUnsignedByteArray(receipt1.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt1.hasTxStatus() && receipt1.isTxStatusOK() && receipt1.isSuccessful());

        Assert.assertEquals(tx2, receipt2.getTransaction());
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt2.getGasUsed()).longValue());
        // Assert.assertEquals(2*GAS_PER_TRANSACTION, BigIntegers.fromUnsignedByteArray(receipt2.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt2.hasTxStatus() && receipt2.isTxStatusOK() && receipt2.isSuccessful());

        Assert.assertEquals(2*GAS_PER_TRANSACTION, result.getGasUsed());
        Assert.assertEquals(2*GAS_PER_TRANSACTION, result.getPaidFees().asBigInteger().intValueExact());

        //here is the problem: in the prior code repository root would never be overwritten by childs
        //while the new code does overwrite the root.
        //Which semantic is correct ? I don't know

        Assert.assertFalse(Arrays.equals(parent.getStateRoot(), result.getFinalState().getHash().getBytes()));

        byte[] calculatedLogsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());
        Assert.assertEquals(256, calculatedLogsBloom.length);
        Assert.assertArrayEquals(new byte[256], calculatedLogsBloom);

        AccountState accountState = repository.getAccountState(account);
        AccountState account2State = repository.getAccountState(account2);
        AccountState account3State = repository.getAccountState(account3);
        AccountState account4State = repository.getAccountState(account4);

        Assert.assertNotNull(accountState);
        Assert.assertNotNull(account3State);
        Assert.assertEquals(BigInteger.valueOf(60000), accountState.getBalance().asBigInteger());
        Assert.assertEquals(BigInteger.valueOf(60000), account3State.getBalance().asBigInteger());

        // here is the papa. my commit changes stateroot while previous commit did not.

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        accountState = finalRepository.getAccountState(account);
        account2State = finalRepository.getAccountState(account2);
        account3State = finalRepository.getAccountState(account3);
        account4State = finalRepository.getAccountState(account4);

        Assert.assertNotNull(accountState);
        Assert.assertNotNull(account2State);
        Assert.assertNotNull(account3State);
        Assert.assertNotNull(account4State);
        Assert.assertEquals(BigInteger.valueOf(60000 - GAS_PER_TRANSACTION - 10), accountState.getBalance().asBigInteger());
        Assert.assertEquals(BigInteger.valueOf(20L), account2State.getBalance().asBigInteger());
        Assert.assertEquals(BigInteger.valueOf(60000 - GAS_PER_TRANSACTION - 10), account3State.getBalance().asBigInteger());
        Assert.assertEquals(BigInteger.valueOf(20L), account4State.getBalance().asBigInteger());
    }


    @Test
    public void executeBlockWithTwoConflictingTransactionsInParallel() {
        Block block = getBlockWithTwoTransactions(); // this changes the best block
        Block parent = blockchain.getBestBlock();
        // split the 2 transactions is 2 different partitions (ie 2 concurrent threads)
        block.setPartitionEnds(new int[]{0});
        // seal the block otherwise the prerun will detect conflict and serialize the transactions
        block.seal();

        Transaction tx1 = block.getTransactionsList().get(0);
        Transaction tx2 = block.getTransactionsList().get(1);
        RskAddress account = tx1.getSender();
        RskAddress account2 = tx1.getReceiveAddress();

        BlockResult result = executor.execute(block, parent.getHeader(), false);

        // conflict between transaction running in different thread shall be detected and lead to an invalid block
        Assert.assertSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
        // World state shall not be impacted
        AccountState accountState = repository.getAccountState(account);
        AccountState account2State = repository.getAccountState(account2);

        Assert.assertNotNull(accountState);
        Assert.assertNotNull(account2State);
        Assert.assertEquals(BigInteger.valueOf(60000), accountState.getBalance().asBigInteger());
        Assert.assertEquals(BigInteger.valueOf(10L), account2State.getBalance().asBigInteger());

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

        Transaction tx = createTransaction(
                account,
                account2,
                BigInteger.TEN,
                repository.getNonce(account.getAddress())
        );
        Transaction tx2 = createTransaction(
                account3,
                account2,
                BigInteger.TEN,
                repository.getNonce(account3.getAddress())
        );
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

        Transaction tx = createTransaction(
                account,
                account2,
                BigInteger.TEN,
                repository.getNonce(account.getAddress())
        );
        Transaction tx2 = createTransaction(
                account3,
                account2,
                BigInteger.TEN,
                repository.getNonce(account3.getAddress())
        );
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

        Transaction tx = createTransaction(
                account,
                account2,
                BigInteger.TEN,
                repository.getNonce(account.getAddress())
        );
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
        List<Transaction> txs = Collections.singletonList(
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()))
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
        List<Transaction> txs = Arrays.asList(
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress())),
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()).add(BigInteger.ONE))
        );

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private Block getBlockWithTwoTransactionsDifferentsAccounts() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(60000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.valueOf(60000));
        Account account4 = createAccount("acctest4", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        List<Transaction> txs = Arrays.asList(
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress())),
                createTransaction(account3, account4, BigInteger.TEN, repository.getNonce(account3.getAddress()))
        );

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private static Transaction createTransaction(Account sender, Account receiver, BigInteger value, BigInteger nonce) {
        String toAddress = Hex.toHexString(receiver.getAddress().getBytes());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        Transaction tx = new Transaction(toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(GAS_PER_TRANSACTION), config.getNetworkConstants().getChainId());
        tx.sign(privateKeyBytes);
        return tx;
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
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(GAS_PER_TRANSACTION, new BigInteger(1, receipt.getCumulativeGas()).longValue());

        Assert.assertEquals(GAS_PER_TRANSACTION, result.getGasUsed());
        Assert.assertEquals(Coin.valueOf(GAS_PER_TRANSACTION), result.getPaidFees());

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
        Assert.assertEquals(BigInteger.valueOf(30000 - GAS_PER_TRANSACTION - 10), accountState.getBalance().asBigInteger());
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
                config.getActivationConfig().isActive(ConsensusRule.RSKIP126, blockNumber)
        );
    }

    private static Transaction createStrangeTransaction(
            Account sender, Account receiver,
            BigInteger value, BigInteger nonce, int strangeTransactionType) {
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        byte[] to = receiver.getAddress().getBytes();
        byte[] gasLimitData = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(GAS_PER_TRANSACTION));
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

        Transaction tx = new Transaction(
                BigIntegers.asUnsignedByteArray(nonce),
                BigIntegers.asUnsignedByteArray(BigInteger.ONE), //gasPrice
                gasLimitData, // gasLimit
                to,
                valueData,
                null
        ); // no data
        tx.sign(privateKeyBytes);
        return tx;
    }

    private static byte[] sha3(byte[] input) {
        Keccak256 digest = new Keccak256();
        digest.update(input);
        return digest.digest();
    }

    private static BlockExecutor buildBlockExecutor(TrieStore store) {
        StateRootHandler stateRootHandler = new StateRootHandler(
                config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>());

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                config.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig());

        return new BlockExecutor(
                config,
                new RepositoryLocator(store, stateRootHandler),
                stateRootHandler,
                new TransactionExecutorFactory(
                        config,
                        null,
                        null,
                        blockFactory,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(config, bridgeSupportFactory)
                )
        );
    }

    @Test
    public void reorderTransactionAndReceiptLists() {
// first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        long balance = 60000;
        Account account1 = createAccount("acctest1", track, Coin.valueOf(balance));
        balance += 10L;
        Account account2 = createAccount("acctest2", track, Coin.valueOf(balance));
        balance += 10L;
        Account account3 = createAccount("acctest3", track, Coin.valueOf(balance));
        balance += 10L;
        Account account4 = createAccount("acctest4", track, Coin.valueOf(balance));
        balance += 10L;
        Account account5 = createAccount("acctest5", track, Coin.valueOf(balance));
        balance += 10L;
        Account account6 = createAccount("acctest6", track, Coin.valueOf(balance));
        balance += 10L;
        Account account7 = createAccount("acctest7", track, Coin.valueOf(balance));
        balance += 10L;
        Account account8 = createAccount("acctest8", track, Coin.valueOf(balance));
        balance += 10L;
        Account account9 = createAccount("acctest9", track, Coin.valueOf(balance));

        track.commit();

        Transaction tx1 = createTransaction(account1, account2, BigInteger.TEN, repository.getNonce(account1.getAddress()));
        TransactionReceipt r1 = createReceipt(tx1);
        Transaction tx2 = createTransaction(account3, account2, BigInteger.TEN, repository.getNonce(account3.getAddress()));
        TransactionReceipt r2 = createReceipt(tx2);
        Transaction tx3 = createTransaction(account4, account5, BigInteger.TEN, repository.getNonce(account4.getAddress()));
        TransactionReceipt r3 = createReceipt(tx3);
        Transaction tx4 = createTransaction(account1, account6, BigInteger.TEN, repository.getNonce(account1.getAddress()).add(BigInteger.ONE));
        TransactionReceipt r4 = createReceipt(tx4);
        Transaction tx5 = createTransaction(account7, account8, BigInteger.TEN, repository.getNonce(account7.getAddress()));
        TransactionReceipt r5 = createReceipt(tx5);
        Transaction tx6 = createTransaction(account9, account5, BigInteger.TEN, repository.getNonce(account9.getAddress()));
        TransactionReceipt r6 = createReceipt(tx6);

        Transaction[] transactions = new Transaction[]{tx1, tx2, tx3, tx4, tx5, tx6};
        List<Transaction> lstTransactions = new ArrayList<>(Arrays.asList(transactions));
        TransactionReceipt[] receipts = new TransactionReceipt[]{r1, r2, r3, r4, r5, r6};
        List<TransactionReceipt> lstReceipts = new ArrayList<>(Arrays.asList(receipts));

        Transaction[] refTransactions = new Transaction[]{tx1, tx2, tx4, tx3, tx6, tx5};
        TransactionReceipt[] expectedReceipts = new TransactionReceipt[]{r1, r2, r4, r3, r6, r5};

        List<Transaction> referenceList = Arrays.asList(refTransactions);

        BlockExecutor.reorderTransactionList(lstTransactions, referenceList);
        Assert.assertArrayEquals(refTransactions, lstTransactions.toArray());

        BlockExecutor.reorderTransactionReceiptList(lstReceipts, referenceList);
        Assert.assertArrayEquals(expectedReceipts, lstReceipts.toArray());
    }

    @Test
    public void moreThan16TransationsInParallel() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        // We define 20 transactions, all independent from each other
        // We expect the partitioning results in 4 partitions of 2 Txs each + 12 partitions of 1 Txs each
        // Hence expected partitionEnds = [1,3,5,7,8,9,10,11,12,13,14,15,16,17,18,19]
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Account sender = createAccount(UUID.randomUUID().toString(), track, Coin.valueOf(60000 + i*20L));
            Account receiver = createAccount(UUID.randomUUID().toString(), track, Coin.valueOf(0L));
            Transaction tx = createTransaction(sender, receiver, BigInteger.TEN, repository.getNonce(sender.getAddress()));
            txs.add(tx);
        }

        track.commit();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());

        List<BlockHeader> uncles = new ArrayList<>();
        Block block = blockGenerator.createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFill(block, genesis.getHeader());

        int[] partitionEnds = block.getPartitionEnds();
        Assert.assertEquals(16, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {1,3,5,7,8,9,10,11,12,13,14,15,16,17,18,19},
                partitionEnds
        );

    }

    @Test
    public void partitioningWithInvalidTransaction() {
        /**
         * Block 1 : we have 3 Txs :
         * - tx1 transfers from key ALICE to key BOB
         * - tx2 transfers from key CAROL to key DENISE
         * - tx3 transfers from key DENISE to key ALICE (creating a conflict with tx1 and tx2)
         * We expect the block to define only one partition with the 3 txs
         *
         * Block 2 : we have the same 3 Txs, but tx3 is going to fail because sender is OOG
         * Hence, we expect the conflict does not happen this time,
         * then the block to define 2 separate partitions for tx1 and tx2.
         */
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        // We define 16 transactions, all inter-dependent (same keys)
        // We expect the partitioning results in only 1 partition containing the 16 txs
        // Hence expected partitionEnds = [15]
        List<Transaction> txs_block1 = new ArrayList<>();
        List<Transaction> txs_block2 = new ArrayList<>();

        Account accountAlice = createAccount("ALICE", track, Coin.valueOf(2*GAS_PER_TRANSACTION + 10L));
        Account accountBob = createAccount("BOB", track, Coin.valueOf(0L));
        Account accountCarol = createAccount("CAROL", track, Coin.valueOf(2*GAS_PER_TRANSACTION + 20L));
        Account accountDenise = createAccount("DENISE", track, Coin.valueOf(GAS_PER_TRANSACTION));

        track.commit();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());
        List<BlockHeader> uncles = new ArrayList<>();

        txs_block1.add(createTransaction(accountAlice, accountBob, BigInteger.TEN, BigInteger.valueOf(0)));
        txs_block1.add(createTransaction(accountCarol, accountDenise, BigInteger.TEN, BigInteger.valueOf(0)));
        txs_block1.add(createTransaction(accountDenise, accountAlice, BigInteger.TEN, BigInteger.valueOf(0)));

        Block firstBlock = blockGenerator.createChildBlock(bestBlock, txs_block1, uncles, 1, null);

        BlockResult result = executor.executeAndFill(firstBlock, bestBlock.getHeader());

        List<Transaction> executedTxs = result.getExecutedTransactions();
        // Check that all transactions execution have succeed
        Assert.assertEquals(txs_block1.size(), executedTxs.size());

        int[] partitionEnds = firstBlock.getPartitionEnds();
        Assert.assertEquals(1, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {2},
                partitionEnds
        );

        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());
        int balanceAlice = finalRepository.getBalance(accountAlice.getAddress()).asBigInteger().intValue();
        int balanceBob = finalRepository.getBalance(accountBob.getAddress()).asBigInteger().intValue();
        int balanceCarol  =finalRepository.getBalance(accountCarol.getAddress()).asBigInteger().intValue();
        int balanceDenise = finalRepository.getBalance(accountDenise.getAddress()).asBigInteger().intValue();

        Assert.assertEquals(Coin.valueOf(GAS_PER_TRANSACTION + 10L), finalRepository.getBalance(accountAlice.getAddress()));
        Assert.assertEquals(Coin.valueOf(10L), finalRepository.getBalance(accountBob.getAddress()));
        Assert.assertEquals(Coin.valueOf(GAS_PER_TRANSACTION + 10L), finalRepository.getBalance(accountCarol.getAddress()));
        Assert.assertEquals(Coin.valueOf(0L), finalRepository.getBalance(accountDenise.getAddress()));

        txs_block2.add(createTransaction(accountAlice, accountBob, BigInteger.TEN, BigInteger.valueOf(1)));
        txs_block2.add(createTransaction(accountCarol, accountDenise, BigInteger.TEN, BigInteger.valueOf(1)));
        txs_block2.add(createTransaction(accountDenise, accountAlice, BigInteger.TEN, BigInteger.valueOf(1)));

        Block secondBlock = blockGenerator.createChildBlock(firstBlock, txs_block2, uncles, 1, null);

        result = executor.executeAndFill(secondBlock, firstBlock.getHeader());

        executedTxs = result.getExecutedTransactions();
        // Check that one transaction has been discraded
        Assert.assertEquals(txs_block2.size() - 1, executedTxs.size());

        partitionEnds = secondBlock.getPartitionEnds();
        Assert.assertEquals(2, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {0,1},
                partitionEnds
        );

    }

    @Test
    public void conflictingTransactions() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        // We define 16 transactions, all inter-dependent (same keys)
        // We expect the partitioning results in only 1 partition containing the 16 txs
        // Hence expected partitionEnds = [15]
        List<Transaction> txs_block1 = new ArrayList<>();
        List<Transaction> txs_block2 = new ArrayList<>();

        // First we create 2 groups of interdependent transactions but keeping the groups independent to each other
        // We expect the partitioning results in 2 partition, one with 8 txs, the other with 7 txs
        Account tx0sender = createAccount(UUID.randomUUID().toString(), track, Coin.valueOf(600000));
        Account tx1receiver = createAccount(UUID.randomUUID().toString(), track, Coin.valueOf(0L));
        Account tx13sender = null;
        Account tx14receiver = null;
        for (int i = 0; i < 15; i++) {
            Account sender, receiver;
            BigInteger nonce_block1 = BigInteger.valueOf(0);
            BigInteger nonce_block2 = BigInteger.valueOf(1);
            if ( i % 2 == 0 ) {
                // tx0, tx2, tx4 ..., until tx14 have the same sender
                sender = tx0sender;
                // increment nonce
                nonce_block1 = BigInteger.valueOf(i/2);
                nonce_block2 = BigInteger.valueOf(8 + i/2);
                receiver = createAccount(UUID.randomUUID().toString(), track, Coin.valueOf(0L));
                if (i == 14) {
                    tx14receiver = receiver;
                }
            } else {
                // tx1, tx3, tx5 ..., until tx13 have the same receiver
                sender = createAccount(UUID.randomUUID().toString(), track, Coin.valueOf(120000 + i*20L));
                receiver = tx1receiver;
                if (i == 13) {
                    tx13sender = sender;
                }
            }
            Transaction tx_block1 = createTransaction(sender, receiver, BigInteger.TEN, nonce_block1);
            txs_block1.add(tx_block1);
            Transaction tx_block2 = createTransaction(sender, receiver, BigInteger.TEN, nonce_block2);
            txs_block2.add(tx_block2);
        }

        track.commit();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());

        List<BlockHeader> uncles = new ArrayList<>();
        Block firstBlock = blockGenerator.createChildBlock(genesis, txs_block1, uncles, 1, null);

        BlockResult result = executor.executeAndFill(firstBlock, genesis.getHeader());

        List<Transaction> executedTxs = result.getExecutedTransactions();
        // Check that all transactions execution have succeed
        Assert.assertEquals(txs_block1.size(), executedTxs.size());

        int[] partitionEnds = firstBlock.getPartitionEnds();
        Assert.assertEquals(2, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {7,14},
                partitionEnds
        );

        // Now, we add tx15 that create conflict with both tx13 (same sender) and tx14 (same receiver)
        // Hence, all transactions become inter-dependent
        txs_block2.add(createTransaction(tx13sender, tx14receiver, BigInteger.TEN, BigInteger.valueOf(2)));

        Block secondBlock = blockGenerator.createChildBlock(firstBlock, txs_block2, uncles, 1, null);

        result = executor.executeAndFill(secondBlock, firstBlock.getHeader());

        executedTxs = result.getExecutedTransactions();
        // Check that all transactions executions have succeed
        Assert.assertEquals(txs_block2.size(), executedTxs.size());

        partitionEnds = secondBlock.getPartitionEnds();
        Assert.assertEquals(1, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {15},
                partitionEnds
        );

    }


    private static TransactionReceipt createReceipt(Transaction tx) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransaction(tx);
        return receipt;
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
