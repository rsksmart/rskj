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

package org.ethereum.rpc.dto;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BlockResultDTOTest {
    private Block block;
    private BlockStore blockStore;

    @Before
    public void setup() {
        RskTestFactory objects = new RskTestFactory() {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true);
            }
        };
        Blockchain blockChain = objects.getBlockchain();

        BlockBuilder builder = new BlockBuilder(null, null, null).parent(blockChain.getBestBlock());
        List<Transaction> transactions = new ArrayList<>();
        RemascTransaction remascTransaction = spy(new RemascTransaction(1));
        when(remascTransaction.isRemascTransaction(1, 2)).thenReturn(true);
        Transaction transaction = spy(new RemascTransaction(1));
        when(transaction.isRemascTransaction(0, 2)).thenReturn(false);
        transactions.add(transaction);
        transactions.add(remascTransaction);

        block = builder.transactions(transactions).build();
        blockStore = mock(BlockStore.class);
        when(blockStore.getTotalDifficultyForHash(any())).thenReturn(BlockDifficulty.ONE);
    }

    @Test
    public void getBlockResultDTOWithRemascAndTransactionHashes() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, false);

        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(2, blockResultDTO.getTransactions().size());
    }

    @Test
    public void getBlockResultDTOWithoutRemascAndTransactionHashes() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, true);

        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(1, blockResultDTO.getTransactions().size());
    }

    @Test
    public void getBlockResultDTOWithRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, false);

        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(2, blockResultDTO.getTransactions().size());
    }

    @Test
    public void getBlockResultDTOWithoutRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, true);

        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(1, blockResultDTO.getTransactions().size());
    }
}
