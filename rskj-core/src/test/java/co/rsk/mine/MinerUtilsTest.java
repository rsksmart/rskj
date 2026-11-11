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
package co.rsk.mine;

import co.rsk.test.builders.TransactionBuilder;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;

class MinerUtilsTest {

    private static final Coin ONE_COIN = Coin.valueOf(1L);

    private final TestSystemProperties config = new TestSystemProperties();
    private MinerUtils minerUtils;
    private SignatureCache signatureCache;

    @BeforeEach
    void setup() {
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        minerUtils = new MinerUtils();
    }

    @Test
    void getAllTransactionsTest() {
        TransactionPool transactionPool = Mockito.mock(TransactionPool.class);

        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);

        byte[] s1 = new byte[32];
        byte[] s2 = new byte[32];

        s1[0] = 0;
        s2[0] = 1;

        byte[] addressBytes = TestUtils.generateBytes("addressBytes",20);

        Mockito.when(tx1.getHash()).thenReturn(new Keccak256(s1));
        Mockito.when(tx2.getHash()).thenReturn(new Keccak256(s2));
        Mockito.when(tx1.getNonce()).thenReturn(ByteUtil.cloneBytes(BigInteger.ZERO.toByteArray()));
        Mockito.when(tx2.getNonce()).thenReturn(ByteUtil.cloneBytes(BigInteger.TEN.toByteArray()));
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx1.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx2.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));

        List<Transaction> txs = new LinkedList<>();

        txs.add(tx1);
        txs.add(tx2);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        List<Transaction> res = minerUtils.getAllTransactions(transactionPool, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals(2, res.size());
    }

    @Test
    void validTransactionRepositoryNonceTest() {
        Transaction tx = TransactionBuilder.createMockTransaction(config, 0, 50000, 5, 0, 0, 0);
        //Mockito.when(tx.checkGasPrice(Mockito.any(BigInteger.class))).thenReturn(true);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx.getSender(signatureCache))).thenReturn(BigInteger.valueOf(0));

        List<Transaction> res = minerUtils.filterTransactions(new LinkedList<>(), txs, repository, ONE_COIN, true, false, signatureCache);
        Assertions.assertEquals(1, res.size());
    }

    @Test
    void validTransactionAccWrapNonceTest() {
        Transaction tx0 = TransactionBuilder.createMockTransaction(config, 0, 50000, 5, 0, 0, 0);
        Transaction tx1 = TransactionBuilder.createMockTransaction(config, 0, 50000, 5, 1, 0, 0);
        List<Transaction> txs = new LinkedList<>(Arrays.asList(tx0, tx1));
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx0.getSender(signatureCache))).thenReturn(BigInteger.valueOf(0));

        List<Transaction> res = minerUtils.filterTransactions(new LinkedList<>(), txs, repository, ONE_COIN, true, false, signatureCache);
        Assertions.assertEquals(Arrays.asList(tx0, tx1), res);
    }

    @Test
    void nonceTrackingDoesNotLeakBetweenCalls() {
        Transaction tx0 = TransactionBuilder.createMockTransaction(config, 0, 50000, 5, 0, 0, 0);
        Transaction tx1 = TransactionBuilder.createMockTransaction(config, 0, 50000, 5, 1, 0, 0);
        List<Transaction> txs = new LinkedList<>(Arrays.asList(tx0, tx1));
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx0.getSender(signatureCache))).thenReturn(BigInteger.valueOf(0));

        List<Transaction> first = minerUtils.filterTransactions(new LinkedList<>(), txs, repository, ONE_COIN, true, false, signatureCache);
        List<Transaction> second = minerUtils.filterTransactions(new LinkedList<>(), txs, repository, ONE_COIN, true, false, signatureCache);

        Assertions.assertEquals(Arrays.asList(tx0, tx1), first);
        Assertions.assertEquals(Arrays.asList(tx0, tx1), second);
    }

    @ParameterizedTest(name = "rskip545 = {0}")
    @ValueSource(booleans = {false, true})
    void txRejectedByGasPriceDoesNotAdvanceNonce(boolean isRskip545Enabled) {
        Coin minGasPrice = Coin.valueOf(2L);
        Transaction lowGasTx0 = TransactionBuilder.createMockTransaction(config, 0, 50000, 1, 0, 0, 0);
        Transaction tx1 = TransactionBuilder.createMockTransaction(config, 0, 50000, 5, 1, 0, 0);
        List<Transaction> txs = new LinkedList<>(Arrays.asList(lowGasTx0, tx1));
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(lowGasTx0.getSender(signatureCache))).thenReturn(BigInteger.valueOf(0));

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, minGasPrice, true, isRskip545Enabled, signatureCache);

        // Gas price checks apply in both modes and evict the tx from the pool.
        Assertions.assertEquals(Collections.singletonList(lowGasTx0), txsToRemove);
        if (isRskip545Enabled) {
            Assertions.assertEquals(Collections.singletonList(tx1), res);
        } else {
            Assertions.assertEquals(Collections.emptyList(), res);
        }
    }

    @Test
    void invalidNonceTransactionTest() {
        Transaction tx = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx.getSender(signatureCache))).thenReturn(BigInteger.valueOf(1));

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, ONE_COIN, true, false, signatureCache);
        Assertions.assertEquals(0, res.size());
        Assertions.assertEquals(0, txsToRemove.size());
    }

    @Test
    void rskip545Enabled_nonceMismatchIsNotFilteredAndLeftToExecution() {
        Transaction tx = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx.getSender(signatureCache))).thenReturn(BigInteger.valueOf(1));

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, ONE_COIN, true, true, signatureCache);
        Assertions.assertEquals(1, res.size());
        Assertions.assertEquals(0, txsToRemove.size());
    }

    @Test
    void rskip545Disabled_nonceMismatchIsStillFiltered() {
        Transaction tx = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx.getSender(signatureCache))).thenReturn(BigInteger.valueOf(1));

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, ONE_COIN, true, false, signatureCache);
        Assertions.assertEquals(0, res.size());
        Assertions.assertEquals(0, txsToRemove.size());
    }

    @Test
    void rskip545Enabled_lowGasPriceIsStillRemoved() {
        Transaction tx = TransactionBuilder.createMockTransaction(config, 0, 50000, 1, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Coin minGasPrice = Coin.valueOf(2L);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, Mockito.mock(Repository.class), minGasPrice, true, true, signatureCache);
        Assertions.assertEquals(0, res.size());
        Assertions.assertEquals(1, txsToRemove.size());
    }

    @Test
    void rskip545Enabled_txsWithSkippedNoncesFromSameSenderAllPass() {
        Transaction otherSenderTx = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 10, 0, 1);
        Transaction senderTx6 = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 6, 0, 2);
        Transaction senderTx8 = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 8, 0, 2);
        List<Transaction> txs = new LinkedList<>(Arrays.asList(otherSenderTx, senderTx6, senderTx8));

        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(otherSenderTx.getSender(signatureCache))).thenReturn(BigInteger.valueOf(10));
        Mockito.when(repository.getNonce(senderTx6.getSender(signatureCache))).thenReturn(BigInteger.valueOf(5));

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, ONE_COIN, true, true, signatureCache);

        Assertions.assertEquals(Arrays.asList(otherSenderTx, senderTx6, senderTx8), res);
        Assertions.assertEquals(0, txsToRemove.size());
    }

    @Test
    void rskip545Disabled_txsWithSkippedNoncesFromSameSenderAreSkipped() {
        Transaction otherSenderTx = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 10, 0, 1);
        Transaction senderTx6 = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 6, 0, 2);
        Transaction senderTx8 = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 8, 0, 2);
        List<Transaction> txs = new LinkedList<>(Arrays.asList(otherSenderTx, senderTx6, senderTx8));

        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(otherSenderTx.getSender(signatureCache))).thenReturn(BigInteger.valueOf(10));
        Mockito.when(repository.getNonce(senderTx6.getSender(signatureCache))).thenReturn(BigInteger.valueOf(5));

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, ONE_COIN, true, false, signatureCache);

        Assertions.assertEquals(Collections.singletonList(otherSenderTx), res);
        Assertions.assertEquals(0, txsToRemove.size());
    }

    @Test
    void rskip545Enabled_doesNotReadParentState() {
        Transaction tx1 = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 3, 0, 1);
        Transaction tx2 = TransactionBuilder.createMockTransaction(config, 0, 50000, 2, 7, 0, 2);
        List<Transaction> txs = new LinkedList<>(Arrays.asList(tx1, tx2));
        Repository repository = Mockito.mock(Repository.class);

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, ONE_COIN, true, true, signatureCache);

        Assertions.assertEquals(2, res.size());
        Mockito.verifyNoInteractions(repository);
    }

    @Test
    void invalidGasPriceTransactionTest() {
        Transaction tx = TransactionBuilder.createMockTransaction(config, 0, 50000, 1, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(2L);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, minGasPrice, true, false, signatureCache);
        Assertions.assertEquals(0, res.size());
        Assertions.assertEquals(1, txsToRemove.size());
    }

    @Test
    void harmfulTransactionTest() {
        Transaction tx = TransactionBuilder.createMockTransaction(config, 0, 50000, 1, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Mockito.when(tx.getGasPrice()).thenReturn(null);
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(2L);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, minGasPrice, true, false, signatureCache);
        Assertions.assertEquals(0, res.size());
        Assertions.assertEquals(1, txsToRemove.size());
    }

    @Test
    void filterTransactions_whenRskip252DisabledThenTxIncludedRegardlessGasPrice() {
        long minGasPriceRef = 2L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);
        long capGasPrice = minGasPriceRef * 100;

        Transaction txLessGasPriceThanCap = TransactionBuilder.createMockTransaction(config, 0, 50000, capGasPrice - 1, 1, 0, 0);
        Transaction txMoreGasPriceThanCap = TransactionBuilder.createMockTransaction(config, 0, 50000, capGasPrice + 1_000_000_000_000L, 1, 0, 1);
        List<Transaction> txs = new LinkedList<>();
        txs.add(txLessGasPriceThanCap);
        txs.add(txMoreGasPriceThanCap);
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(txLessGasPriceThanCap.getSender(signatureCache))).thenReturn(BigInteger.ONE);
        Mockito.when(repository.getNonce(txMoreGasPriceThanCap.getSender(signatureCache))).thenReturn(BigInteger.ONE);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, minGasPrice, false, false, signatureCache);

        Assertions.assertEquals(2, res.size());
        Assertions.assertEquals(0, txsToRemove.size());
    }

    @Test
    void filterTransactions_whenRskip252EnabledThenTxWithMoreGasPriceThanCapExcluded() {
        long minGasPriceRef = 2L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);
        long capGasPrice = minGasPriceRef * 100;

        Transaction txLessGasPriceThanCap = TransactionBuilder.createMockTransaction(config, 0, 50000, capGasPrice - 1, 1, 0, 0);
        Transaction txMoreGasPriceThanCap = TransactionBuilder.createMockTransaction(config, 0, 50000, capGasPrice + 1, 1, 0, 1);
        List<Transaction> txs = new LinkedList<>();
        txs.add(txLessGasPriceThanCap);
        txs.add(txMoreGasPriceThanCap);
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(txLessGasPriceThanCap.getSender(signatureCache))).thenReturn(BigInteger.ONE);
        Mockito.when(repository.getNonce(txMoreGasPriceThanCap.getSender(signatureCache))).thenReturn(BigInteger.ONE);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = minerUtils.filterTransactions(txsToRemove, txs, repository, minGasPrice, true, false, signatureCache);

        Assertions.assertEquals(1, res.size());
        Assertions.assertEquals(txLessGasPriceThanCap, res.get(0));
        Assertions.assertEquals(1, txsToRemove.size());
        Assertions.assertEquals(txMoreGasPriceThanCap, txsToRemove.get(0));
    }

    @Test
    @SuppressWarnings("squid:S5961")
    void getAllTransactionsCheckOrderTest() {
        TransactionPool transactionPool = Mockito.mock(TransactionPool.class);

        Transaction tx0 = Mockito.mock(Transaction.class);
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        Transaction tx3 = Mockito.mock(Transaction.class);

        byte[] nonce0 = ByteUtil.cloneBytes(BigInteger.valueOf(0).toByteArray());
        byte[] nonce1 = ByteUtil.cloneBytes(BigInteger.valueOf(1).toByteArray());
        byte[] nonce2 = ByteUtil.cloneBytes(BigInteger.valueOf(2).toByteArray());

        byte[] addressBytes = TestUtils.generateBytes("addressBytes",20);
        Mockito.when(tx0.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx0.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx0.getGasPrice()).thenReturn(Coin.valueOf(10));

        Mockito.when(tx1.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx1.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));

        Mockito.when(tx2.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx2.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce1));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(10));

        Mockito.when(tx3.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx3.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce2));
        Mockito.when(tx3.getGasPrice()).thenReturn(Coin.valueOf(100));

        List<Transaction> txs = new LinkedList<>();

        //Test order with same nonce different price
        txs.add(tx0);
        txs.add(tx1);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        List<Transaction> res = minerUtils.getAllTransactions(transactionPool, signatureCache);

        Assertions.assertEquals(2, res.size());
        Assertions.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(10));
        Assertions.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(1));

        //Test order with same sender, different nonce, different price
        txs = new LinkedList<>();
        txs.add(tx3);
        txs.add(tx1);
        txs.add(tx2);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        res = minerUtils.getAllTransactions(transactionPool, signatureCache);

        Assertions.assertEquals(3, res.size());
        Assertions.assertEquals(res.get(0).getNonce(), tx1.getNonce());
        Assertions.assertEquals(res.get(1).getNonce(), tx2.getNonce());
        Assertions.assertEquals(res.get(2).getNonce(), tx3.getNonce());
        Assertions.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(1));
        Assertions.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(10));
        Assertions.assertEquals(res.get(2).getGasPrice(), Coin.valueOf(100));

        // Test order with different sender, nonce and price
        Transaction tx4 = Mockito.mock(Transaction.class);
        Transaction tx5 = Mockito.mock(Transaction.class);
        Transaction tx6 = Mockito.mock(Transaction.class);

        byte[] addressBytes2 = TestUtils.generateBytes("addressBytes2",20);
        Mockito.when(tx4.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes2));
        Mockito.when(tx4.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx4.getGasPrice()).thenReturn(Coin.valueOf(50));

        Mockito.when(tx5.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes2));
        Mockito.when(tx5.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce1));
        Mockito.when(tx5.getGasPrice()).thenReturn(Coin.valueOf(1000));

        Mockito.when(tx6.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes2));
        Mockito.when(tx6.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce2));
        Mockito.when(tx6.getGasPrice()).thenReturn(Coin.valueOf(1));

        // Test another sender.
        txs.add(tx6);
        txs.add(tx5);
        txs.add(tx4);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        res = minerUtils.getAllTransactions(transactionPool, signatureCache);

        Assertions.assertEquals(6, res.size());
        Assertions.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(50));
        Assertions.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(1000));
        Assertions.assertEquals(res.get(2).getGasPrice(), Coin.valueOf(1));
        Assertions.assertEquals(res.get(3).getGasPrice(), Coin.valueOf(10));
        Assertions.assertEquals(res.get(4).getGasPrice(), Coin.valueOf(100));
        Assertions.assertEquals(res.get(5).getGasPrice(), Coin.valueOf(1));

        Transaction tx7 = Mockito.mock(Transaction.class);
        Transaction tx8 = Mockito.mock(Transaction.class);
        Transaction tx9 = Mockito.mock(Transaction.class);

        byte[] addressBytes3 = TestUtils.generateBytes("addressBytes3",20);
        Mockito.when(tx7.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes3));
        Mockito.when(tx7.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx7.getGasPrice()).thenReturn(Coin.valueOf(500));

        Mockito.when(tx8.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes3));
        Mockito.when(tx8.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce1));
        Mockito.when(tx8.getGasPrice()).thenReturn(Coin.valueOf(500));

        Mockito.when(tx9.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes3));
        Mockito.when(tx9.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce2));
        Mockito.when(tx9.getGasPrice()).thenReturn(Coin.valueOf(2000));

        txs.add(tx7);
        txs.add(tx8);
        txs.add(tx9);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        res = minerUtils.getAllTransactions(transactionPool, signatureCache);

        Assertions.assertEquals(9, res.size());
        Assertions.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(500));
        Assertions.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(500));
        Assertions.assertEquals(res.get(2).getGasPrice(), Coin.valueOf(2000));
        Assertions.assertEquals(res.get(3).getGasPrice(), Coin.valueOf(50));
        Assertions.assertEquals(res.get(4).getGasPrice(), Coin.valueOf(1000));
        Assertions.assertEquals(res.get(5).getGasPrice(), Coin.valueOf(1));
        Assertions.assertEquals(res.get(6).getGasPrice(), Coin.valueOf(10));
        Assertions.assertEquals(res.get(7).getGasPrice(), Coin.valueOf(100));
        Assertions.assertEquals(res.get(8).getGasPrice(), Coin.valueOf(1));
    }

    @Test
    void getAllTransactions_withNonCanonicalNonce_discardsAndReturnsValidTxs() {
        TransactionPool transactionPool = Mockito.mock(TransactionPool.class);

        Transaction validTx = Mockito.mock(Transaction.class);
        Transaction nonCanonicalTx = Mockito.mock(Transaction.class);

        byte[] addressBytes = TestUtils.generateBytes("addressBytes", 20);

        Mockito.when(validTx.getHash()).thenReturn(new Keccak256(new byte[32]));
        Mockito.when(validTx.getNonce()).thenReturn(new byte[]{0x01});
        Mockito.when(validTx.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(validTx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));

        byte[] nonCanonicalHash = new byte[32];
        nonCanonicalHash[0] = 1;
        Mockito.when(nonCanonicalTx.getHash()).thenReturn(new Keccak256(nonCanonicalHash));
        Mockito.when(nonCanonicalTx.getNonce()).thenReturn(new byte[9]);
        Mockito.when(nonCanonicalTx.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(nonCanonicalTx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));

        List<Transaction> txs = new LinkedList<>();
        txs.add(validTx);
        txs.add(nonCanonicalTx);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        List<Transaction> txsToRemove = new ArrayList<>();
        List<Transaction> res = minerUtils.getAllTransactions(transactionPool, signatureCache, txsToRemove);

        Assertions.assertEquals(1, res.size());
        Assertions.assertEquals(validTx, res.get(0));
        Assertions.assertEquals(1, txsToRemove.size());
        Assertions.assertEquals(nonCanonicalTx, txsToRemove.get(0));
    }

    @Test
    void getAllTransactions_allValid_noDiscarding() {
        TransactionPool transactionPool = Mockito.mock(TransactionPool.class);

        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);

        byte[] addressBytes = TestUtils.generateBytes("addressBytes", 20);

        Mockito.when(tx1.getHash()).thenReturn(new Keccak256(new byte[32]));
        Mockito.when(tx1.getNonce()).thenReturn(new byte[]{0x01});
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx1.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));

        byte[] hash2 = new byte[32];
        hash2[0] = 1;
        Mockito.when(tx2.getHash()).thenReturn(new Keccak256(hash2));
        Mockito.when(tx2.getNonce()).thenReturn(new byte[]{0x02});
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(addressBytes));

        List<Transaction> txs = new LinkedList<>();
        txs.add(tx1);
        txs.add(tx2);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        List<Transaction> txsToRemove = new ArrayList<>();
        List<Transaction> res = minerUtils.getAllTransactions(transactionPool, signatureCache, txsToRemove);

        Assertions.assertEquals(2, res.size());
        Assertions.assertTrue(txsToRemove.isEmpty());
    }

}
