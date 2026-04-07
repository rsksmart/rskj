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
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class BlockResultDTOTest {

    // todo(fedejinich) currently RemascTx(blockNumber) has a bug, thats why I initialize this way
    public static final RemascTransaction REMASC_TRANSACTION = new RemascTransaction(new RemascTransaction(1).getEncoded());
    public static final Transaction TRANSACTION = new TransactionBuilder().buildRandomTransaction();
    private static final String HEX_ZERO = "0x0";
    @TempDir
    public Path tempDir;
    private Block block;
    private BlockStore blockStore;

    @BeforeEach
    void setup() {
        block = buildBlockWithTransactions(Arrays.asList(TRANSACTION, REMASC_TRANSACTION));
        blockStore = mock(BlockStore.class);
        when(blockStore.getTotalDifficultyForHash(any())).thenReturn(BlockDifficulty.ONE);
    }

    @Test
    void getBlockResultDTOWithRemascAndTransactionHashes() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, false, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        List<String> transactionHashes = transactionHashesByBlock(blockResultDTO);

        Assertions.assertNotNull(blockResultDTO);
        Assertions.assertEquals(2, blockResultDTO.getTransactions().size());
        Assertions.assertTrue(transactionHashes.contains(TRANSACTION.getHash().toJsonString()));
        Assertions.assertTrue(transactionHashes.contains(REMASC_TRANSACTION.getHash().toJsonString()));
    }

    @Test
    void getBlockResultDTOWithoutRemascAndTransactionHashes() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, true, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        List<String> transactionHashes = transactionHashesByBlock(blockResultDTO);

        Assertions.assertNotNull(blockResultDTO);
        Assertions.assertEquals(1, blockResultDTO.getTransactions().size());
        Assertions.assertTrue(transactionHashes.contains(TRANSACTION.getHash().toJsonString()));
        Assertions.assertFalse(transactionHashes.contains(REMASC_TRANSACTION.getHash().toJsonString()));
    }

    @Test
    void getBlockResultDTOWithoutRemasc_emptyTransactions() {
        Block block = buildBlockWithTransactions(List.of(REMASC_TRANSACTION));
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, true, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals(HexUtils.toUnformattedJsonHex(EMPTY_TRIE_HASH), blockResultDTO.getTransactionsRoot());

        Assertions.assertNotNull(blockResultDTO);
        Assertions.assertTrue(blockResultDTO.getTransactions().isEmpty());
    }

    @Test
    void getBlockResultDTO_containsObsoleteFields() {
        Block block = buildBlockWithTransactions(Collections.emptyList());
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, false, blockStore, true, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", blockResultDTO.getMixHash());
        Assertions.assertEquals("0x0000000000000000", blockResultDTO.getNonce());
    }

    @Test
    void getBlockResultDTO_getBaseEvent() {
        byte[] baseEvent = new byte[]{0x1, 0x2, 0x3};
        Block spyBlock = spy(block);
        BlockHeader mockHeader = mock(BlockHeader.class);
        when(spyBlock.getHeader()).thenReturn(mockHeader);
        when(mockHeader.getBaseEvent()).thenReturn(baseEvent);

        BlockResultDTO result = BlockResultDTO.fromBlock(spyBlock, false, blockStore, false, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertEquals(HexUtils.toUnformattedJsonHex(baseEvent), result.getBaseEvent());
    }

    @Test
    void getBlockResultDTOWithNullSignatureRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, false, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertNotNull(blockResultDTO);
        Assertions.assertEquals(2, blockResultDTO.getTransactions().size());

        TransactionResultDTO regularTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(0);
        Assertions.assertNotNull(regularTransaction);

        TransactionResultDTO remascTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(1);
        Assertions.assertNotNull(remascTransaction);
        Assertions.assertNull(remascTransaction.getV());
        Assertions.assertNull(remascTransaction.getR());
        Assertions.assertNull(remascTransaction.getS());
    }

    @Test
    void getBlockResultDTOWithWithZeroSignatureRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, false, true, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertNotNull(blockResultDTO);
        Assertions.assertEquals(2, blockResultDTO.getTransactions().size());

        TransactionResultDTO regularTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(0);
        Assertions.assertNotNull(regularTransaction);

        TransactionResultDTO remascTransaction = (TransactionResultDTO) blockResultDTO.getTransactions().get(1);
        Assertions.assertNotNull(remascTransaction);
        Assertions.assertEquals(HEX_ZERO, remascTransaction.getV());
        Assertions.assertEquals(HEX_ZERO, remascTransaction.getR());
        Assertions.assertEquals(HEX_ZERO, remascTransaction.getS());
    }

    @Test
    void getBlockResultDTOWithoutRemascAndFullTransactions() {
        BlockResultDTO blockResultDTO = BlockResultDTO.fromBlock(block, true, blockStore, true, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        List<String> transactionResultsHashes = transactionResultsByBlock(blockResultDTO).stream().map(e -> e.getHash()).collect(Collectors.toList());

        Assertions.assertNotNull(blockResultDTO);
        Assertions.assertEquals(1, blockResultDTO.getTransactions().size());
        Assertions.assertTrue(transactionResultsHashes.contains(TRANSACTION.getHash().toJsonString()));
        Assertions.assertFalse(transactionResultsHashes.contains(REMASC_TRANSACTION.getHash().toJsonString()));
        Assertions.assertNotNull(blockResultDTO.getRskPteEdges());
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
        RskTestFactory objects = new RskTestFactory(tempDir) {
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
