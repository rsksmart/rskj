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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;


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

    @BeforeEach
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
        long biggestGasLimitPossibleInBucket = blockGasLimit - 1;

        aWrappedKey = new ByteArrayWrapper(aKey);
        buckets = 2;
        sequentialBucketNumber = buckets;
        handler = new ParallelizeTransactionHandler(buckets, blockGasLimit);
        tx = new TransactionBuilder().nonce(1).sender(sender).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx2 = new TransactionBuilder().nonce(1).sender(sender2).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx3 = new TransactionBuilder().nonce(1).sender(sender3).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        bigTx = new TransactionBuilder().nonce(1).sender(sender4).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInBucket)).value(BigInteger.valueOf(1)).build();
        bigTx2 = new TransactionBuilder().nonce(1).sender(sender5).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInBucket)).value(BigInteger.valueOf(1)).build();
        aDifferentWrapperKey = new ByteArrayWrapper(aDifferentKey);
    }

    @Test
    void createAHandlerShouldReturnAnEmptyTransactionList() {
        int expectedNumberOfTxs = 0;
        int expectedNumberOfTxsInBuckets = 0;

        Assertions.assertEquals(expectedNumberOfTxs, handler.getTransactionsInOrder().size());
        Assertions.assertEquals(expectedNumberOfTxsInBuckets, handler.getTransactionsPerBucketInOrder().length);
    }

    @Test
    void createAHandlerAndGasUsedInBucketShouldBeZero() {
        int expectedGasUsed = 0;
        for (short i = 0; i < buckets; i++) {
            Assertions.assertEquals(expectedGasUsed, handler.getGasUsedIn(i));
        }
    }

    @Test
    void addTransactionIntoTheHandlerAndShouldBeAddedInTheFirstParallelBucket() {
        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), 0);
        short[] expectedTransactionEdgeList = new short[]{1};
        long expectedGasUsed = 0;

        Assertions.assertTrue(bucketGasUsed.isPresent());
        Assertions.assertEquals(expectedGasUsed, (long) bucketGasUsed.get());

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void addTransactionIntoTheHandlerAndShouldBeSubtractedGasUsedInTheBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
    }

    @Test
    void addTwoTransactionsWithTheSameReadKeyAndShouldBeAddedInDifferentBuckets() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        Set<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithDifferentReadKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx2);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithSameWrittenKeysShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx+gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithDifferentWrittenKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx2);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithTheSameWrittenReadKeyShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx+gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithTheSameReadWrittenKeyShouldBeAddedInTheSameBucket() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx+gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithDifferentReadWrittenKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionWithDifferentWrittenReadKeyShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, writtenKeys,  new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoIndependentTxsAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> differentWrittenKeys = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), differentWrittenKeys, gasUsedByTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, differentWrittenKeys, writtenKeys, gasUsedByTx3);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx3, (long) bucketGasUsed3.get());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void addTwoDependentTxsWithTheSecondInSequentialAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        long totalGasInSequential = gasUsedByTx2 + gasUsedByTx3;


        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);
        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(totalGasInSequential, (long) bucketGasUsed3.get());
        Assertions.assertEquals(totalGasInSequential, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void addABigTransactionAndAnotherWithTheSameWrittenKeyAndTheLastOneShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    void addABigTxAndAnotherWithTheSameReadWrittenKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, readKeys, new HashSet<>(), gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    void addABigTxAndAnotherWithTheSameWrittenReadKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithTheSameSenderToTheSequentialBucketAndTheSecondShouldBeAddedCorrectly() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, tx, tx);
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx2.getGasLimit()));
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed3.get());

        Optional<Long> bucketGasUsed4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(bucketGasUsed4.isPresent());
        Assertions.assertEquals(2*gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertEquals(2*gasUsedByTx, (long) bucketGasUsed4.get());

        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void twoTransactionWithTheSameSenderShouldBeInTheSameBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(2*gasUsedByTx, (long) bucketGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    void ifATxHasTheSameSenderThatAnotherAlreadyAddedIntoTheSequentialShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrapperKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrapperKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx);
        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, readKeys, new HashSet<>(), gasUsedByTx3);
        Optional<Long> bucketGasUsed4 = handler.addTransaction(tx3, new HashSet<>(), new HashSet<>(), gasUsedByTx3);
        Assertions.assertTrue(bucketGasUsed3.isPresent() && bucketGasUsed4.isPresent());
        Assertions.assertEquals(gasUsedByTx3*2, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifATxReadTwoDifferentWrittenKeysShouldGoToSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrapperKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrapperKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx);
        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, readKeys, new HashSet<>(), gasUsedByTx3);
        Assertions.assertTrue(bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifATxWritesAKeyAlreadyReadByTwoTxsPlacedInDifferentBucketsShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3);
        Assertions.assertTrue(bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifATxReadTwoKeysThatAreInDifferentBucketsShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aDifferentWrapperKey);
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrapperKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3);
        Assertions.assertTrue(bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifATxCollidesWithAnotherOneThatAlsoHasTheSameSenderShouldGoIntoTheSameBucket() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(2*gasUsedByTx, (long) bucketGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheBuckets(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    void ifATransactionHasAnAlreadyAddedSenderButCollidesWithAnotherTxShouldBeAddedIntoTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifANewTxComesAndAllThePossibleBucketsAreFullTheTxShouldNotBeAdded() {
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
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> bucketGasUsed4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertFalse(bucketGasUsed4.isPresent());
        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());

        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed3.get());
        Assertions.assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifBucketsAreFullAndAnIndependentTxComesShouldBeAddedInTheSequential() {
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
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent() && bucketGasUsed3.isPresent());

        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, (long) bucketGasUsed3.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifAllTheBucketsAreFullTheNewIndependentTxShouldNotBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, bigTx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Assertions.assertTrue(bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed3.get());
        Assertions.assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> emptyBucket = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(tx.getGasLimit()));
        Assertions.assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertFalse(emptyBucket.isPresent());
        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void ifAllTheBucketsAreFullTheNewDependentTxShouldNotBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, bigTx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> bucketGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Optional<Long> bucketGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> bucketGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Assertions.assertTrue(bucketGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed3.get());
        Assertions.assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));

        Optional<Long> emptyBucket = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));
        Assertions.assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertFalse(emptyBucket.isPresent());
        Assertions.assertTrue(bucketGasUsed.isPresent() && bucketGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) bucketGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) bucketGasUsed2.get());
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }

    @Test
    void aRemascTxAddedShouldBeInTheSequentialBucket() {
        List<Transaction> expectedListOfTxs = Collections.singletonList(tx);
        long gasUsedByTx = GasCost.toGas(bigTx.getGasLimit());

        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialBucketNumber));
        Optional<Long> sequentialBucketGasUsed = handler.addRemascTransaction(tx, gasUsedByTx);

        Assertions.assertTrue(sequentialBucketGasUsed.isPresent());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialBucketNumber));
        Assertions.assertEquals(gasUsedByTx, (long) sequentialBucketGasUsed.get());
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
    }

    @Test
    void ifItsSequentialTheEdgesListShouldHaveSizeZero() {
        handler.addRemascTransaction(tx, GasCost.toGas(bigTx.getGasLimit()));
        Assertions.assertEquals(0, handler.getTransactionsPerBucketInOrder().length);
    }

    @Test
    void callGetGasUsedInWithAnInvalidBucketShouldThrowAnError() {
        short invalidBucketId = (short) (buckets+1);
        try {
            handler.getGasUsedIn(invalidBucketId);
            Assertions.fail();
        } catch (NoSuchElementException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test
    void callGetGasUsedInWithAnInvalidBucketShouldThrowAnError2() {
        short invalidBucketId = -1;
        try {
            handler.getGasUsedIn(invalidBucketId);
            Assertions.fail();
        } catch (NoSuchElementException e) {
            Assertions.assertTrue(true);
        }
    }
    private HashSet<ByteArrayWrapper> createASetAndAddKeys(ByteArrayWrapper... aKey) {
        return new HashSet<>(Arrays.asList(aKey));
    }

    private void assertTwoTransactionsWereAddedProperlyIntoTheBuckets(Transaction tx, Transaction tx2, short[] expectedTransactionEdgeList) {
        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerBucketInOrder());
    }
}
