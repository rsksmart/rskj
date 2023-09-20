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
package co.rsk.rpc.modules.eth;

import co.rsk.config.BridgeConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.core.bc.PendingState;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.TransactionGateway;
import co.rsk.bridge.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.program.ProgramResult;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EthModuleTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

    @Test
    void callSmokeTest() {
        CallArguments args = new CallArguments();
        ExecutionBlockRetriever.Result blockResult = mock(ExecutionBlockRetriever.Result.class);
        Block block = mock(Block.class);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.retrieveExecutionBlock("latest"))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(block);

        byte[] hReturn = HexUtils.stringToByteArray("hello");
        ProgramResult executorResult = mock(ProgramResult.class);
        when(executorResult.getHReturn())
                .thenReturn(hReturn);

        ReversibleTransactionExecutor executor = mock(ReversibleTransactionExecutor.class);
        when(executor.executeTransaction(eq(blockResult.getBlock()), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(executorResult);

        EthModule eth = new EthModule(
                null,
                anyByte(),
                null,
                null,
                executor,
                retriever,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        String expectedResult = HexUtils.toUnformattedJsonHex(hReturn);
        String actualResult = eth.call(args, "latest");

        assertEquals(expectedResult, actualResult);
    }

    @Test
    void callWithoutReturn() {
        CallArguments args = new CallArguments();
        ExecutionBlockRetriever.Result blockResult = mock(ExecutionBlockRetriever.Result.class);
        Block block = mock(Block.class);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.retrieveExecutionBlock("latest"))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(block);

        byte[] hReturn = new byte[0];
        ProgramResult executorResult = mock(ProgramResult.class);
        when(executorResult.getHReturn())
                .thenReturn(hReturn);

        ReversibleTransactionExecutor executor = mock(ReversibleTransactionExecutor.class);
        when(executor.executeTransaction(eq(blockResult.getBlock()), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(executorResult);

        EthModule eth = new EthModule(
                null,
                anyByte(),
                null,
                null,
                executor,
                retriever,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        String expectedResult = HexUtils.toUnformattedJsonHex(hReturn);
        String actualResult = eth.call(args, "latest");

        assertEquals(expectedResult, actualResult);
    }

    @Test
    void test_revertedTransaction() {
        CallArguments args = new CallArguments();
        ExecutionBlockRetriever.Result blockResult = mock(ExecutionBlockRetriever.Result.class);
        Block block = mock(Block.class);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.retrieveExecutionBlock("latest"))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(block);

        byte[] hreturn = Hex.decode(
                "08c379a000000000000000000000000000000000000000000000000000000000" +
                        "0000002000000000000000000000000000000000000000000000000000000000" +
                        "0000000f6465706f73697420746f6f2062696700000000000000000000000000" +
                        "00000000");
        ProgramResult executorResult = mock(ProgramResult.class);
        when(executorResult.isRevert()).thenReturn(true);
        when(executorResult.getHReturn())
                .thenReturn(hreturn);

        ReversibleTransactionExecutor executor = mock(ReversibleTransactionExecutor.class);
        when(executor.executeTransaction(eq(blockResult.getBlock()), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(executorResult);

        EthModule eth = new EthModule(
                null,
                anyByte(),
                null,
                null,
                executor,
                retriever,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        try {
            eth.call(args, "latest");
        } catch (RskJsonRpcRequestException e) {
            assertThat(e.getMessage(), Matchers.containsString("deposit too big"));
        }
    }

    @Test
    void sendTransactionWithGasLimitTest() {

        Constants constants = Constants.regtest();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        Transaction tx = TransactionFactoryHelper.createTransaction(args, constants.getChainId(), wallet.getAccount(sender));
        String txExpectedResult = tx.getHash().toJsonString();

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        when(transactionGateway.receiveTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        TransactionPool transactionPool = mock(TransactionPool.class);

        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool, transactionGateway);

        // Hash of the actual transaction builded inside the sendTransaction
        String txResult = ethModuleTransaction.sendTransaction(args);

        assertEquals(txExpectedResult, txResult);
    }

    @Test
    void sendTransactionThrowsErrorOnChainIdValidationTest() {

        Constants constants = Constants.regtest();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setChainId("" + ((int) constants.getChainId() - 2));

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        when(transactionGateway.receiveTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        TransactionPool transactionPool = mock(TransactionPool.class);

        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool, transactionGateway);

        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> ethModuleTransaction.sendTransaction(args));
    }

    @Test
    void sendRawTransactionThrowsErrorOnChainIdValidationTest() {

        Constants constants = Constants.regtest();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setChainId("" + ((int) constants.getChainId() - 2));

        Transaction tx = TransactionFactoryHelper.createTransaction(args, (byte) ((int) constants.getChainId() - 1), wallet.getAccount(sender));

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        when(transactionGateway.receiveTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        TransactionPool transactionPool = mock(TransactionPool.class);

        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool, transactionGateway);

        String rawData = ByteUtil.toHexString(tx.getEncoded());
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> ethModuleTransaction.sendRawTransaction(rawData));
    }

    @Test
    void sendTransaction_invalidSenderAccount_throwsRskJsonRpcRequestException() {
        // Given
        Constants constants = Constants.regtest();
        Wallet wallet = new Wallet(new HashMapDB());
        TransactionPool transactionPoolMock = mock(TransactionPool.class);
        TransactionGateway transactionGatewayMock = mock(TransactionGateway.class);

        CallArguments argsMock = mock(CallArguments.class);
        RskAddress addressFrom = new RskAddress(new ECKey().getAddress());
        doReturn(addressFrom.toJsonString()).when(argsMock).getFrom(); // Address not in wallet

        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPoolMock, transactionGatewayMock);

        // Then
        try {
            ethModuleTransaction.sendTransaction(argsMock);
            fail("RskJsonRpcRequestException should be thrown");
        } catch (RskJsonRpcRequestException ex) {
            verify(argsMock, times(2)).getFrom();
            assertEquals("Could not find account for address: " + addressFrom.toJsonString(), ex.getMessage());
        }
    }

    @Test
    void getCode() {
        byte[] expectedCode = new byte[]{1, 2, 3};

        TransactionPool mockTransactionPool = mock(TransactionPool.class);
        PendingState mockPendingState = mock(PendingState.class);

        doReturn(expectedCode).when(mockPendingState).getCode(any(RskAddress.class));
        doReturn(mockPendingState).when(mockTransactionPool).getPendingState();

        EthModule eth = new EthModule(
                null,
                (byte) 0,
                null,
                mockTransactionPool,
                null,
                null,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null,
                        null,
                        null,
                        signatureCache
                ),
                config.getGasEstimationCap(),
                config.getCallGasCap()
        );

        String addr = eth.getCode(TestUtils.generateAddress("addr").toHexString(), "pending");
        MatcherAssert.assertThat(Hex.decode(addr.substring("0x".length())), is(expectedCode));
    }

    @Test
    void chainId() {
        EthModule eth = new EthModule(
                mock(BridgeConstants.class),
                (byte) 33,
                mock(Blockchain.class),
                mock(TransactionPool.class),
                mock(ReversibleTransactionExecutor.class),
                mock(ExecutionBlockRetriever.class),
                mock(RepositoryLocator.class),
                mock(EthModuleWallet.class),
                mock(EthModuleTransaction.class),
                mock(BridgeSupportFactory.class),
                config.getGasEstimationCap(),
                config.getCallGasCap()
        );
        assertThat(eth.chainId(), is("0x21"));
    }
}
