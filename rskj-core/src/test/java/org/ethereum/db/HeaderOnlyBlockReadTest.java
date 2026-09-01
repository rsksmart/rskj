/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
package org.ethereum.db;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import co.rsk.core.BlockDifficulty;
import co.rsk.db.HashMapBlocksIndex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.HashMapDB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reading a header must not require decoding the block it belongs to. Validating one block walks 449
 * ancestors and uses nothing but their headers, so the block path made that walk decode every
 * transaction in every one of those blocks and discard the result.
 */
class HeaderOnlyBlockReadTest {

    private final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    private IndexedBlockStore storeWith(Block... blocks) {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        BlockDifficulty cum = BlockDifficulty.ZERO;
        for (Block b : blocks) {
            cum = cum.add(b.getCumulativeDifficulty());
            store.saveBlock(b, cum, true);
        }
        return store;
    }

    @Test
    void headerReadMatchesTheHeaderOfTheWholeBlock() {
        Block block = new BlockGenerator().getBlock(1);
        IndexedBlockStore store = storeWith(block);

        BlockHeader viaHeader = store.getBlockHeaderByHash(block.getHash().getBytes());
        BlockHeader viaBlock = store.getBlockByHash(block.getHash().getBytes()).getHeader();

        assertArrayEquals(viaBlock.getHash().getBytes(), viaHeader.getHash().getBytes());
        assertArrayEquals(viaBlock.getFullEncoded(), viaHeader.getFullEncoded());
        assertEquals(viaBlock.getNumber(), viaHeader.getNumber());
    }

    /** The interesting case: nothing is cached, so the header comes off the encoded block. */
    @Test
    void headerIsDecodedFromStorageWithoutTheBody() {
        Block block = new BlockGenerator().getBlock(3);
        IndexedBlockStore store = storeWith(block);

        BlockHeader fromRlp = blockFactory.decodeBlockHeader(block.getEncoded());

        assertArrayEquals(block.getHeader().getFullEncoded(), fromRlp.getFullEncoded());
        assertArrayEquals(block.getHash().getBytes(), fromRlp.getHash().getBytes());
    }

    @Test
    void decodingOnlyTheHeaderAgreesWithDecodingTheWholeBlock() {
        for (int i = 1; i <= 4; i++) {
            Block block = new BlockGenerator().getBlock(i);
            byte[] encoded = block.getEncoded();

            BlockHeader whole = blockFactory.decodeBlock(encoded).getHeader();
            BlockHeader headerOnly = blockFactory.decodeBlockHeader(encoded);

            assertArrayEquals(whole.getFullEncoded(), headerOnly.getFullEncoded(),
                    "header-only decode diverged from full decode at block " + i);
        }
    }

    @Test
    void unknownHashHasNoHeader() {
        IndexedBlockStore store = storeWith(new BlockGenerator().getBlock(1));

        assertNull(store.getBlockHeaderByHash(new byte[32]));
    }

    /** A removed block must not keep answering from the header cache. */
    @Test
    void removingABlockClearsItsCachedHeader() {
        Block block = new BlockGenerator().getBlock(1);
        IndexedBlockStore store = storeWith(block);

        assertArrayEquals(block.getHash().getBytes(),
                store.getBlockHeaderByHash(block.getHash().getBytes()).getHash().getBytes());

        store.removeBlock(block);

        assertNull(store.getBlockHeaderByHash(block.getHash().getBytes()));
    }
}
