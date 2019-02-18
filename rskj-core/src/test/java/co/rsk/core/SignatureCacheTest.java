package co.rsk.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.SignatureCache;
import co.rsk.core.bc.*;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.util.RskTestFactory;

import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.TransactionFactoryHelper.createAccount;
import static org.ethereum.util.TransactionFactoryHelper.createSampleTransaction;

public class SignatureCacheTest {

    private static final TestSystemProperties config = new TestSystemProperties();
    public static final int MAX_TXS_BLOCK = 300;


    private TransactionPoolImpl transactionPool;
    private Blockchain blockChain;
    private SignatureCache signatureCache;
    private BlockBuilder builder;
    private Block genesis;
    private AccountBuilder accBuilder;
    private TransactionBuilder txBuilder;

    @Before
    public void setUp() {
        RskTestFactory factory = new RskTestFactory();
        blockChain = factory.getBlockchain();
        genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        signatureCache = factory.getSignatureCache();
        transactionPool = new TransactionPoolImpl(config, factory.getRepository(), null, null, signatureCache, new ProgramInvokeFactoryImpl(), new TestCompositeEthereumListener(), 10, 100);
        transactionPool.processBest(blockChain.getBestBlock());
        builder = new BlockBuilder(blockChain);
        accBuilder = new AccountBuilder(blockChain);
        txBuilder = new TransactionBuilder();
    }



    @Test
    public void newTxIsAddedInCache() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Account sender = createAccount(1);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        transactionPool.addTransaction(tx);
        RskAddress addrCachedBroadcasted = signatureCache.getSenderCacheInBroadcastTx(tx.getRawHash().getBytes(), tx.getSignature());
        Assert.assertEquals(addrCachedBroadcasted, sender.getAddress());
    }

    @Test
    public void sameTxIsSentTwice() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 1000, 0);
        transactionPool.addTransaction(tx);
        Assert.assertTrue(signatureCache.isInBroadcastTxCache(tx2.getRawHash().getBytes(), tx2.getSignature()));
    }

    @Test
    public void processABlockWithCachedTx() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        transactionPool.addTransaction(tx);

        Assert.assertTrue(signatureCache.isInBroadcastTxCache(tx.getRawHash().getBytes(), tx.getSignature()));

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block = builder.parent(genesis).transactions(txs).signatureCache(signatureCache).build();

        Assert.assertFalse(signatureCache.isInBroadcastTxCache(tx.getRawHash().getBytes(), tx.getSignature()));
        Assert.assertTrue(signatureCache.isInBlockTxCache(tx.getRawHash().getBytes(), tx.getSignature()));

        transactionPool.acceptBlock(block);

    }

    @Test
    public void processABlockWithNoCachedTx() {
        Coin balance = Coin.valueOf(1000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        builder.parent(genesis).transactions(txs).signatureCache(signatureCache).build();

        Assert.assertFalse(signatureCache.isInBroadcastTxCache(tx.getRawHash().getBytes(), tx.getSignature()));
        Assert.assertTrue(signatureCache.isInBlockTxCache(tx.getRawHash().getBytes(), tx.getSignature()));


    }

    @Test

    public void invalidTxIsNotCached() {
        Coin balance = Coin.valueOf(1000000);
        BigInteger r = new BigInteger("0", 16);
        BigInteger s = new BigInteger("0", 16);
        ECKey.ECDSASignature sig = ECKey.ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), (byte) 0x00);

        Account acc1 = accBuilder.name("1").balance(balance).build();
        Account acc2 = accBuilder.name("2").balance(balance).build();

        Transaction tx = txBuilder.sender(acc1).receiver(acc2).build();
        tx.setSignature(sig);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        builder.parent(genesis).transactions(txs).signatureCache(signatureCache).build();
        Assert.assertFalse(signatureCache.isInBroadcastTxCache(tx.getRawHash().getBytes(), tx.getSignature()));
        Assert.assertFalse(signatureCache.isInBlockTxCache(tx.getRawHash().getBytes(), tx.getSignature()));
    }



    @Test
    public void threeFullBlocksDeleteTheCachedTx() {
        Coin balance = Coin.valueOf(1000000000);
        createTestAccounts(2, balance);
        Transaction tx = createSampleTransaction(1, 2, 1, 0);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        builder.parent(genesis).transactions(txs).signatureCache(signatureCache).build();

        Assert.assertFalse(signatureCache.isInBroadcastTxCache(tx.getRawHash().getBytes(), tx.getSignature()));
        Assert.assertTrue(signatureCache.isInBlockTxCache(tx.getRawHash().getBytes(), tx.getSignature()));


        for (int j = 0; j < 3; j++){
            txs = generateTransactions(j* MAX_TXS_BLOCK);
            builder.transactions(txs).signatureCache(signatureCache).build();
        }

        Assert.assertFalse(signatureCache.isInBroadcastTxCache(tx.getRawHash().getBytes(), tx.getSignature()));
        Assert.assertFalse(signatureCache.isInBlockTxCache(tx.getRawHash().getBytes(), tx.getSignature()));


    }

    private List<Transaction> generateTransactions(Integer nonceInitial) {
        List<Transaction> txs = new ArrayList<>();
        for (int i = nonceInitial; i < nonceInitial+MAX_TXS_BLOCK; i++) {
            txs.add(createSampleTransaction(1, 2, 1, i+1));
        }

        return txs;
    }

    private void createTestAccounts(int naccounts, Coin balance) {
        Repository repository = blockChain.getRepository();

        Repository track = repository.startTracking();

        for (int k = 1; k <= naccounts; k++) {
            Account account = createAccount(k);
            track.createAccount(account.getAddress());
            track.addBalance(account.getAddress(), balance);
        }

        track.commit();
    }
}
