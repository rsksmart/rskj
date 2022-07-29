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
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.HexUtils;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockResultDTOTest {

    private static final String HEX_ZERO = "0x0";

    private Block block;
    private BlockStore blockStore;
    public static final Transaction TRANSACTION = new TransactionBuilder().buildRandomTransaction();

    // todo(fedejinich) currently RemascTx(blockNumber) has a bug, thats why I initialize this way
    public static final RemascTransaction REMASC_TRANSACTION = new RemascTransaction(new RemascTransaction(1).getEncoded());

    @Before
    public void setup() {
        block = buildBlockWithTransactions(Arrays.asList(TRANSACTION, REMASC_TRANSACTION));
        blockStore = mock(BlockStore.class);
        when(blockStore.getTotalDifficultyForHash(any())).thenReturn(BlockDifficulty.ONE);
    }

    @Test
    public void getBlockResultDTOWithRemascAndTransactionHashes() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, false, false);
        List<String> transactionHashes = transactionHashesByBlock(blockResultDTO);

        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(2, blockResultDTO.getTransactions().size());
        Assert.assertTrue(transactionHashes.contains(TRANSACTION.getHash().toJsonString()));
        Assert.assertTrue(transactionHashes.contains(REMASC_TRANSACTION.getHash().toJsonString()));
    }

    @Test
    public void getBlockResultDTOWithoutRemascAndTransactionHashes() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, true, false);
        List<String> transactionHashes = transactionHashesByBlock(blockResultDTO);

        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(1, blockResultDTO.getTransactions().size());
        Assert.assertTrue(transactionHashes.contains(TRANSACTION.getHash().toJsonString()));
        Assert.assertFalse(transactionHashes.contains(REMASC_TRANSACTION.getHash().toJsonString()));
    }

    @Test
    public void getBlockResultDTOWithoutRemasc_emptyTransactions() {
        Block block = buildBlockWithTransactions(Arrays.asList(REMASC_TRANSACTION));
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, true, false);

        Assert.assertEquals(HexUtils.toUnformattedJsonHex(EMPTY_TRIE_HASH), blockResultDTO.getTransactionsRoot());

        Assert.assertNotNull(blockResultDTO);
        Assert.assertTrue(blockResultDTO.getTransactions().isEmpty());
    }


    @Test
    public void getBlockResultDTOWithNullSignatureRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, false, false);
        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(2, blockResultDTO.getTransactions().size());

        TransactionResultDTO regularTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(0);
        Assert.assertNotNull(regularTransaction);

        TransactionResultDTO remascTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(1);
        Assert.assertNotNull(remascTransaction);
        Assert.assertNull(remascTransaction.getV());
        Assert.assertNull(remascTransaction.getR());
        Assert.assertNull(remascTransaction.getS());
    }


    @Test
    public void getBlockResultDTOWithWithZeroSignatureRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, false, true);
        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(2, blockResultDTO.getTransactions().size());

        TransactionResultDTO regularTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(0);
        Assert.assertNotNull(regularTransaction);

        TransactionResultDTO remascTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(1);
        Assert.assertNotNull(remascTransaction);
        Assert.assertEquals(HEX_ZERO, remascTransaction.getV());
        Assert.assertEquals(HEX_ZERO, remascTransaction.getR());
        Assert.assertEquals(HEX_ZERO, remascTransaction.getS());
    }

    @Test
    public void getBlockResultDTOWithoutRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, true, false);

        List<String> transactionResultsHashes = transactionResultsByBlock(blockResultDTO).stream().map(e -> e.getHash()).collect(Collectors.toList());

        Assert.assertNotNull(blockResultDTO);
        Assert.assertEquals(1, blockResultDTO.getTransactions().size());
        Assert.assertTrue(transactionResultsHashes.contains(TRANSACTION.getHash().toJsonString()));
        Assert.assertFalse(transactionResultsHashes.contains(REMASC_TRANSACTION.getHash().toJsonString()));
    }

    private List<TransactionResultDTO> transactionResultsByBlock(BlockResultDTO blockResultDTO) {
        return blockResultDTO.getTransactions().stream()
                .map(e -> (TransactionResultDTO) e)
                .collect(Collectors.toList());
    }

    private List<String> transactionHashesByBlock(BlockResultDTO blockResultDTO) {
        return blockResultDTO.getTransactions().stream()
                .map(Objects::toString)
                .collect(Collectors.toList());
    }

    private Block buildBlockWithTransactions(List<Transaction> transactions) {
        RskTestFactory objects = new RskTestFactory() {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true);
            }
        };
        Blockchain blockChain = objects.getBlockchain();

        // Build block with remasc and normal txs
        BlockBuilder builder = new BlockBuilder(null, null, null).parent(blockChain.getBestBlock());

        return builder.transactions(transactions).build();
    }
}
