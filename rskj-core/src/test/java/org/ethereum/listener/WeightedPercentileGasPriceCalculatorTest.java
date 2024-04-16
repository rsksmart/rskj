/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.listener;

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeightedPercentileGasPriceCalculatorTest {

    private static final int WINDOW_SIZE = 512;
    private WeightedPercentileGasPriceCalculator weightedPercentileGasPriceCalculator;


    @BeforeEach
    void setup() {
        weightedPercentileGasPriceCalculator = new WeightedPercentileGasPriceCalculator();
    }

    @Test
    void testCalculateWeightedPercentileWithNoTransactions() {
        // Test when no transactions are added
        assertNotNull(weightedPercentileGasPriceCalculator);
        assertFalse(weightedPercentileGasPriceCalculator.getGasPrice().isPresent(), "Gas price should not be present when no transactions are added");
    }

    @Test
    void testCalculateGasPriceWithZeroTotalGasUsed() {
        // Test when the total gas used is zero
        Block mockBlock = Mockito.mock(Block.class);

        TransactionReceipt mockReceipt = Mockito.mock(TransactionReceipt.class);
        Transaction mockTransaction = Mockito.mock(Transaction.class);
        when(mockTransaction.getGasPrice()).thenReturn(new Coin(BigInteger.valueOf(100)));
        when(mockReceipt.getTransaction()).thenReturn(mockTransaction);
        when(mockReceipt.getGasUsed()).thenReturn(BigInteger.ZERO.toByteArray());

        weightedPercentileGasPriceCalculator.onBlock(mockBlock, Collections.singletonList(mockReceipt));

        Optional<Coin> gasPrice = weightedPercentileGasPriceCalculator.getGasPrice();
        assertFalse(gasPrice.isPresent(), "Gas price should not be present when total gas used is zero");
    }

    @Test
    void testCalculateGasPriceWithSingleTransaction() {
        // Test when a single transaction is added
        Block mockBlock = Mockito.mock(Block.class);

        TransactionReceipt mockReceipt = Mockito.mock(TransactionReceipt.class);
        Transaction mockTransaction = Mockito.mock(Transaction.class);
        when(mockTransaction.getGasPrice()).thenReturn(new Coin(BigInteger.valueOf(100)));
        when(mockReceipt.getTransaction()).thenReturn(mockTransaction);
        when(mockReceipt.getGasUsed()).thenReturn(BigInteger.valueOf(500).toByteArray());

        weightedPercentileGasPriceCalculator.onBlock(mockBlock, Collections.singletonList(mockReceipt));

        Optional<Coin> gasPrice = weightedPercentileGasPriceCalculator.getGasPrice();
        assertTrue(gasPrice.isPresent(), "Gas price should be present when a transaction is added");
        assertEquals(new Coin(BigInteger.valueOf(100)), gasPrice.get(), "Gas price should be the same as the single transaction's gas price");
    }

    @Test
    void testCalculateGasPriceWithMultipleTransactionsSameGasUsage() {
        // Test when multiple transactions are added
        Block mockBlock = Mockito.mock(Block.class);

        TransactionReceipt mockReceipt1 = Mockito.mock(TransactionReceipt.class);
        Transaction mockTransaction1 = Mockito.mock(Transaction.class);
        when(mockTransaction1.getGasPrice()).thenReturn(new Coin(BigInteger.valueOf(100)));
        when(mockReceipt1.getTransaction()).thenReturn(mockTransaction1);
        when(mockReceipt1.getGasUsed()).thenReturn(BigInteger.valueOf(100).toByteArray());

        TransactionReceipt mockReceipt2 = Mockito.mock(TransactionReceipt.class);
        Transaction mockTransaction2 = Mockito.mock(Transaction.class);
        when(mockTransaction2.getGasPrice()).thenReturn(new Coin(BigInteger.valueOf(300)));
        when(mockReceipt2.getTransaction()).thenReturn(mockTransaction2);
        when(mockReceipt2.getGasUsed()).thenReturn(BigInteger.valueOf(300).toByteArray());

        TransactionReceipt mockReceipt3 = Mockito.mock(TransactionReceipt.class);
        Transaction mockTransaction3 = Mockito.mock(Transaction.class);
        when(mockTransaction3.getGasPrice()).thenReturn(new Coin(BigInteger.valueOf(200)));
        when(mockReceipt3.getTransaction()).thenReturn(mockTransaction3);
        when(mockReceipt3.getGasUsed()).thenReturn(BigInteger.valueOf(200).toByteArray());
        weightedPercentileGasPriceCalculator.onBlock(mockBlock, Arrays.asList(mockReceipt1, mockReceipt2, mockReceipt3));

        Optional<Coin> gasPrice = weightedPercentileGasPriceCalculator.getGasPrice();
        assertTrue(gasPrice.isPresent(), "Gas price should be present when multiple transactions are added");
        assertEquals(new Coin(BigInteger.valueOf(200)), gasPrice.get(), "Expecting 200 as weighted percentile for the provided set.");
    }

    @Test
    void testCalculateGasPriceWithPlainSet() {
        Block mockBlock = Mockito.mock(Block.class);
        List<TransactionReceipt> receipts = createMockReceipts(100, 1);
        weightedPercentileGasPriceCalculator.onBlock(mockBlock, receipts);
        Optional<Coin> gasPrice = weightedPercentileGasPriceCalculator.getGasPrice();
        assertTrue(gasPrice.isPresent(), "Gas price should be present when multiple transactions are added");
        assertEquals(new Coin(BigInteger.valueOf(25)), gasPrice.get(), "Gas price should be the weighted average of multiple transactions");
    }

    @Test
    void cacheValueIsNotUpdatedUntilWindowSizeIsReached() {
        WeightedPercentileCalc percentileCalc = new WeightedPercentileCalc();
        WeightedPercentileCalc spy = spy(percentileCalc);
        WeightedPercentileGasPriceCalculator gasPriceCalculator = new WeightedPercentileGasPriceCalculator(spy);

        Block mockBlock = Mockito.mock(Block.class);
        gasPriceCalculator.onBlock(mockBlock, createMockReceipts(10, 1));

        Optional<Coin> result1 = gasPriceCalculator.getGasPrice();
        assertTrue(result1.isPresent(), "Gas price should be present when multiple transactions are added");

        gasPriceCalculator.onBlock(mockBlock, createMockReceipts(WINDOW_SIZE - 20, 2));
        Optional<Coin> result2 = gasPriceCalculator.getGasPrice();
        assertTrue(result2.isPresent(), "Gas price should be present when multiple transactions are added");

        assertEquals(result1.get(), result2.get(), "Gas price is not updated if window threshold is not reached");
        verify(spy, times(1)).calculateWeightedPercentile(anyFloat(), anyList());

        gasPriceCalculator.onBlock(mockBlock, createMockReceipts(30, 1));
        Optional<Coin> result3 = gasPriceCalculator.getGasPrice();
        assertTrue(result3.isPresent(), "Gas price should be present when multiple transactions are added");

        assertNotEquals(result1.get(), result3.get(), "Gas price is updated if window threshold is reached");
        verify(spy, times(2)).calculateWeightedPercentile(anyFloat(), anyList());
    }

    @Test
    void olderTxAreRemovedWhenWindowLimitIsReach() {
        Block mockBlock = Mockito.mock(Block.class);
        WeightedPercentileCalc mockPC = Mockito.mock(WeightedPercentileCalc.class);
        when(mockPC.calculateWeightedPercentile(anyFloat(), anyList())).thenReturn(new Coin(BigInteger.valueOf(1)));

        ArgumentCaptor<List<WeightedPercentileGasPriceCalculator.GasEntry>> captor = ArgumentCaptor.forClass(List.class);
        WeightedPercentileGasPriceCalculator gpc = new WeightedPercentileGasPriceCalculator(mockPC);

        //Transactions are added until window size limit
        gpc.onBlock(mockBlock, createMockReceipts(WINDOW_SIZE, 1));
        gpc.getGasPrice();

        //New transactions are added to reach the window limit and re-calculate gas
        TransactionReceipt mockReceipt = Mockito.mock(TransactionReceipt.class);
        Transaction mockTransaction = Mockito.mock(Transaction.class);
        when(mockTransaction.getGasPrice()).thenReturn(new Coin(BigInteger.valueOf(850)));
        when(mockReceipt.getTransaction()).thenReturn(mockTransaction);
        when(mockReceipt.getGasUsed()).thenReturn(BigInteger.valueOf(1).toByteArray());
        gpc.onBlock(mockBlock, Collections.singletonList(mockReceipt));
        gpc.getGasPrice();

        verify(mockPC, times(2)).calculateWeightedPercentile(anyFloat(), captor.capture());
        List<List<WeightedPercentileGasPriceCalculator.GasEntry>> gasPriceList = captor.getAllValues();

        List<WeightedPercentileGasPriceCalculator.GasEntry> firstList = gasPriceList.get(0);
        Coin firstValueFirstList = firstList.get(0).getGasPrice();

        assertEquals(new Coin(BigInteger.valueOf(1)), firstValueFirstList, "Gas price should be the same as the first transaction's gas price");

        List<WeightedPercentileGasPriceCalculator.GasEntry> secondList = gasPriceList.get(1);
        //The second time the getGasPrice is called the first transaction should be removed and the new one added at the bottom
        assertEquals(new Coin(BigInteger.valueOf(850)), secondList.get(secondList.size() - 1).getGasPrice(), "Gas price should be the same as the first transaction's gas price");
        assertEquals(firstList.subList(1, firstList.size() - 1), secondList.subList(0, secondList.size() - 2), "The first list should be the same as the second list without the first and last element");
    }

    private List<TransactionReceipt> createMockReceipts(int numOfReceipts, int gasUsed) {
        List<TransactionReceipt> receipts = new ArrayList<>();
        for (int i = 0; i < numOfReceipts; i++) {
            TransactionReceipt mockReceipt = Mockito.mock(TransactionReceipt.class);
            Transaction mockTransaction = Mockito.mock(Transaction.class);
            when(mockTransaction.getGasPrice()).thenReturn(new Coin(BigInteger.valueOf(1 + i)));
            when(mockReceipt.getTransaction()).thenReturn(mockTransaction);
            when(mockReceipt.getGasUsed()).thenReturn(BigInteger.valueOf(gasUsed).toByteArray());
            receipts.add(mockReceipt);
        }
        return receipts;
    }

}