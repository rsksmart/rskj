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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import co.rsk.config.BridgeConstants;
import co.rsk.core.Coin;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.PendingState;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.rpc.CallArguments;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.Web3Impl;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.program.ProgramResult;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import co.rsk.config.BridgeConstants;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.PendingState;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.TransactionGateway;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class EthModuleTest {

    @Test
    public void callSmokeTest() {
        CallArguments args = new CallArguments();
        BlockResult blockResult = mock(BlockResult.class);
        Block block = mock(Block.class);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.retrieveExecutionBlock("latest"))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(block);

        byte[] hReturn = TypeConverter.stringToByteArray("hello");
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
                        null, null, null));

        String expectedResult = TypeConverter.toUnformattedJsonHex(hReturn);
        String actualResult = eth.call(args, "latest");

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void callWithoutReturn() {
        CallArguments args = new CallArguments();
        BlockResult blockResult = mock(BlockResult.class);
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
                        null, null, null));

        String expectedResult = TypeConverter.toUnformattedJsonHex(hReturn);
        String actualResult = eth.call(args, "latest");

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void test_revertedTransaction() {
        CallArguments args = new CallArguments();
        BlockResult blockResult = mock(BlockResult.class);
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
                        null, null, null));

        try {
            eth.call(args, "latest");
        } catch (RskJsonRpcRequestException e) {
            assertThat(e.getMessage(), Matchers.containsString("deposit too big"));
        }
    }

	@Test
	public void sendTransactionWithGasLimitTest() {

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
    public void sendTransaction_invalidSenderAccount_throwsRskJsonRpcRequestException() {
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
    public void getCode() {
        byte[] expectedCode = new byte[] {1, 2, 3};

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
                        null
                )
        );

        String addr = eth.getCode(TestUtils.randomAddress().toHexString(), "pending");
        Assert.assertThat(Hex.decode(addr.substring("0x".length())), is(expectedCode));
    }

    String anyAddress = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private Web3.CallArguments getTransactionParameters() {
        //
        RskAddress addr1 = new RskAddress(anyAddress);
        BigInteger value = BigInteger.valueOf(0); // do not pass value
        BigInteger gasPrice = BigInteger.valueOf(8);
        BigInteger gasLimit = BigInteger.valueOf(500000); // large enough
        String data = "0xff";

        Web3.CallArguments args = new Web3.CallArguments();
        args.from = TypeConverter.toJsonHex(addr1.getBytes());
        args.to = args.from;  // same account
        args.data = data;
        args.gas = TypeConverter.toQuantityJsonHex(gasLimit);
        args.gasPrice = TypeConverter.toQuantityJsonHex(gasPrice);
        args.value = value.toString();
        // Nonce doesn't matter
        args.nonce = "0";

        return args;
    }


    @Test
    public void estimateGas() {

        TransactionPool mockTransactionPool = mock(TransactionPool.class);
        PendingState mockPendingState = mock(PendingState.class);

        doReturn(mockPendingState).when(mockTransactionPool).getPendingState();
        Blockchain blockchain = mock(Blockchain.class);
        Block block = mock(Block.class);
        doReturn(block).when(blockchain).getBestBlock();
        RskAddress coinbase = new RskAddress(anyAddress);
        doReturn(coinbase).when(block).getCoinbase();

        ReversibleTransactionExecutor executor = mock(ReversibleTransactionExecutor.class);
        ProgramResult programResult = new ProgramResult();
        programResult.addDeductedRefund(10000);
        programResult.spendGas(30000);
        doReturn(programResult).when(executor).executeTransaction(any(),any(),
                any(),any(),any(),any(),any(),any());

        EthModule eth = new EthModule(
                null,
                (byte) 0,
                blockchain,
                mockTransactionPool,
                executor ,
                null,
                null,
                null,
                null,
                null,
                new BridgeSupportFactory(
                        null,
                        null,
                        null
                )
        );

        Web3.CallArguments args = getTransactionParameters();
        String gas = eth.estimateGas(args);
        byte[] gasReturned = Hex.decode(gas.substring("0x".length()));
        Assert.assertThat(gasReturned, is(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(40000))));
    }

    @Test
    public void chainId() {
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
                mock(BridgeSupportFactory.class)
        );
        assertThat(eth.chainId(), is("0x21"));
    }
}
