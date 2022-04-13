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

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.GasCost;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.Assert.*;

public class ParallelizeTransactionHandlerTest {

    private short buckets;
    private ParallelizeTransactionHandler handler;
    private Transaction tx;
    private Transaction tx2;
    private Transaction tx3;
    private ByteArrayWrapper aWrappedKey;
    private ByteArrayWrapper aDifferentWrapperKey;
    private Transaction bigTx;
    private Transaction bigTx2;
    private short sequentialBucketNumber;

    @Before
    public void setup() {
        Account sender = new AccountBuilder().name("sender").build();
        Account sender2 = new AccountBuilder().name("sender2").build();
        Account sender3 = new AccountBuilder().name("sender3").build();
        Account sender4 = new AccountBuilder().name("sender4").build();
        Account sender5 = new AccountBuilder().name("sender5").build();
        byte[] aKey = {1, 2, 3};
        byte[] aDifferentKey = {1, 2, 3, 4};
        int blockGasLimit = 6800000;
        long gasUsedByTx = 16000;
        long biggestGasLimitPossibleInBucket = blockGasLimit / 2 - 1;

        aWrappedKey = new ByteArrayWrapper(aKey);
        buckets = 2;
        sequentialBucketNumber = buckets;
        handler = new ParallelizeTransactionHandler(buckets, blockGasLimit/buckets);
        tx = new TransactionBuilder().nonce(1).sender(sender).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx2 = new TransactionBuilder().nonce(1).sender(sender2).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx3 = new TransactionBuilder().nonce(1).sender(sender3).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        bigTx = new TransactionBuilder().nonce(1).sender(sender4).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInBucket)).value(BigInteger.valueOf(1)).build();
        bigTx2 = new TransactionBuilder().nonce(1).sender(sender5).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInBucket)).value(BigInteger.valueOf(1)).build();
        aDifferentWrapperKey = new ByteArrayWrapper(aDifferentKey);
    }

    @Test
    public void createAHandlerShouldReturnAnEmptyTransactionList() {
        int expectedNumberOfTxs = 0;
        int expectedNumberOfTxsInBuckets = 0;

        assertEquals(expectedNumberOfTxs, handler.getTransactionsInOrder().size());
        assertEquals(expectedNumberOfTxsInBuckets, handler.getTransactionsPerBucketInOrder().length);
    }

    @Test
    public void createAHandlerAndGasUsedInBucketShouldBeZero() {
        int expectedGasUsed = 0;
        for (short i = 0; i < buckets; i++) {
            assertEquals(expectedGasUsed, handler.getGasUsedIn(i));
        }
    }

    @Test
    public void addTransactionIntoTheHandlerAndShouldBeAddedInTheFirstParallelBucket() {
        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), 0);
        short[] expectedTransactionEdgeList = new short[]{1};
        short expectedBucketId = 0;

        assertTrue(bucketId.isPresent());
        assertEquals((Short) expectedBucketId, bucketId.get());

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void addTransactionIntoTheHandlerAndShouldBeSubtractedGasUsedInTheBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        assertTrue(bucketId.isPresent());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(bucketId.get()));
    }

    @Test
    public void addTwoTransactionsWithTheSameReadKeyAndShouldBeAddedInDifferentBuckets() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Short> bucketId2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx);

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentReadKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createAMapAndAddAKey(aDifferentWrapperKey);

        Optional<Short> bucketId = handler.addTransaction(tx, readKeys, new HashSet<>(), GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), GasCost.toGas(tx2.getGasLimit()));

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithSameWrittenKeysShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, GasCost.toGas(tx2.getGasLimit()));

        assertEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentWrittenKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        HashSet<ByteArrayWrapper> writtenKeys2 = createAMapAndAddAKey(aDifferentWrapperKey);

        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, GasCost.toGas(tx2.getGasLimit()));

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithTheSameWrittenReadKeyShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), GasCost.toGas(tx2.getGasLimit()));

        assertEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithTheSameReadWrittenKeyShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(tx, readKeys, new HashSet<>(), GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, GasCost.toGas(tx2.getGasLimit()));

        assertEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentReadWrittenKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aDifferentWrapperKey);

        Optional<Short> bucketId = handler.addTransaction(tx, readKeys, new HashSet<>(), GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, GasCost.toGas(tx2.getGasLimit()));

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionWithDifferentWrittenReadKeyShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aDifferentWrapperKey);

        Optional<Short> bucketId = handler.addTransaction(tx, writtenKeys,  new HashSet<>(), GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), GasCost.toGas(tx2.getGasLimit()));

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoIndependentTxsAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};
        long tx3GasLimit = GasCost.toGas(tx3.getGasLimit());

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> differentWrittenKeys = createAMapAndAddAKey(aDifferentWrapperKey);

        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, new HashSet<>(), differentWrittenKeys, GasCost.toGas(tx2.getGasLimit()));
        Optional<Short> bucketId3 = handler.addTransaction(tx3, differentWrittenKeys, writtenKeys, tx3GasLimit);

        assertTrue(bucketId.isPresent() && bucketId2.isPresent() && bucketId3.isPresent());

        assertEquals((short) 0, (short) bucketId.get());
        assertEquals((short) 1, (short) bucketId2.get());
        assertEquals(sequentialBucketNumber, (short) bucketId3.get());
        assertEquals(handler.getGasUsedIn(sequentialBucketNumber), tx3GasLimit);

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        expectedListOfTxs.add(tx2);
        expectedListOfTxs.add(tx3);

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void addTwoDependentTxsWithTheSecondInSequentialAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, GasCost.toGas(bigTx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, GasCost.toGas(tx2.getGasLimit()));
        Optional<Short> bucketId3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, GasCost.toGas(tx3.getGasLimit()));

        assertTrue(bucketId.isPresent() && bucketId2.isPresent() && bucketId3.isPresent());

        assertEquals((short) 0, (short) bucketId.get());
        assertEquals(sequentialBucketNumber, (short) bucketId2.get());
        assertEquals(sequentialBucketNumber, (short) bucketId3.get());

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(tx2);
        expectedListOfTxs.add(tx3);

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void addABigTransactionAndAnotherWithTheSameWrittenKeyAndTheLastOneShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, GasCost.toGas(bigTx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void addABigTxAndAnotherWithTheSameReadWrittenKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(bigTx, readKeys, new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void addABigTxAndAnotherWithTheSameWrittenReadKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, GasCost.toGas(bigTx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(tx, readKeys, new HashSet<>(), GasCost.toGas(tx.getGasLimit()));

        assertNotEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void twoTransactionWithTheSameSenderShouldBeInTheSameBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};

        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Short> bucketId2 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        assertEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void ifATxCollidesWithAnotherOneThatAlsoHasTheSameSenderShouldGoIntoTheSameBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Short> bucketId2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        assertEquals(bucketId, bucketId2);
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void ifATransactionHasAnAlreadyAddedSenderButCollidesWithAnotherTxShouldBeAddedIntoTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Short> bucketId = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Short> bucketId2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, GasCost.toGas(tx2.getGasLimit()));
        Optional<Short> bucketId3 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        assertNotEquals(bucketId, bucketId2);
        assertNotEquals(bucketId, bucketId3);
        assertNotEquals(bucketId2, bucketId3);

        assertTrue(bucketId3.isPresent());
        assertEquals(sequentialBucketNumber, (short) bucketId3.get());

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        expectedListOfTxs.add(tx2);
        expectedListOfTxs.add(tx);

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void ifANewTxComesAndAllThePossibleBucketsAreFullTheTxShouldNotBeAdded() {
        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(bigTx);

        Optional<Short> bucketId = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Short> bucketId2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx2.getGasLimit()));
        Optional<Short> bucketId3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Short> bucketId4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(tx.getGasLimit()));

        assertEquals((short) 0, (short) bucketId.get());
        assertEquals((short) 1, (short) bucketId2.get());
        assertEquals(sequentialBucketNumber, (short) bucketId3.get());
        assertFalse(bucketId4.isPresent());

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void ifBucketsAreFullAndAnIndependentTxComesShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(tx);

        Optional<Short> bucketId = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx2.getGasLimit()));
        Optional<Short> bucketId3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(tx.getGasLimit()));

        assertEquals((short) 0, (short) bucketId.get());
        assertEquals((short) 1, (short) bucketId2.get());
        assertEquals(sequentialBucketNumber, (short) bucketId3.get());

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void ifAllTheBucketsAreFullTheNewTxShouldntBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(bigTx);

        Optional<Short> bucketId = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        Optional<Short> bucketId2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx2.getGasLimit()));
        Optional<Short> bucketId3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        Optional<Short> emptyBucket = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(tx.getGasLimit()));

        assertFalse(emptyBucket.isPresent());
        assertEquals((short) 0, (short) bucketId.get());
        assertEquals((short) 1, (short) bucketId2.get());
        assertEquals(sequentialBucketNumber, (short) bucketId3.get());

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void aRemascTxAddedShouldBeInTheSequentialBucket() {
        List<Transaction> expectedListOfTxs = Collections.singletonList(tx);
        Optional<Short> bucketId = handler.addRemascTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        assertEquals(sequentialBucketNumber, (short) bucketId.get());
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
    }

    @Test
    public void ifItsSequentialTheEdgesListShouldHaveSizeZero() {
        handler.addRemascTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        assertEquals(0, handler.getTransactionsPerBucketInOrder().length);
    }

    @Test
    public void callGetGasUsedInWithAnInvalidBucketShouldThrowAnError() {
        short invalidBucketId = (short) (buckets+1);
        try {
            handler.getGasUsedIn(invalidBucketId);
            fail();
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    @Test
    public void callGetGasUsedInWithAnInvalidBucketShouldThrowAnError2() {
        short invalidBucketId = -1;
        try {
            handler.getGasUsedIn(invalidBucketId);
            fail();
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    private HashSet<ByteArrayWrapper> createAMapAndAddAKey(ByteArrayWrapper aKey) {
        HashSet<ByteArrayWrapper> aMap = new HashSet<>();
        aMap.add(aKey);
        return aMap;
    }

    private void assertTwoTransactionsWereAddedProperlyIntoTheBuckets(Transaction tx, Transaction tx2, short[] expectedTransactionEdgeList) {
        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        expectedListOfTxs.add(tx2);

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }
}
