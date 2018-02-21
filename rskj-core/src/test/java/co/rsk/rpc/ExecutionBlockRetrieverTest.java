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

package co.rsk.rpc;

import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExecutionBlockRetrieverTest {
    @Test
    public void getLatest() {
        Block latest = mock(Block.class);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest);
        ExecutionBlockRetriever retriever = new ExecutionBlockRetriever(blockchain);

        assertThat(retriever.getExecutionBlock("latest"), is(latest));
    }

    @Test
    public void getLatestIsUpToDate() {
        Block latest1 = mock(Block.class);
        Block latest2 = mock(Block.class);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest1)
                .thenReturn(latest2);
        ExecutionBlockRetriever retriever = new ExecutionBlockRetriever(blockchain);

        assertThat(retriever.getExecutionBlock("latest"), is(latest1));
        assertThat(retriever.getExecutionBlock("latest"), is(latest2));
    }

    @Test(expected = JsonRpcUnimplementedMethodException.class)
    public void getOtherThanLatestThrows() {
        Block latest = mock(Block.class);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest);
        ExecutionBlockRetriever retriever = new ExecutionBlockRetriever(blockchain);

        retriever.getExecutionBlock("other");
    }
}