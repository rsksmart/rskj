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
import co.rsk.peg.BridgeSupportFactory;
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
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.program.ProgramResult;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class EthModuleTest {

    public static final String TEST_DATA = "0x603d80600c6000396000f3007c01000000000000000000000000000000000000000000000000000000006000350463c6888fa18114602d57005b6007600435028060005260206000f3";
    
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
        String actualResult = eth.call(new CallArgumentsParam(args), new BlockIdentifierParam("latest"));

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
        String actualResult = eth.call(new CallArgumentsParam(args), new BlockIdentifierParam("latest"));

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
            eth.call(new CallArgumentsParam(args), new BlockIdentifierParam("latest"));
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
        String txResult = ethModuleTransaction.sendTransaction(new CallArgumentsParam(args));

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

        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> ethModuleTransaction.sendTransaction(new CallArgumentsParam(args)));
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
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> ethModuleTransaction.sendRawTransaction(new HexDataParam(rawData)));
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
            ethModuleTransaction.sendTransaction(new CallArgumentsParam(argsMock));
            fail("RskJsonRpcRequestException should be thrown");
        } catch (RskJsonRpcRequestException ex) {
            verify(argsMock, times(4)).getFrom();
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

    @Test
    void whenExecuteCallWithDataParameter_callExecutorWithData() {
        CallArguments args = new CallArguments();
        args.setData(TEST_DATA);
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

        eth.call(args, "latest");

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(executor, times(1))
                .executeTransaction(eq(blockResult.getBlock()), any(), any(), any(), any(), any(), dataCaptor.capture(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getData()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteCallWithInputParameter_callExecutorWithInput() {
        CallArguments args = new CallArguments();
        args.setInput(TEST_DATA);
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

        eth.call(args, "latest");

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(executor, times(1))
                .executeTransaction(eq(blockResult.getBlock()), any(), any(), any(), any(), any(), dataCaptor.capture(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getInput()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteCallWithInputAndDataParameters_callExecutorWithInput() {
        CallArguments args = new CallArguments();
        args.setData(TEST_DATA);
        args.setInput(TEST_DATA);
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


        eth.call(args, "latest");

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(executor, times(1))
                .executeTransaction(eq(blockResult.getBlock()), any(), any(), any(), any(), any(), dataCaptor.capture(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getInput()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteEstimateGasWithDataParameter_callExecutorWithData() {
        CallArguments args = new CallArguments();
        args.setData(TEST_DATA);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        Block block = mock(Block.class);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock())
                .thenReturn(block);

        ProgramResult executorResult = mock(ProgramResult.class);
        TransactionExecutor transactionExecutor = mock(TransactionExecutor.class);
        when(transactionExecutor.getResult())
                .thenReturn(executorResult);

        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);
        when(reversibleTransactionExecutor.estimateGas(eq(blockchain.getBestBlock()), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(transactionExecutor);

        EthModule eth = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                null,
                reversibleTransactionExecutor,
                retriever,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        eth.estimateGas(args);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(reversibleTransactionExecutor, times(1))
                .estimateGas(eq(blockchain.getBestBlock()), any(), any(), any(), any(), any(), dataCaptor.capture(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getData()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteEstimateGasWithInputParameter_callExecutorWithInput() {
        CallArguments args = new CallArguments();
        args.setInput(TEST_DATA);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        Block block = mock(Block.class);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock())
                .thenReturn(block);

        ProgramResult executorResult = mock(ProgramResult.class);
        TransactionExecutor transactionExecutor = mock(TransactionExecutor.class);
        when(transactionExecutor.getResult())
                .thenReturn(executorResult);

        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);
        when(reversibleTransactionExecutor.estimateGas(eq(blockchain.getBestBlock()), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(transactionExecutor);

        EthModule eth = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                null,
                reversibleTransactionExecutor,
                retriever,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        eth.estimateGas(args);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(reversibleTransactionExecutor, times(1))
                .estimateGas(eq(blockchain.getBestBlock()), any(), any(), any(), any(), any(), dataCaptor.capture(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getInput()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteEstimateGasWithInputAndDataParameters_callExecutorWithInput() {
        CallArguments args = new CallArguments();
        args.setData(TEST_DATA);
        args.setInput(TEST_DATA);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        Block block = mock(Block.class);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock())
                .thenReturn(block);

        ProgramResult executorResult = mock(ProgramResult.class);
        TransactionExecutor transactionExecutor = mock(TransactionExecutor.class);
        when(transactionExecutor.getResult())
                .thenReturn(executorResult);

        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);
        when(reversibleTransactionExecutor.estimateGas(eq(blockchain.getBestBlock()), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(transactionExecutor);

        EthModule eth = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                null,
                reversibleTransactionExecutor,
                retriever,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        eth.estimateGas(args);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(reversibleTransactionExecutor, times(1))
                .estimateGas(eq(blockchain.getBestBlock()), any(), any(), any(), any(), any(), dataCaptor.capture(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getInput()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteSendTransactionWithDataParameter_callExecutorWithData() {
        Constants constants = Constants.regtest();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setData(TEST_DATA);

        String expectedDataValue = args.getData().substring(2);

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        when(transactionGateway.receiveTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        TransactionPool transactionPool = mock(TransactionPool.class);

        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool, transactionGateway);

        ethModuleTransaction.sendTransaction(args);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionGateway, times(1))
                .receiveTransaction(transactionCaptor.capture());
        assertArrayEquals(Hex.decode(expectedDataValue), transactionCaptor.getValue().getData());
    }

    @Test
    void whenExecuteSendTransactionWithInputParameter_callExecutorWithInput() {
        Constants constants = Constants.regtest();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setInput(TEST_DATA);

        String expectedDataValue = args.getInput().substring(2);

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        when(transactionGateway.receiveTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        TransactionPool transactionPool = mock(TransactionPool.class);

        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool, transactionGateway);

        ethModuleTransaction.sendTransaction(args);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionGateway, times(1))
                .receiveTransaction(transactionCaptor.capture());
        assertArrayEquals(Hex.decode(expectedDataValue), transactionCaptor.getValue().getData());
    }

    @Test
    void whenExecuteSendTransactionWithInputAndDataParameters_callExecutorWithInput() {
        Constants constants = Constants.regtest();

        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        // Hash of the expected transaction
        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        args.setData(TEST_DATA);
        args.setInput(TEST_DATA);

        String expectedDataValue = args.getInput().substring(2);

        TransactionPoolAddResult transactionPoolAddResult = mock(TransactionPoolAddResult.class);
        when(transactionPoolAddResult.transactionsWereAdded()).thenReturn(true);

        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        when(transactionGateway.receiveTransaction(any(Transaction.class))).thenReturn(transactionPoolAddResult);

        TransactionPool transactionPool = mock(TransactionPool.class);

        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool, transactionGateway);

        ethModuleTransaction.sendTransaction(args);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionGateway, times(1))
                .receiveTransaction(transactionCaptor.capture());
        assertArrayEquals(Hex.decode(expectedDataValue), transactionCaptor.getValue().getData());
    }
}
