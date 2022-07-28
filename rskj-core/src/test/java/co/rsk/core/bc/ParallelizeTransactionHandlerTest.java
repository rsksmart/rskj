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

    private short sublists;
    private ParallelizeTransactionHandler handler;
    private Transaction tx;
    private Transaction tx2;
    private Transaction tx3;
    private ByteArrayWrapper aWrappedKey;
    private ByteArrayWrapper aDifferentWrapperKey;
    private Transaction bigTx;
    private Transaction bigTx2;
    private short sequentialSublistNumber;

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
        long biggestGasLimitPossibleInSublists = blockGasLimit - 1;

        aWrappedKey = new ByteArrayWrapper(aKey);
        sublists = 2;
        sequentialSublistNumber = sublists;
        handler = new ParallelizeTransactionHandler(sublists, blockGasLimit, blockGasLimit);
        tx = new TransactionBuilder().nonce(1).sender(sender).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx2 = new TransactionBuilder().nonce(1).sender(sender2).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        tx3 = new TransactionBuilder().nonce(1).sender(sender3).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(gasUsedByTx)).build();
        bigTx = new TransactionBuilder().nonce(1).sender(sender4).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInSublists)).value(BigInteger.valueOf(1)).build();
        bigTx2 = new TransactionBuilder().nonce(1).sender(sender5).gasLimit(BigInteger.valueOf(biggestGasLimitPossibleInSublists)).value(BigInteger.valueOf(1)).build();
        aDifferentWrapperKey = new ByteArrayWrapper(aDifferentKey);
    }

    @Test
    public void createAHandlerShouldReturnAnEmptyTransactionList() {
        int expectedNumberOfTxs = 0;
        int expectedNumberOfTxsInSublists = 0;

        assertEquals(expectedNumberOfTxs, handler.getTransactionsInOrder().size());
        assertEquals(expectedNumberOfTxsInSublists, handler.getTransactionsPerSublistInOrder().length);
    }

    @Test
    public void createAHandlerAndGasUsedInSublistsShouldBeZero() {
        int expectedGasUsed = 0;
        for (short i = 0; i < sublists; i++) {
            assertEquals(expectedGasUsed, handler.getGasUsedIn(i));
        }
    }

    @Test
    public void addTransactionIntoTheHandlerAndShouldBeAddedInTheFirstParallelSublist() {
        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), 0, 0);
        short[] expectedTransactionEdgeList = new short[]{1};
        long expectedGasUsed = 0;

        assertTrue(sublistGasUsed.isPresent());
        assertEquals(expectedGasUsed, (long) sublistGasUsed.get());

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(tx);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void addTransactionIntoTheHandlerAndShouldBeSubtractedGasUsedInTheSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
    }

    @Test
    public void addTwoTransactionsWithTheSameReadKeyAndShouldBeAddedInADifferentSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        Set<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentReadKeysShouldBeAddedInADifferentSublist() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx2, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithSameWrittenKeysShouldBeAddedInTheSameSublist() {
        short[] expectedTransactionEdgeList = new short[]{2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx+gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentWrittenKeysShouldBeAddedInDifferentSublist() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx2, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithTheSameWrittenReadKeyShouldBeAddedInTheSameSublist() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx+gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithTheSameReadWrittenKeyShouldBeAddedInTheSameSublist() {
        short[] expectedTransactionEdgeList = new short[]{2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx+gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithDifferentReadWrittenKeysShouldBeAddedInDifferentSublists() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionWithDifferentWrittenReadKeyShouldBeAddedInDifferentSublists() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, writtenKeys,  new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys, new HashSet<>(), gasUsedByTx2, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx2, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoIndependentTxsAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1, 2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> differentWrittenKeys = createASetAndAddKeys(aDifferentWrapperKey);

        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), differentWrittenKeys, gasUsedByTx2, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, differentWrittenKeys, writtenKeys, gasUsedByTx3, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(gasUsedByTx3, (long) sublistGasUsed3.get());
        assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void addTwoDependentTxsWithTheSecondInSequentialAndAThirdOneCollidingWithBothAndShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        long totalGasInSequential = gasUsedByTx2 + gasUsedByTx3;


        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2, 0);
        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(totalGasInSequential, (long) sublistGasUsed3.get());
        assertEquals(totalGasInSequential, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, tx2, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void addABigTransactionAndAnotherWithTheSameWrittenKeyAndTheLastOneShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void addABigTxAndAnotherWithTheSameReadWrittenKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, readKeys, new HashSet<>(), gasUsedByBigTx, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void addABigTxAndAnotherWithTheSameWrittenReadKeyAndShouldGoToSequential() {
        short[] expectedTransactionEdgeList = new short[]{1};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx, (long) sublistGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(bigTx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void addTwoTransactionsWithTheSameSenderToTheSequentialSublistAndTheSecondShouldBeAddedCorrectly() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, tx, tx);
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx.getGasLimit()), 0);
        handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), GasCost.toGas(bigTx2.getGasLimit()), 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);
        assertTrue(sublistGasUsed3.isPresent());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertEquals(gasUsedByTx, (long) sublistGasUsed3.get());

        Optional<Long> sublistGasUsed4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);
        assertTrue(sublistGasUsed4.isPresent());
        assertEquals(2*gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertEquals(2*gasUsedByTx, (long) sublistGasUsed4.get());

        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void twoTransactionWithTheSameSenderShouldBeInTheSameSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(2*gasUsedByTx, (long) sublistGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void ifATxHasTheSameSenderThatAnotherAlreadyAddedIntoTheSequentialShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrapperKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrapperKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx, 0);
        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, readKeys, new HashSet<>(), gasUsedByTx3, 0);
        Optional<Long> sublistGasUsed4 = handler.addTransaction(tx3, new HashSet<>(), new HashSet<>(), gasUsedByTx3, 0);
        assertTrue(sublistGasUsed3.isPresent() && sublistGasUsed4.isPresent());
        assertEquals(gasUsedByTx3*2, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifATxReadTwoDifferentWrittenKeysShouldGoToSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys2 = createASetAndAddKeys(aDifferentWrapperKey);
        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrapperKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys2, gasUsedByTx, 0);
        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, readKeys, new HashSet<>(), gasUsedByTx3, 0);
        assertTrue(sublistGasUsed3.isPresent());
        assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifATxWritesAKeyAlreadyReadByTwoTxsPlacedInDifferentSublistsShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx, 0);
        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3, 0);
        assertTrue(sublistGasUsed3.isPresent());
        assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifATxReadTwoKeysThatAreInDifferentSublistsShouldGoToTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx3 = GasCost.toGas(tx3.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        HashSet<ByteArrayWrapper> readKeys = createASetAndAddKeys(aWrappedKey);
        HashSet<ByteArrayWrapper> readKeys2 = createASetAndAddKeys(aDifferentWrapperKey);
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey, aDifferentWrapperKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, readKeys, new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, readKeys2, new HashSet<>(), gasUsedByTx, 0);
        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx3, new HashSet<>(), writtenKeys, gasUsedByTx3, 0);
        assertTrue(sublistGasUsed3.isPresent());
        assertEquals(gasUsedByTx3, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx3);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifATxCollidesWithAnotherOneThatAlsoHasTheSameSenderShouldGoIntoTheSameSublist() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(2*gasUsedByTx, (long) sublistGasUsed2.get());
        assertTwoTransactionsWereAddedProperlyIntoTheSublist(tx, tx, expectedTransactionEdgeList);
    }

    @Test
    public void ifATransactionHasAnAlreadyAddedSenderButCollidesWithAnotherTxShouldBeAddedIntoTheSequential() {
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        long gasUsedByTx2 = GasCost.toGas(tx2.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(tx2, new HashSet<>(), writtenKeys, gasUsedByTx2, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx, new HashSet<>(), writtenKeys, gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());
        assertEquals(gasUsedByTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByTx2, (long) sublistGasUsed2.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));

        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2, tx);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifANewTxComesAndAllThePossibleSublistAreFullTheTxShouldNotBeAdded() {
        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(bigTx);

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx, 0);
        Optional<Long> sublistGasUsed4 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);

        assertFalse(sublistGasUsed4.isPresent());
        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());

        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed3.get());
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifSublistsAreFullAndAnIndependentTxComesShouldBeAddedInTheSequential() {
        short[] expectedTransactionEdgeList = new short[]{1,2};

        List<Transaction> expectedListOfTxs = new ArrayList<>();
        expectedListOfTxs.add(bigTx);
        expectedListOfTxs.add(bigTx2);
        expectedListOfTxs.add(tx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        long gasUsedByTx = GasCost.toGas(tx.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), gasUsedByTx, 0);

        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent() && sublistGasUsed3.isPresent());

        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        assertEquals(gasUsedByTx, (long) sublistGasUsed3.get());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifAllTheSublistsAreFullTheNewIndependentTxShouldNotBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, bigTx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx, 0);
        assertTrue(sublistGasUsed3.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed3.get());
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> emptySublist = handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), GasCost.toGas(tx.getGasLimit()), 0);
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertFalse(emptySublist.isPresent());
        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void ifAllTheSublistsAreFullTheNewDependentTxShouldNotBeIncluded() {
        short[] expectedTransactionEdgeList = new short[]{1,2};
        List<Transaction> expectedListOfTxs = Arrays.asList(bigTx, bigTx2, bigTx);

        long gasUsedByBigTx = GasCost.toGas(bigTx.getGasLimit());
        long gasUsedByBigTx2 = GasCost.toGas(bigTx2.getGasLimit());
        HashSet<ByteArrayWrapper> writtenKeys = createASetAndAddKeys(aWrappedKey);

        Optional<Long> sublistGasUsed = handler.addTransaction(bigTx, new HashSet<>(), writtenKeys, gasUsedByBigTx, 0);
        Optional<Long> sublistGasUsed2 = handler.addTransaction(bigTx2, new HashSet<>(), new HashSet<>(), gasUsedByBigTx2, 0);
        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> sublistGasUsed3 = handler.addTransaction(bigTx, new HashSet<>(), new HashSet<>(), gasUsedByBigTx, 0);
        assertTrue(sublistGasUsed3.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed3.get());
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialSublistNumber));

        Optional<Long> emptySublist = handler.addTransaction(tx, new HashSet<>(), writtenKeys, GasCost.toGas(tx.getGasLimit()), 0);
        assertEquals(gasUsedByBigTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertFalse(emptySublist.isPresent());
        assertTrue(sublistGasUsed.isPresent() && sublistGasUsed2.isPresent());
        assertEquals(gasUsedByBigTx, (long) sublistGasUsed.get());
        assertEquals(gasUsedByBigTx2, (long) sublistGasUsed2.get());
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }

    @Test
    public void aRemascTxAddedShouldBeInTheSequentialSublist() {
        List<Transaction> expectedListOfTxs = Collections.singletonList(tx);
        long gasUsedByTx = GasCost.toGas(bigTx.getGasLimit());

        assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber));
        Optional<Long> sequentialSublistGasUsed = handler.addRemascTransaction(tx, gasUsedByTx);

        assertTrue(sequentialSublistGasUsed.isPresent());
        assertEquals(gasUsedByTx, handler.getGasUsedIn(sequentialSublistNumber));
        assertEquals(gasUsedByTx, (long) sequentialSublistGasUsed.get());
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
    }

    @Test
    public void testingTxsDistribution() {
        for (int i = 0; i < 40; i++) {
            Account sender = new AccountBuilder().name(String.valueOf(i)).build();
            Account receiver = new AccountBuilder().name(String.valueOf(i+41)).build();
            Transaction tx = new TransactionBuilder().receiver(receiver).nonce(1).sender(sender).value(BigInteger.valueOf(1)).gasLimit(BigInteger.valueOf(1)).build();
            handler.addTransaction(tx, new HashSet<>(), new HashSet<>(), 1, 0);
        }
        System.out.println(handler.getGasPerSublist());
        System.out.println(handler.getTransactionsInOrder());
    }

    @Test
    public void ifItsSequentialTheEdgesListShouldHaveSizeZero() {
        handler.addRemascTransaction(tx, GasCost.toGas(bigTx.getGasLimit()));
        assertEquals(0, handler.getTransactionsPerSublistInOrder().length);
    }

    @Test
    public void callGetGasUsedInWithAnInvalidSublistShouldThrowAnError() {
        short invalidSublistId = (short) (sublists +1);
        try {
            handler.getGasUsedIn(invalidSublistId);
            fail();
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    @Test
    public void callGetGasUsedInWithAnInvalidSublistShouldThrowAnError2() {
        short invalidSublistId = -1;
        try {
            handler.getGasUsedIn(invalidSublistId);
            fail();
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }
    private HashSet<ByteArrayWrapper> createASetAndAddKeys(ByteArrayWrapper... aKey) {
        return new HashSet<>(Arrays.asList(aKey));
    }

    private void assertTwoTransactionsWereAddedProperlyIntoTheSublist(Transaction tx, Transaction tx2, short[] expectedTransactionEdgeList) {
        List<Transaction> expectedListOfTxs = Arrays.asList(tx, tx2);
        assertEquals(expectedListOfTxs, handler.getTransactionsInOrder());
        assertArrayEquals(expectedTransactionEdgeList, handler.getTransactionsPerSublistInOrder());
    }
}
