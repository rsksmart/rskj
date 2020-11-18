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
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.PendingState;
import co.rsk.db.RepositoryLocator;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.program.ProgramResult;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class EthModuleTest {
    @Test
    public void callSmokeTest() {
        Web3.CallArguments args = new Web3.CallArguments();
        BlockResult blockResult = mock(BlockResult.class);
        Block block = mock(Block.class);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.getExecutionBlock_workaround("latest"))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(block);

        byte[] hreturn = TypeConverter.stringToByteArray("hello");
        ProgramResult executorResult = mock(ProgramResult.class);
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

        String result = eth.call(args, "latest");
        assertThat(result, is(TypeConverter.toJsonHex(hreturn)));
    }

    @Test
    public void test_revertedTransaction() {
        Web3.CallArguments args = new Web3.CallArguments();
        BlockResult blockResult = mock(BlockResult.class);
        Block block = mock(Block.class);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.getExecutionBlock_workaround("latest"))
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
