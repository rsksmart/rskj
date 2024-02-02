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
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class ParallelizeTransactionHandlerTest {

    private short sublists;
    private ParallelizeTransactionHandler handler;
    private Transaction tx;
    private Transaction tx2;
    private Transaction tx3;
    private ByteArrayWrapper aWrappedKey;
    private ByteArrayWrapper aDifferentWrappedKey;
    private Transaction bigTx;
    private Transaction bigTx2;
    private Transaction bigSequentialTx;
    private short sequentialSublistNumber;

    @BeforeEach
    public void setup() {
        long blockGasLimit = 8_160_000L;
        Block executionBlock = mock(Block.class);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(blockGasLimit).toByteArray());
        Account sender = new AccountBuilder().name("sender").build();
        Account sender2 = new AccountBuilder().name("sender2").build();
        Account sender3 = new AccountBuilder().name("sender3").build();
        Account sender4 = new AccountBuilder().name("sender4").build();
        Account sender5 = new AccountBuilder().name("sender5").build();
        Account sender6 = new AccountBuilder().name("sender6").build();
        byte[] aKey = {1, 2, 3};
        byte[] aDifferentKey = {1, 2, 3, 4};
        long gasUsedByTx = 16000;
        long biggestGasLimitPossibleInParallelSublists = BlockUtils.getSublistGasLimit(executionBlock, false) - 1;
        long biggestGasLimitPossibleInSequentialSublist = BlockUtils.getSublistGasLimit(executionBlock, true) - 1;

        aWrappedKey = new ByteArrayWrapper(aKey);
        sublists = 2;
        sequentialSublistNumber = sublists;
        handler = new ParallelizeTransactionHandler(sublists, executionBlock);
        tx = new TransactionBuilder().nonce(1).sender(sender).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx2 = new TransactionBuilder().nonce(1).sender(sender2).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx3 = new TransactionBuilder().nonce(1).sender(sender3).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        bigTx = new TransactionBuilder().nonce(1).sender(sender4).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInParallelSublists)).value(BigInteger.valueOf(1)).build();
        bigTx2 = new TransactionBuilder().nonce(1).sender(sender5).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInParallelSublists)).value(BigInteger.valueOf(1)).build();
        bigSequentialTx = new TransactionBuilder().nonce(1).sender(sender6).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInSequentialSublist)).value(BigInteger.valueOf(1)).build();
        aDifferentWrappedKey = new ByteArrayWrapper(aDifferentKey);
    }

    @Test
    void createAHandlerShouldReturnAnEmptyTransactionList() {
        int expectedNumberOfTxs = 0;
        int expectedNumberOfTxsInSublists = 0;
        Assertions.assertEquals(expectedNumberOfTxs, handler.getTransactionsInOrder().size());
        Assertions.assertEquals(expectedNumberOfTxsInSublists, handler.getTransactionsPerSublistInOrder().length);
    }

    @Test
    void createAHandlerAndGasUsedInBucketShouldBeZero() {
        int expectedGasUsed = 0;
        for (short i = 0; i < sublists; i++) {
            Assertions.assertEquals(expectedGasUsed, handler.getGasUsedIn(i));
        }
    }

    @Test
    void addTransactionIntoTheHandlerAndShouldBeAddedInTheFirstParallelSublist() {
        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), 0);
        short[] expectedTransactionEdgeList = new short[]{1};
        long expectedGasUsed = 0;

        Assertions.assertTrue(sublistGasUsed.isPresent());
        Assertions.assertEquals(expectedGasUsed, (long) sublistGasUsed.get());

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void addTransactionIntoTheHandlerAndShouldBeSubtractedGasUsedInTheSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(sublistGasUsed.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
    }

    @Test
    void addTwoTransactionsWithTheSameReadKeyAndShouldBeAddedInADifferentSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        Set<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithDifferentReadKeysShouldBeAddedInADifferentSublist() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aDifferentWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx2);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithSameWrittenKeysShouldBeAddedInTheSameSublist() {
        short[] expectedTransactionEdgeList = new short[]{2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx+gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithDifferentWrittenKeysShouldBeAddedInDifferentBuckets() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx2);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithTheSameWrittenReadKeyShouldBeAddedInTheSameSublist() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx+gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithTheSameReadWrittenKeyShouldBeAddedInTheSameSublist() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx+gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithDifferentReadWrittenKeysShouldBeAddedInDifferentSublists() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aDifferentWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionWithDifferentWrittenReadKeyShouldBeAddedInDifferentSublists() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aDifferentWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, writtenKeys,  new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    void addTwoIndependentTxsAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> differentWrittenKeys = createASetAndAddKeys(aDifferentWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), differentWrittenKeys, gasUsedByTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, differentWrittenKeys, writtenKeys, gasUsedByTx3);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx3, (long) sublistGasUsed3.get());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void addTwoDependentTxsWithTheSecondInSequentialAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        long totalGasInSequential = gasUsedByTx2 + gasUsedByTx3;


        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);
        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(totalGasInSequential, (long) sublistGasUsed3.get());
        Assertions.assertEquals(totalGasInSequential, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void addABigTransactionAndAnotherWithTheSameWrittenKeyAndTheLastOneShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    void addABigTxAndAnotherWithTheSameReadWrittenKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, readKeys, new HashSet<>(), gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    void addABigTxAndAnotherWithTheSameWrittenReadKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    void addTwoTransactionsWithTheSameSenderToTheSequentialSublistAndTheSecondShouldBeAddedCorrectly() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, tx, tx);
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()));
        handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx2.getGasLimit()));
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed3.get());

        Optional<Long> sublistGasUsed4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(sublistGasUsed4.isPresent());
        Assertions.assertEquals(2*gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(2*gasUsedByTx, (long) sublistGasUsed4.get());

        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void twoTransactionWithTheSameSenderShouldBeInTheSameSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(2*gasUsedByTx, (long) sublistGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    void ifATxHasTheSameSenderThatAnotherAlreadyAddedIntoTheSequentialShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrappedKey);


        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx);
        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, readKeys, new HashSet<>(), gasUsedByTx3);
        Optional<Long> sublistGasUsed4 = handler.addTransaction(tx3, new HashSet<>(), new HashSet<>(), gasUsedByTx3);
        Assertions.assertTrue(sublistGasUsed3.isPresent() && sublistGasUsed4.isPresent());
        Assertions.assertEquals(gasUsedByTx3*2, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void ifATxReadTwoDifferentKeysWrittenByDifferentTxsInDifferentSublistsShouldGoToSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx2);
        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, readKeys, new HashSet<>(), gasUsedByTx3);
        Assertions.assertTrue(sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void ifATxWritesAKeyAlreadyReadByTwoTxsInDifferentSublistsShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3);
        Assertions.assertTrue(sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }
    @Test
    void ifATxReadTwoKeysThatAreWereWrittenByTxsInDifferentSublistsShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aDifferentWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx);
        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3);
        Assertions.assertTrue(sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void ifATxCollidesWithAnotherOneThatAlsoHasTheSameSenderShouldGoIntoTheSameSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(2*gasUsedByTx, (long) sublistGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    void ifATxCollidesByTheSenderAndAKeyWithTwoTxsShouldBeAddedIntoTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void ifANewTxComesAndAllThePossibleSublistAreFullTheTxShouldNotBeAdded() {
        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByBigSequentialTx = GasCost.toGas(bigSequentialTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(bigSequentialTx);

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(bigSequentialTx, new HashSet<>(), new HashSet<>(), gasUsedByBigSequentialTx);
        Optional<Long> sublistGasUsed4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertFalse(sublistGasUsed4.isPresent());
        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());

        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(gasUsedByBigSequentialTx, (long) sublistGasUsed3.get());
        Assertions.assertEquals(gasUsedByBigSequentialTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void ifSublistsAreFullAndAnIndependentTxComesShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(tx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());

        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(gasUsedByTx, (long) sublistGasUsed3.get());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void ifAllTheSublistsAreFullTheNewIndependentTxShouldNotBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, bigSequentialTx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByBigSequentialTx = GasCost.toGas(bigSequentialTx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(bigSequentialTx, new HashSet<>(), new HashSet<>(), gasUsedByBigSequentialTx);
        Assertions.assertTrue(sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByBigSequentialTx, (long) sublistGasUsed3.get());
        Assertions.assertEquals(gasUsedByBigSequentialTx, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> emptySublist = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(tx.getGasLimit()));
        Assertions.assertEquals(gasUsedByBigSequentialTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertFalse(emptySublist.isPresent());
        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void ifAllTheSublistsAreFullTheNewDependentTxShouldNotBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, bigSequentialTx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByBigSequentialTx = GasCost.toGas(bigSequentialTx.getGasLimit());
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2);
        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(bigSequentialTx, new HashSet<>(), new HashSet<>(), gasUsedByBigSequentialTx);
        Assertions.assertTrue(sublistGasUsed3.isPresent());
        Assertions.assertEquals(gasUsedByBigSequentialTx, (long) sublistGasUsed3.get());
        Assertions.assertEquals(gasUsedByBigSequentialTx, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> emptySublist = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()));
        Assertions.assertEquals(gasUsedByBigSequentialTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertFalse(emptySublist.isPresent());
        Assertions.assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        Assertions.assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        Assertions.assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void aRemascTxAddedShouldBeInTheSequentialSublist() {
        List<Transaction> expectedListOfTxs = Collections.singletonList(tx);
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        Optional<Long> sequentialSublistGasUsed = handler.addRemascTransaction(tx, gasUsedByTx);

        Assertions.assertTrue(sequentialSublistGasUsed.isPresent());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByTx, (long) sequentialSublistGasUsed.get());
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
    }

    @Test
    void aTxDirectedToAPrecompiledContractAddedShouldBeInTheSequentialSublist() {
        List<Transaction> expectedListOfTxs = Collections.singletonList(bigSequentialTx);
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByBigSequentialTx = GasCost.toGas(bigSequentialTx.getGasLimit());

        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sequentialSublistGasUsedAfterBigTx = handler.addTxToSequentialSublist(bigSequentialTx, gasUsedByBigSequentialTx);
        Assertions.assertTrue(sequentialSublistGasUsedAfterBigTx.isPresent());
        Assertions.assertEquals(gasUsedByBigSequentialTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByBigSequentialTx, (long) sequentialSublistGasUsedAfterBigTx.get());

        Optional<Long> sequentialSublistGasUsedAfterTx = handler.addTxToSequentialSublist(tx, gasUsedByTx);
        Assertions.assertFalse(sequentialSublistGasUsedAfterTx.isPresent());

        Assertions.assertEquals(gasUsedByBigSequentialTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
    }

    @Test
    void aTxDirectedToAPrecompiledContractAddedWithSequentialSublistFullShouldNotBeAdded() {
        List<Transaction> expectedListOfTxs = Collections.singletonList(tx);
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        Optional<Long> sequentialSublistGasUsed = handler.addTxToSequentialSublist(tx, gasUsedByTx);

        Assertions.assertTrue(sequentialSublistGasUsed.isPresent());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByTx, (long) sequentialSublistGasUsed.get());
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
    }

    @Test
    void whenATxCallsAPrecompiledAnotherWithTheSameSenderShouldGoToSequential() {
        List<Transaction> expectedListOfTxsPreSecondTx = Collections.singletonList(tx);
        List<Transaction> expectedListOfTxsPostSecondTx = Arrays.asList(tx, tx);
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Assertions.assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        Optional<Long> sequentialSublistGasUsed = handler.addTxToSequentialSublist(tx, gasUsedByTx);

        Assertions.assertTrue(sequentialSublistGasUsed.isPresent());
        Assertions.assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByTx, (long) sequentialSublistGasUsed.get());
        Assertions.assertEquals(expectedListOfTxsPreSecondTx, handler.getTransactionsInOrder());

        Optional<Long> sequentialSublistGasUsedByTx2 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx);

        Assertions.assertTrue(sequentialSublistGasUsedByTx2.isPresent());
        Assertions.assertEquals(gasUsedByTx*2, handler.getGasUsedIn(sequentialSublistNumber));
        Assertions.assertEquals(gasUsedByTx*2, (long) sequentialSublistGasUsedByTx2.get());
        Assertions.assertEquals(expectedListOfTxsPostSecondTx, handler.getTransactionsInOrder());
    }

    @Test
    void ifItsSequentialTheEdgesListShouldHaveSizeZero() {
        handler.addRemascTransaction(tx, GasCost.toGas(bigTx.getGasLimit()));
        Assertions.assertEquals(0, handler.getTransactionsPerSublistInOrder().length);
    }

    @Test
    void callGetGasUsedInWithAnInvalidSublistShouldThrowAnError() {
        short invalidSublistId = (short) (sublists +1);
        try {
            handler.getGasUsedIn(invalidSublistId);
            Assertions.fail();
        } catch (NoSuchElementException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test
    void callGetGasUsedInWithAnInvalidSublistShouldThrowAnError2() {
        short invalidSublistId = -1;
        try {
            handler.getGasUsedIn(invalidSublistId);
            Assertions.fail();
        } catch (NoSuchElementException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test
    void senderWritesAKeyAndReadsAnotherThatIsWrittenShouldGoToSequential() {
        HashSet<ByteArrayWrapper> writeKeyX = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writeKeyY = createASetAndAddKeys(aDifferentWrappedKey);
        HashSet<ByteArrayWrapper> readKeyY = createASetAndAddKeys(aDifferentWrappedKey);

        Account senderA = new AccountBuilder().name("sender1").build();
        Account senderB = new AccountBuilder().name("sender2").build();

        Transaction a_writes_x = new TransactionBuilder().nonce(1).sender(senderA).value(BigInteger.valueOf(0)).gasLimit(BigInteger.valueOf(16000)).build();
        Transaction b_writes_y = new TransactionBuilder().nonce(1).sender(senderB).value(BigInteger.valueOf(0)).gasLimit(BigInteger.valueOf(16000)).build();
        Transaction a_reads_y = new TransactionBuilder().nonce(2).sender(senderA).value(BigInteger.valueOf(0)).gasLimit(BigInteger.valueOf(16000)).build();

        handler.addTransaction(a_writes_x, new HashSet<>(), writeKeyX, 1000);
        handler.addTransaction(b_writes_y, new HashSet<>(), writeKeyY, 1000);
        handler.addTransaction(a_reads_y, readKeyY, new HashSet<>(), 1000);

        Assertions.assertArrayEquals(new short[]{ 1, 2 }, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void senderWritesAKeyAndReadsAnotherThatIsWrittenShouldGoToSequentialIfReadingOtherKeys() {
        ByteArrayWrapper anotherKey = new ByteArrayWrapper(new byte[]{ 7, 7, 7 });
        HashSet<ByteArrayWrapper> writeKeyX = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writeKeyYAndAnother = createASetAndAddKeys(anotherKey, aDifferentWrappedKey);
        HashSet<ByteArrayWrapper> readKeyYAndAnother = createASetAndAddKeys(anotherKey, aDifferentWrappedKey);

        Account senderA = new AccountBuilder().name("sender1").build();
        Account senderB = new AccountBuilder().name("sender2").build();

        Transaction a_writes_x = new TransactionBuilder().nonce(1).sender(senderA).value(BigInteger.valueOf(0)).gasLimit(BigInteger.valueOf(16000)).build();
        Transaction b_writes_y = new TransactionBuilder().nonce(1).sender(senderB).value(BigInteger.valueOf(0)).gasLimit(BigInteger.valueOf(16000)).build();
        Transaction a_reads_y = new TransactionBuilder().nonce(2).sender(senderA).value(BigInteger.valueOf(0)).gasLimit(BigInteger.valueOf(16000)).build();

        handler.addTransaction(a_writes_x, new HashSet<>(), writeKeyX, 1000);
        handler.addTransaction(b_writes_y, new HashSet<>(), writeKeyYAndAnother, 1000);
        handler.addTransaction(a_reads_y, readKeyYAndAnother, new HashSet<>(), 1000);

        Assertions.assertArrayEquals(new short[]{ 1, 2 }, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void writeSequentialAfterTwoParallelReadsAndAWriteShouldGoToSequential() {
        HashSet<ByteArrayWrapper> setWithX = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> setWithY = createASetAndAddKeys(aDifferentWrappedKey);
        HashSet<ByteArrayWrapper> setWithXAndY = createASetAndAddKeys(aWrappedKey, aDifferentWrappedKey);

        AccountBuilder accountBuilder = new AccountBuilder();

        // read X
        handler.addTransaction(
                new TransactionBuilder().sender(accountBuilder.name("sender1").build()).build(),
                setWithX, new HashSet<>(), 10000
        );

        // read Y
        handler.addTransaction(
                new TransactionBuilder().sender(accountBuilder.name("sender2").build()).build(),
                setWithY, new HashSet<>(), 10000
        );

        // write X and Y
        handler.addTransaction(
                new TransactionBuilder().sender(accountBuilder.name("sender3").build()).build(),
                new HashSet<>(), setWithXAndY, 10000
        );

        // last write of X is in sequential
        Assertions.assertArrayEquals(new short[]{ 1, 2 }, handler.getTransactionsPerSublistInOrder());

        // write X
        handler.addTransaction(
                new TransactionBuilder().sender(accountBuilder.name("sender4").build()).build(),
                new HashSet<>(), setWithX, 10000
        );

        // should go to sequential
        // [[read x], [read y]] [write x and y, write x]
        Assertions.assertArrayEquals(new short[]{ 1, 2 }, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    void writeAfterWriteToSequentialForOutOfGasShouldGoToSequential() {
        Block executionBlock = mock(Block.class);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6_804_000L).toByteArray());

        HashSet<ByteArrayWrapper> setWithX = createASetAndAddKeys(aWrappedKey);

        AccountBuilder accountBuilder = new AccountBuilder();

        ParallelizeTransactionHandler handler = new ParallelizeTransactionHandler((short) 2, executionBlock);

        // write X with 800
        handler.addTransaction(
                new TransactionBuilder()
                        .sender(accountBuilder.name("sender1").build())
                        .gasLimit(BigInteger.valueOf(800))
                        .build(),
                new HashSet<>(), setWithX, 800
        );

        // write X with 300
        handler.addTransaction(
                new TransactionBuilder()
                        .sender(accountBuilder.name("sender2").build())
                        .gasLimit(BigInteger.valueOf(300))
                        .build(),
                new HashSet<>(), setWithX, 300
        );

        // last write of X is in sequential because of out of gas. 800 + 300 > 1000
        Assertions.assertArrayEquals(new short[]{ 1 }, handler.getTransactionsPerSublistInOrder());

        // read X with 100
        handler.addTransaction(
                new TransactionBuilder()
                        .sender(accountBuilder.name("sender3").build())
                        .gasLimit(BigInteger.valueOf(100))
                        .build(),
                setWithX, new HashSet<>(), 100
        );

        // should go to sequential
        Assertions.assertArrayEquals(new short[]{ 1 }, handler.getTransactionsPerSublistInOrder());
    }

    private HashSet<ByteArrayWrapper> createASetAndAddKeys(ByteArrayWrapper... aKey) {
        return new HashSet<>(Arrays.asList(aKey));
    }

    private void assertTwoTransactionsWereAddedProperlyIntoTheSublist(Transaction tx, Transaction tx2, short[] expectedTransactionEdgeList) {
        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2);
        Assertions.assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        Assertions.assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }
}
