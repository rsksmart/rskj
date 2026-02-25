/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package org.ethereum.util;

import java.math.BigInteger;

import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.TransactionArguments;
import org.ethereum.core.TransactionType;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;

import static org.junit.jupiter.api.Assertions.*;

class TransactionArgumentsUtilTest {

    @Test
    void processArguments() {

        Constants constants = Constants.regtest();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);

        TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, null, wallet.getAccount(sender), constants.getChainId());

        assertEquals(txArgs.getValue(), BigInteger.valueOf(100000L));
        assertEquals(txArgs.getGasPrice(), BigInteger.valueOf(10000000000000L));
        assertEquals(txArgs.getGasLimit(), BigInteger.valueOf(30400L));
        assertEquals(33, txArgs.getChainId());
        assertEquals(BigInteger.ONE, txArgs.getNonce());
        assertNull(txArgs.getData());
        assertArrayEquals(txArgs.getTo(), receiver.getBytes());

    }

    @Test
    void testProcessArguments_txTypeInHexWithSingleDigit_executesAsExpected() {
        // Given

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);

        args.setType("0x0");

        // When
        TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, null, wallet.getAccount(sender), Constants.REGTEST_CHAIN_ID);

        // Then
        assertEquals(TransactionType.LEGACY, txArgs.getType());
    }

    @Test
    void testProcessArguments_txTypeInHexWithDoubleDigits_executesAsExpected() {
        // Given

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);

        args.setType("0x00");

        // When
        TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, null, wallet.getAccount(sender), Constants.REGTEST_CHAIN_ID);

        // Then
        assertEquals(TransactionType.LEGACY, txArgs.getType());
    }

    @Test
    void testProcessArguments_outOfRangeTxType_executesAsExpected() {
        // Given
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();
        Account senderAccount = wallet.getAccount(sender);

        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);

        args.setType("0xFF");

        // Then
        try {
            TransactionArgumentsUtil.processArguments(args, null, senderAccount, Constants.REGTEST_CHAIN_ID);
            fail("RskJsonRpcRequestException should be thrown!");
        } catch (RskJsonRpcRequestException ex) {
            assertEquals(-32602, ex.getCode());
            assertEquals("Invalid transaction type: 0xFF", ex.getMessage());
        }

    }

    @Test
    void testProcessArguments_invalidHexTxType_executesAsExpected() {
        // Given
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();
        Account senderAccount = wallet.getAccount(sender);

        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);

        args.setType("0x00FF");

        // Then
        try {
            TransactionArgumentsUtil.processArguments(args, null, senderAccount, Constants.REGTEST_CHAIN_ID);
            fail("RskJsonRpcRequestException should be thrown!");
        } catch (RskJsonRpcRequestException ex) {
            assertEquals(-32602, ex.getCode());
            assertEquals("Invalid transaction type: 0x00FF", ex.getMessage());
        }

    }

}
