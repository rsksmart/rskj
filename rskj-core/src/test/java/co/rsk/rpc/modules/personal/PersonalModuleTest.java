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

package co.rsk.rpc.modules.personal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.core.bc.PendingState;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.util.TransactionFactoryHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;

class PersonalModuleTest {

    private static final String PASS_FRASE = "passfrase";

    @Test
    void sendTransactionWithGasLimitTest() throws Exception {

        TestSystemProperties props = new TestSystemProperties();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount(PASS_FRASE);
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);
        Transaction tx = TransactionFactoryHelper.createTransaction(args, props.getNetworkConstants().getChainId(), wallet.getAccount(sender, PASS_FRASE));
        String txExpectedResult = tx.getHash().toJsonString();

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        Ethereum ethereum = mock(Ethereum.class);
        when(ethereum.submitTransaction(tx)).thenReturn(transactionPoolAddResult);

        TransactionPool transactionPool = mock(TransactionPool.class);
        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(props, ethereum, wallet, transactionPool);

        // Hash of the actual transaction builded inside the sendTransaction
        String txResult = personalModuleWalletEnabled.sendTransaction(argsParam, PASS_FRASE);

        assertEquals(txExpectedResult, txResult);
    }

    @Test
    void sendTransactionThrowsErrorOnChainIdValidationTest() {

        TestSystemProperties props = new TestSystemProperties();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount(PASS_FRASE);
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setChainId("" + ((int) props.getNetworkConstants().getChainId() - 2));
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        Ethereum ethereum = mock(Ethereum.class);

        TransactionPool transactionPool = mock(TransactionPool.class);
        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(props, ethereum, wallet, transactionPool);

        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> personalModuleWalletEnabled.sendTransaction(argsParam, PASS_FRASE));
    }

    @Test
    void sendTransaction_whenNoNonceProvided_usesNonceFromPendingState() throws Exception {
        TestSystemProperties props = new TestSystemProperties();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount(PASS_FRASE);
        RskAddress receiver = wallet.addAccount();

        // No nonce set — supplier must be invoked
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setNonce(null);
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        BigInteger expectedNonce = BigInteger.TEN;

        PendingState pendingState = mock(PendingState.class);
        when(pendingState.getNonce(sender)).thenReturn(expectedNonce);

        TransactionPool transactionPool = mock(TransactionPool.class);
        when(transactionPool.getPendingState()).thenReturn(pendingState);

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        Ethereum ethereum = mock(Ethereum.class);
        when(ethereum.submitTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(props, ethereum, wallet, transactionPool);
        personalModuleWalletEnabled.sendTransaction(argsParam, PASS_FRASE);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(ethereum).submitTransaction(captor.capture());
        BigInteger actualNonce = new BigInteger(1, captor.getValue().getNonce());
        assertEquals(expectedNonce, actualNonce);
    }

    @Test
    void sendTransaction_whenNoNonceProvidedAndNonceIsAboveSingleHexDigit_nonceIsCorrect() throws Exception {
        // Nonce 16 in decimal is "10" in hex. Without a 0x prefix, strHexOrStrNumberToBigInteger
        // treats the string as decimal, so toString(16) would silently produce nonce 10 instead of 16.
        TestSystemProperties props = new TestSystemProperties();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount(PASS_FRASE);
        RskAddress receiver = wallet.addAccount();

        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setNonce(null);
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        BigInteger expectedNonce = BigInteger.valueOf(16);

        PendingState pendingState = mock(PendingState.class);
        when(pendingState.getNonce(sender)).thenReturn(expectedNonce);

        TransactionPool transactionPool = mock(TransactionPool.class);
        when(transactionPool.getPendingState()).thenReturn(pendingState);

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        Ethereum ethereum = mock(Ethereum.class);
        when(ethereum.submitTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(props, ethereum, wallet, transactionPool);
        personalModuleWalletEnabled.sendTransaction(argsParam, PASS_FRASE);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(ethereum).submitTransaction(captor.capture());
        BigInteger actualNonce = new BigInteger(1, captor.getValue().getNonce());
        assertEquals(expectedNonce, actualNonce);
    }
}
