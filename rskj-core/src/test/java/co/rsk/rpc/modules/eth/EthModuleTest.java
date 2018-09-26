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

import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.rpc.ExecutionBlockRetriever;
import org.ethereum.core.Block;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EthModuleTest {
    @Test
    public void callSmokeTest() {
        Web3.CallArguments args = new Web3.CallArguments();
        Block executionBlock = mock(Block.class);
        ExecutionBlockRetriever retriever = mock(ExecutionBlockRetriever.class);
        when(retriever.getExecutionBlock("latest"))
                .thenReturn(executionBlock);

        byte[] hreturn = TypeConverter.stringToByteArray("hello");
        ProgramResult executorResult = mock(ProgramResult.class);
        when(executorResult.getHReturn())
                .thenReturn(hreturn);

        ReversibleTransactionExecutor executor = mock(ReversibleTransactionExecutor.class);
        when(executor.executeTransaction(eq(executionBlock), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(executorResult);

        EthModule eth = new EthModule(
                null,
                null,
                executor,
                retriever,
                null,
                null,
                null
        );

        String result = eth.call(args, "latest");
        assertThat(result, is(TypeConverter.toJsonHex(hreturn)));
    }
}
