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
import co.rsk.core.BlockchainDummy;
import co.rsk.core.Coin;
import co.rsk.db.RepositoryImpl;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieStoreImpl;
import com.google.common.collect.Lists;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.RLP;
import org.ethereum.vm.trace.ProgramTrace;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 29/07/2016.
 */
public class BlockExecutorTest {
    public static final byte[] EMPTY_TRIE_HASH = sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    private static final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void executeBlockWithoutTransaction() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock());

        Repository repository = new RepositoryImpl(config, new TrieStoreImpl(new HashMapDB()));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(10L));
        Assert.assertTrue(account.getEcKey().hasPrivKey());
        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = new BlockExecutor(config, repository, null, null, null);

        BlockResult result = executor.execute(block, repository.getRoot(), false);

        Assert.assertNotNull(result);

        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertTrue(result.getTransactionReceipts().isEmpty());
        Assert.assertArrayEquals(repository.getRoot(), result.getStateRoot());

        AccountState accountState = repository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.TEN, accountState.getBalance().asBigInteger());
    }

    @Test
    public void executeBlockWithOneTransaction() {
        SimpleEthereumListener listener = new SimpleEthereumListener();
        TestObjects objects = generateBlockWithOneTransaction();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, listener);
        Repository repository = objects.getRepository();
        Transaction tx = objects.getTransaction();
        Account account = objects.getAccount();

        BlockResult result = executor.execute(block, repository.getRoot(), false);

        Assert.assertNotNull(result);

        Assert.assertNotNull(listener.getLatestSummary());

        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(21000, result.getGasUsed());
        Assert.assertEquals(21000, result.getPaidFees().asBigInteger().intValueExact());

        Assert.assertNotNull(result.getReceiptsRoot());
        Assert.assertArrayEquals(BlockChainImpl.calcReceiptsTrie(result.getTransactionReceipts()), result.getReceiptsRoot());

        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getStateRoot()));

        Assert.assertNotNull(result.getLogsBloom());
        Assert.assertEquals(256, result.getLogsBloom().length);
        for (int k = 0; k < result.getLogsBloom().length; k++) {
            Assert.assertEquals(0, result.getLogsBloom()[k]);
        }

        AccountState accountState = repository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = repository.getSnapshotTo(result.getStateRoot());

        accountState = finalRepository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    @Test
    public void executeBlockWithTwoTransactions() {
        Repository repository = new RepositoryImpl(config, new TrieStoreImpl(new HashMapDB()));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(60000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = new BlockExecutor(config, repository, new BlockchainDummy(), null, null);

        Transaction tx1 = createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()));
        Transaction tx2 = createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()).add(BigInteger.ONE));
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block = blockGenerator.createChildBlock(blockGenerator.getGenesisBlock(), txs, uncles, 1, null);

        BlockResult result = executor.execute(block, repository.getRoot(), false);

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

        Assert.assertNotNull(result.getReceiptsRoot());
        Assert.assertArrayEquals(BlockChainImpl.calcReceiptsTrie(result.getTransactionReceipts()), result.getReceiptsRoot());
        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getStateRoot()));

        Assert.assertNotNull(result.getLogsBloom());
        Assert.assertEquals(256, result.getLogsBloom().length);
        for (int k = 0; k < result.getLogsBloom().length; k++)
            Assert.assertEquals(0, result.getLogsBloom()[k]);

        AccountState accountState = repository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(60000), accountState.getBalance().asBigInteger());

        Repository finalRepository = repository.getSnapshotTo(result.getStateRoot());

        accountState = finalRepository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(60000 - 42000 - 20), accountState.getBalance().asBigInteger());
    }

    @Test
    public void executeAndFillBlockWithOneTransaction() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, null);

        BlockResult result = executor.execute(block, parent.getStateRoot(), false);
        executor.executeAndFill(block, parent);

        Assert.assertArrayEquals(result.getReceiptsRoot(), block.getReceiptsRoot());
        Assert.assertArrayEquals(result.getStateRoot(), block.getStateRoot());
        Assert.assertEquals(result.getGasUsed(), block.getGasUsed());
        Assert.assertEquals(result.getPaidFees(), block.getFeesPaidToMiner());
        Assert.assertArrayEquals(result.getLogsBloom(), block.getLogBloom());

        Assert.assertEquals(3000000, new BigInteger(1, block.getGasLimit()).longValue());
    }

    @Test
    public void executeAndFillBlockWithTxToExcludeBecauseSenderHasNoBalance() {
        Repository repository = new RepositoryImpl(config, new TrieStoreImpl(new HashMapDB()));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.ZERO);

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = new BlockExecutor(config, repository, new BlockchainDummy(), null, null);

        Transaction tx = createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()));
        Transaction tx2 = createTransaction(account3, account2, BigInteger.TEN, repository.getNonce(account3.getAddress()));
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        txs.add(tx2);

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFill(block, genesis);

        // Check tx2 was excluded
        Assert.assertEquals(1, block.getTransactionsList().size());
        Assert.assertEquals(tx, block.getTransactionsList().get(0));
        Assert.assertArrayEquals(Block.getTxTrie(Lists.newArrayList(tx)).getHash(), block.getTxTrieRoot());
        
        Assert.assertEquals(3141592, new BigInteger(1, block.getGasLimit()).longValue());
    }

    @Test
    public void executeBlockWithTxThatMakesBlockInvalidSenderHasNoBalance() {
        Repository repository = new RepositoryImpl(config, new TrieStoreImpl(new HashMapDB()));

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        Account account3 = createAccount("acctest3", track, Coin.ZERO);

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = new BlockExecutor(config, repository, new BlockchainDummy(), null, null);

        Transaction tx = createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()));
        Transaction tx2 = createTransaction(account3, account2, BigInteger.TEN, repository.getNonce(account3.getAddress()));
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        txs.add(tx2);

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, txs, uncles, 1, null);

        BlockResult result = executor.execute(block, genesis.getStateRoot(), false);

        Assert.assertSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
    }

    @Test
    public void validateBlock() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, null);

        Assert.assertTrue(executor.executeAndValidate(block, parent));
    }

    @Test
    public void invalidBlockBadStateRoot() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, null);

        byte[] stateRoot = block.getStateRoot();
        stateRoot[0] = (byte)((stateRoot[0] + 1) % 256);

        Assert.assertFalse(executor.executeAndValidate(block, parent));
    }

    @Test
    public void invalidBlockBadReceiptsRoot() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, null);

        byte[] receiptsRoot = block.getReceiptsRoot();
        receiptsRoot[0] = (byte)((receiptsRoot[0] + 1) % 256);

        Assert.assertFalse(executor.executeAndValidate(block, parent));
    }

    @Test
    public void invalidBlockBadGasUsed() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, null);

        block.getHeader().setGasUsed(0);

        Assert.assertFalse(executor.executeAndValidate(block, parent));
    }

    @Test
    public void invalidBlockBadPaidFees() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, null);

        block.getHeader().setPaidFees(Coin.ZERO);

        Assert.assertFalse(executor.executeAndValidate(block, parent));
    }

    @Test
    public void invalidBlockBadLogsBloom() {
        TestObjects objects = generateBlockWithOneTransaction();
        Block parent = objects.getParent();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, null);

        byte[] logBloom = block.getLogBloom();
        logBloom[0] = (byte)((logBloom[0] + 1) % 256);

        Assert.assertFalse(executor.executeAndValidate(block, parent));
    }

    public static TestObjects generateBlockWithOneTransaction() {
        BlockChainImpl blockchain = new BlockChainBuilder().build();
        Repository repository = blockchain.getRepository();

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = new BlockExecutor(config, repository, new BlockchainDummy(), null, null);

        Transaction tx = createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()));
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        List<BlockHeader> uncles = new ArrayList<>();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockchain);
        genesis.setStateRoot(repository.getRoot());
        Block block = new BlockGenerator().createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFill(block, genesis);

        return new TestObjects(repository, block, genesis, tx, account);
    }

    private static Transaction createTransaction(Account sender, Account receiver, BigInteger value, BigInteger nonce) {
        String toAddress = Hex.toHexString(receiver.getAddress().getBytes());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        Transaction tx = Transaction.create(config, toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(21000));
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
        byte[] privateKeyBytes = HashUtil.sha3(seed.getBytes());
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
        executeBlockWithOneStrangeTransaction(true, false, generateBlockWithOneStrangeTransaction(2));
    }

    public void executeBlockWithOneStrangeTransaction(boolean mustFailValidation, boolean mustFailExecution, TestObjects objects) {
        SimpleEthereumListener listener = new SimpleEthereumListener();
        Block block = objects.getBlock();
        BlockExecutor executor = new BlockExecutor(config, objects.getRepository(), new BlockchainDummy(), null, listener);
        Repository repository = objects.getRepository();
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

        BlockResult result = executor.execute(block, repository.getRoot(), false);

        Assert.assertNotNull(result);
        if (mustFailExecution) {
            Assert.assertEquals(result, BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT);
            return;
        }
        Assert.assertNotNull(listener.getLatestSummary());

        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assert.assertArrayEquals(result.getStateRoot(), receipt.getPostTxState());

        Assert.assertEquals(21000, result.getGasUsed());
        Assert.assertEquals(21000, result.getPaidFees());

        Assert.assertNotNull(result.getReceiptsRoot());
        Assert.assertArrayEquals(BlockChainImpl.calcReceiptsTrie(result.getTransactionReceipts()), result.getReceiptsRoot());

        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getStateRoot()));

        Assert.assertNotNull(result.getLogsBloom());
        Assert.assertEquals(256, result.getLogsBloom().length);
        for (int k = 0; k < result.getLogsBloom().length; k++)
            Assert.assertEquals(0, result.getLogsBloom()[k]);

        AccountState accountState = repository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = repository.getSnapshotTo(result.getStateRoot());

        accountState = finalRepository.getAccountState(account.getAddress());

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    public static TestObjects generateBlockWithOneStrangeTransaction(int strangeTransactionType) {

        BlockChainImpl blockchain = new BlockChainBuilder().build();
        Repository repository = blockchain.getRepository();

        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        BlockExecutor executor = new BlockExecutor(config, repository, new BlockchainDummy(), null, null);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = createStrangeTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()), strangeTransactionType);
        txs.add(tx);

        List<BlockHeader> uncles = new ArrayList<>();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockchain);
        genesis.setStateRoot(repository.getRoot());
        Block block = new BlockGenerator().createChildBlock(genesis, txs, uncles, 1, null);

        executor.executeAndFillReal(block, genesis); // Forces all transactions included

        return new TestObjects(repository, block, genesis, tx, account);
    }

    private static Transaction createStrangeTransaction(Account sender, Account receiver,
                                                        BigInteger value, BigInteger nonce, int strangeTransactionType) {
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        byte[] to = receiver.getAddress().getBytes();
        byte[] gasLimitData = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21000));
        byte[] valueData = BigIntegers.asUnsignedByteArray(value);

        if (strangeTransactionType==0) {
            to = new byte[1]; // one zero
            to[0] = 127;
        } else if (strangeTransactionType==1) {
            to = new byte[1024];
            java.util.Arrays.fill(to, (byte) -1); // fill with 0xff
        } else {
            // Bad encoding for value
            byte[] newValueData = new byte[1024];
            System.arraycopy(valueData,0,newValueData ,1024- valueData.length,valueData.length);
            valueData = newValueData;
        }

        Transaction tx = new Transaction(
                BigIntegers.asUnsignedByteArray(nonce),
                BigIntegers.asUnsignedByteArray(BigInteger.ONE), //gasPrice
                gasLimitData, // gasLimit
                to,
                valueData,
                null); // no data
        tx.sign(privateKeyBytes);
        return tx;
    }

    private static byte[] sha3(byte[] input) {
        Keccak256 digest =  new Keccak256();
        digest.update(input);
        return digest.digest();
    }

    public static class TestObjects {
        private Repository repository;
        private Block block;
        private Block parent;
        private Transaction transaction;
        private Account account;

        public TestObjects(Repository repository, Block block, Block parent, Transaction transaction, Account account) {
            this.repository = repository;
            this.block = block;
            this.parent = parent;
            this.transaction = transaction;
            this.account = account;
        }

        public Repository getRepository() {
            return this.repository;
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

    public static class SimpleEthereumListener implements EthereumListener {
        private Block latestBlock;
        private List<TransactionReceipt> latestReceipts;
        private String latestTransactionHash;
        private String latestTrace;
        private TransactionExecutionSummary latestSummary;

        public String getLatestTransactionHash() {
            return latestTransactionHash;
        }

        public String getLatestTrace() {
            return latestTrace;
        }

        public TransactionExecutionSummary getLatestSummary() {
            return latestSummary;
        }

        @Override
        public void trace(String output) {
            latestTrace = output;
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
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            latestBlock = block;
            latestReceipts = receipts;
        }

        public Block getLatestBlock() { return latestBlock; }

        public List<TransactionReceipt> getLatestReceipts() { return latestReceipts; }

        @Override
        public void onPeerDisconnect(String host, long port) {

        }

        @Override
        public void onPendingTransactionsReceived(List<Transaction> transactions) {

        }

        @Override
        public void onPendingStateChanged(PendingState pendingState) {

        }

        @Override
        public void onSyncDone() {

        }

        @Override
        public void onNoConnections() {

        }

        @Override
        public void onVMTraceCreated(String transactionHash, ProgramTrace trace) {
            latestTransactionHash = transactionHash;
            latestTrace = trace.toString();
        }

        @Override
        public void onTransactionExecuted(TransactionExecutionSummary summary) {
            latestSummary = summary;
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
