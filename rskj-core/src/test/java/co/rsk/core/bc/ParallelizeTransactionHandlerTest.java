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
        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), 0);
        short[] expectedTransactionEdgeList = new short[]{1};
        long expectedGasUsed = 0;

        assertTrue(bucketGasUsed.isPresent());
        assertEquals(expectedGasUsed, (long) bucketGasUsed.get());

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void addTransactionIntoTheHandlerAndShouldBeSubtractedGasUsedInTheBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
    }

    @Test
    public void addTwoTransactionsWithTheSameReadKeyAndShouldBeAddedInDifferentBuckets() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentReadKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createAMapAndAddAKey(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx2);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithSameWrittenKeysShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx+gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentWrittenKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createAMapAndAddAKey(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx2);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithTheSameWrittenReadKeyShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx+gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithTheSameReadWrittenKeyShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx+gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentReadWrittenKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionWithDifferentWrittenReadKeyShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, writtenKeys,  new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoIndependentTxsAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> differentWrittenKeys = createAMapAndAddAKey(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), differentWrittenKeys, gasUsedByTx2);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, differentWrittenKeys, writtenKeys, gasUsedByTx3);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(gasUsedByTx3, (long) bucketGasUsed3.get());
        assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void addTwoDependentTxsWithTheSecondInSequentialAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        long totalGasInSequential = gasUsedByTx2 + gasUsedByTx3;


        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);
        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());
        assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(totalGasInSequential, (long) bucketGasUsed3.get());
        assertEquals(totalGasInSequential, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, tx2, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void addABigTransactionAndAnotherWithTheSameWrittenKeyAndTheLastOneShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void addABigTxAndAnotherWithTheSameReadWrittenKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, readKeys, new HashSet<>(), gasUsedByBigTx);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void addABigTxAndAnotherWithTheSameWrittenReadKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createAMapAndAddAKey(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void twoTransactionWithTheSameSenderShouldBeInTheSameBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(2*gasUsedByTx, (long) bucketGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void ifATxCollidesWithAnotherOneThatAlsoHasTheSameSenderShouldGoIntoTheSameBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(2*gasUsedByTx, (long) bucketGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void ifATransactionHasAnAlreadyAddedSenderButCollidesWithAnotherTxShouldBeAddedIntoTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createAMapAndAddAKey(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());
        assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void ifANewTxComesAndAllThePossibleBucketsAreFullTheTxShouldNotBeAdded() {
        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(bigTx);

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> bucketGasUsed4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        assertFalse(bucketGasUsed4.isPresent());
        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());

        assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByBigTx2, (long) bucketGasUsed2.get());
        assertEquals(gasUsedByBigTx, (long) bucketGasUsed3.get());
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));
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

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());

        assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByBigTx2, (long) bucketGasUsed2.get());
        assertEquals(gasUsedByTx, (long) bucketGasUsed3.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void ifAllTheBucketsAreFullTheNewTxShouldntBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, bigTx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        assertTrue(bucketGasUsed3.isPresent());
        assertEquals(gasUsedByBigTx, (long) bucketGasUsed3.get());
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> emptyBucket = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(tx.getGasLimit()));
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertFalse(emptyBucket.isPresent());
        assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        assertEquals(gasUsedByBigTx2, (long) bucketGasUsed2.get());
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    public void aRemascTxAddedShouldBeInTheSequentialBucket() {
        List<Transaction> expectedListOfTxs = Collections.singletonList(tx);
        long gasUsedByTx = GasCost.toGas(bigTx.getGasLimit());

        assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        Optional<Long> sequentialBucketGasUsed = handler.addRemascTransaction(tx, gasUsedByTx);

        assertTrue(sequentialBucketGasUsed.isPresent());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertEquals(gasUsedByTx, (long) sequentialBucketGasUsed.get());
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
    }

    @Test
    public void ifItsSequentialTheEdgesListShouldHaveSizeZero() {
        handler.addRemascTransaction(tx, GasCost.toGas(bigTx.getGasLimit()));
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
        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }
}
