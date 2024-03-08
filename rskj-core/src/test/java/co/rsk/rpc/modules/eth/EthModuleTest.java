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
import co.rsk.core.Coin;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.core.bc.PendingState;
import co.rsk.crypto.Keccak256;
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
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.program.ProgramResult;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EthModuleTest {

    public static final String TEST_DATA = "0x603d80600c6000396000f3007c01000000000000000000000000000000000000000000000000000000006000350463c6888fa18114602d57005b6007600435028060005260206000f3";

    private final TestSystemProperties config = new TestSystemProperties();
    private final SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

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
        String actualResult = eth.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));

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
        String actualResult = eth.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));

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
            eth.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));
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
        String txResult = ethModuleTransaction.sendTransaction(TransactionFactoryHelper.toCallArgumentsParam(args));

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

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> ethModuleTransaction.sendTransaction(callArgumentsParam));
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
        HexDataParam hexDataParam = new HexDataParam(rawData);
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> ethModuleTransaction.sendRawTransaction(hexDataParam));
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

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(argsMock);
        // Then
        try {
            ethModuleTransaction.sendTransaction(callArgumentsParam);
            fail("RskJsonRpcRequestException should be thrown");
        } catch (RskJsonRpcRequestException ex) {
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

        HexAddressParam addressParam = new HexAddressParam(TestUtils.generateAddress("addr").toHexString());
        String addr = eth.getCode(addressParam, "pending");
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

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");

        eth.call(callArgumentsParam, blockIdentifierParam);

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

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");

        eth.call(callArgumentsParam, blockIdentifierParam);

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


        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");

        eth.call(callArgumentsParam, blockIdentifierParam);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(executor, times(1))
                .executeTransaction(eq(blockResult.getBlock()), any(), any(), any(), any(), any(), dataCaptor.capture(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getInput()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteEstimateGasWithDataParameter_callExecutorWithData() {
        CallArguments args = new CallArguments();
        args.setData(TEST_DATA);
        Block block = mock(Block.class);
        ExecutionBlockRetriever.Result blockResult = mock(ExecutionBlockRetriever.Result.class);
        when(blockResult.getBlock()).thenReturn(block);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.retrieveExecutionBlock("latest")).thenReturn(blockResult);
        Blockchain blockchain = mock(Blockchain.class);

        ProgramResult executorResult = mock(ProgramResult.class);
        TransactionExecutor transactionExecutor = mock(TransactionExecutor.class);
        when(transactionExecutor.getResult())
                .thenReturn(executorResult);

        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);
        when(reversibleTransactionExecutor.estimateGas(eq(block), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(transactionExecutor);

        EthModule eth = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                null,
                reversibleTransactionExecutor,
                retriever,
                mock(RepositoryLocator.class),
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        eth.estimateGas(callArgumentsParam, new BlockIdentifierParam("latest"));

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(reversibleTransactionExecutor, times(1))
                .estimateGas(eq(block), any(), any(), any(), any(), any(), dataCaptor.capture(), any(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getData()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteEstimateGasWithInputParameter_callExecutorWithInput() {
        CallArguments args = new CallArguments();
        args.setInput(TEST_DATA);
        Block block = mock(Block.class);
        ExecutionBlockRetriever.Result blockResult = mock(ExecutionBlockRetriever.Result.class);
        when(blockResult.getBlock()).thenReturn(block);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.retrieveExecutionBlock("latest")).thenReturn(blockResult);
        Blockchain blockchain = mock(Blockchain.class);

        ProgramResult executorResult = mock(ProgramResult.class);
        TransactionExecutor transactionExecutor = mock(TransactionExecutor.class);
        when(transactionExecutor.getResult())
                .thenReturn(executorResult);

        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);
        when(reversibleTransactionExecutor.estimateGas(eq(block), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(transactionExecutor);

        EthModule eth = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                null,
                reversibleTransactionExecutor,
                retriever,
                mock(RepositoryLocator.class),
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        eth.estimateGas(callArgumentsParam, new BlockIdentifierParam("latest"));

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(reversibleTransactionExecutor, times(1))
                .estimateGas(eq(block), any(), any(), any(), any(), any(), dataCaptor.capture(), any(), any());
        assertArrayEquals(HexUtils.strHexOrStrNumberToByteArray(args.getInput()), dataCaptor.getValue());
    }

    @Test
    void whenExecuteEstimateGasWithInputAndDataParameters_callExecutorWithInput() {
        CallArguments args = new CallArguments();
        args.setData(TEST_DATA);
        args.setInput(TEST_DATA);
        Block block = mock(Block.class);
        ExecutionBlockRetriever.Result blockResult = mock(ExecutionBlockRetriever.Result.class);
        when(blockResult.getBlock()).thenReturn(block);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.retrieveExecutionBlock("latest")).thenReturn(blockResult);
        Blockchain blockchain = mock(Blockchain.class);

        ProgramResult executorResult = mock(ProgramResult.class);
        TransactionExecutor transactionExecutor = mock(TransactionExecutor.class);
        when(transactionExecutor.getResult())
                .thenReturn(executorResult);

        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);
        when(reversibleTransactionExecutor.estimateGas(eq(block), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(transactionExecutor);

        EthModule eth = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                null,
                reversibleTransactionExecutor,
                retriever,
                mock(RepositoryLocator.class),
                null,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        eth.estimateGas(callArgumentsParam, new BlockIdentifierParam("latest"));

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(reversibleTransactionExecutor, times(1))
                .estimateGas(eq(block), any(), any(), any(), any(), any(), dataCaptor.capture(), any(), any());
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

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        ethModuleTransaction.sendTransaction(callArgumentsParam);

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

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        ethModuleTransaction.sendTransaction(callArgumentsParam);

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

        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        ethModuleTransaction.sendTransaction(callArgumentsParam);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionGateway, times(1))
                .receiveTransaction(transactionCaptor.capture());
        assertArrayEquals(Hex.decode(expectedDataValue), transactionCaptor.getValue().getData());
    }

    @Test
    void testEthPendingTransactionsWithNoTransactions() {
        EthModuleWallet ethModuleWalletMock = mock(EthModuleWallet.class);
        TransactionPool transactionPoolMock = mock(TransactionPool.class);
        when(transactionPoolMock.getPendingTransactions()).thenReturn(Collections.emptyList());

        Block block = mock(Block.class);
        ExecutionBlockRetriever.Result blockResult = mock(ExecutionBlockRetriever.Result.class);
        when(blockResult.getBlock()).thenReturn(block);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        Blockchain blockchain = mock(Blockchain.class);

        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);

        EthModule ethModule  = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                transactionPoolMock,
                reversibleTransactionExecutor,
                retriever,
                mock(RepositoryLocator.class),
                ethModuleWalletMock,
                null,
                new BridgeSupportFactory(
                        null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        List<Transaction> result = ethModule.ethPendingTransactions();

        assertTrue(result.isEmpty(), "Expected no transactions");
    }

    @Test
    void pendingTransactionsWithMultipleManagedAccounts() {
        Wallet wallet = mock(Wallet.class);
        TransactionPool transactionPoolMock = mock(TransactionPool.class);
        EthModuleWalletEnabled ethModuleWallet = new EthModuleWalletEnabled(wallet, transactionPoolMock, signatureCache);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        Blockchain blockchain = mock(Blockchain.class);
        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);

        EthModule ethModule = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                transactionPoolMock,
                reversibleTransactionExecutor,
                retriever,
                mock(RepositoryLocator.class),
                ethModuleWallet,
                null,
                new BridgeSupportFactory(null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        Transaction mockTransaction1 = createMockTransaction("0x63a15ed8c3b83efc744f2e0a7824a00846c21860");
        Transaction mockTransaction2 = createMockTransaction( "0xa3a15ed8c3b83efc744f2e0a7824a00846c21860");
        Transaction mockTransaction3 = createMockTransaction( "0xb3a15ed8c3b83efc744f2e0a7824a00846c21860");
        List<Transaction> allTransactions = Arrays.asList(mockTransaction1, mockTransaction2, mockTransaction3);

        when(transactionPoolMock.getPendingTransactions()).thenReturn(allTransactions);
        when(ethModuleWallet.accounts()).thenReturn(new String[]{"0x63a15ed8c3b83efc744f2e0a7824a00846c21860", "0xa3a15ed8c3b83efc744f2e0a7824a00846c21860"});

        List<Transaction> result = ethModule.ethPendingTransactions();

        assertEquals(2, result.size(), "Expected only transactions from managed accounts");
    }

    @Test
    void pendingTransactionsWithNoManagedAccounts() {
        Wallet wallet = mock(Wallet.class);
        TransactionPool transactionPoolMock = mock(TransactionPool.class);
        EthModuleWalletEnabled ethModuleWallet = new EthModuleWalletEnabled(wallet, transactionPoolMock, signatureCache);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        Blockchain blockchain = mock(Blockchain.class);
        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);

        EthModule ethModule = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                transactionPoolMock,
                reversibleTransactionExecutor,
                retriever,
                mock(RepositoryLocator.class),
                ethModuleWallet,
                null,
                new BridgeSupportFactory(null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        Transaction mockTransaction1 = createMockTransaction("0x63a15ed8c3b83efc744f2e0a7824a00846c21860");
        Transaction mockTransaction2 = createMockTransaction("0x13a15ed8c3b83efc744f2e0a7824a00846c21860");
        List<Transaction> allTransactions = Arrays.asList(mockTransaction1, mockTransaction2);

        when(transactionPoolMock.getPendingTransactions()).thenReturn(allTransactions);
        when(ethModuleWallet.accounts()).thenReturn(new String[]{});

        List<Transaction> result = ethModule.ethPendingTransactions();

        assertTrue(result.isEmpty(), "Expected no transactions as there are no managed accounts");
    }

    @Test
    void pendingTransactionsWithWalletDisabled() {
        TransactionPool transactionPoolMock = mock(TransactionPool.class);
        EthModuleWalletDisabled ethModuleWallet = new EthModuleWalletDisabled();
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        Blockchain blockchain = mock(Blockchain.class);
        ReversibleTransactionExecutor reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);

        EthModule ethModule = new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                blockchain,
                transactionPoolMock,
                reversibleTransactionExecutor,
                retriever,
                mock(RepositoryLocator.class),
                ethModuleWallet,
                null,
                new BridgeSupportFactory(null, null, null, signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap());

        List<Transaction> result = ethModule.ethPendingTransactions();

        assertTrue(result.isEmpty(), "Expected no transactions as wallet is disabled");
    }


    private Transaction createMockTransaction(String fromAddress) {
        Transaction transaction = mock(Transaction.class);
        RskAddress address = new RskAddress(fromAddress);
        System.out.println("mock address: " + address);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(address);

        byte[] mockHashBytes = new byte[32];
        Arrays.fill(mockHashBytes, (byte) 1);
        Keccak256 mockHash = new Keccak256(mockHashBytes);
        when(transaction.getHash()).thenReturn(mockHash);
        when(transaction.getReceiveAddress()).thenReturn(address);
        when(transaction.getNonce()).thenReturn(BigInteger.ZERO.toByteArray());
        when(transaction.getGasLimit()).thenReturn(BigInteger.valueOf(21000).toByteArray());
        when(transaction.getGasPrice()).thenReturn(Coin.valueOf(50_000_000_000L));
        when(transaction.getValue()).thenReturn(Coin.ZERO);
        when(transaction.getData()).thenReturn(new byte[0]);
        ECDSASignature mockSignature = new ECDSASignature(BigInteger.ONE, BigInteger.ONE);
        when(transaction.getSignature()).thenReturn(mockSignature);
        when(transaction.getEncodedV()).thenReturn((byte) 1);

        return transaction;
    }
}
