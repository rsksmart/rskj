/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.net;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockCacheTest {

    private static final Keccak256 HASH_1 = new Keccak256(HashUtil.sha256(new byte[]{1}));
    private static final Keccak256 HASH_2 = new Keccak256(HashUtil.sha256(new byte[]{2}));
    private static final Keccak256 HASH_3 = new Keccak256(HashUtil.sha256(new byte[]{3}));
    private static final Keccak256 HASH_4 = new Keccak256(HashUtil.sha256(new byte[]{4}));
    private static final Keccak256 HASH_5 = new Keccak256(HashUtil.sha256(new byte[]{5}));

    @Test
    public void getUnknownBlockAsNull() {
        BlockCache store = getSubject();
        assertFalse(store.getBlockByHash(HASH_1).isPresent());
    }

    @Test
    public void putAndGetValue() {
        BlockCache store = getSubject();
        Block block = blockWithHash(HASH_1);
        store.addBlock(block);

        assertTrue(store.getBlockByHash(HASH_1).isPresent());
        assertThat(store.getBlockByHash(HASH_1).get(), is(block));
    }

    @Test
    public void putMoreThanSizeAndCheckCleanup() {
        BlockCache store = getSubject();

        store.addBlock(blockWithHash(HASH_1));
        store.addBlock(blockWithHash(HASH_2));
        store.addBlock(blockWithHash(HASH_3));
        store.addBlock(blockWithHash(HASH_4));
        store.addBlock(blockWithHash(HASH_5));

        assertFalse(store.getBlockByHash(HASH_1).isPresent());
        assertTrue(store.getBlockByHash(HASH_2).isPresent());
        assertTrue(store.getBlockByHash(HASH_3).isPresent());
        assertTrue(store.getBlockByHash(HASH_4).isPresent());
        assertTrue(store.getBlockByHash(HASH_5).isPresent());
    }

    @Test
    public void repeatingValueAtEndPreventsCleanup() {
        BlockCache store = getSubject();

        store.addBlock(blockWithHash(HASH_1));
        store.addBlock(blockWithHash(HASH_2));
        store.addBlock(blockWithHash(HASH_3));
        store.addBlock(blockWithHash(HASH_4));
        store.addBlock(blockWithHash(HASH_5));
        store.addBlock(blockWithHash(HASH_1));
        store.addBlock(blockWithHash(HASH_5));

        assertTrue(store.getBlockByHash(HASH_1).isPresent());
        assertFalse(store.getBlockByHash(HASH_2).isPresent());
        assertTrue(store.getBlockByHash(HASH_3).isPresent());
        assertTrue(store.getBlockByHash(HASH_4).isPresent());
        assertTrue(store.getBlockByHash(HASH_5).isPresent());
    }

    @Test
    public void addAndRemoveBlock() {
        BlockCache store = getSubject();
        Block block = blockWithHash(HASH_1);
        store.addBlock(block);
        store.removeBlock(block);

        assertFalse(store.getBlockByHash(HASH_1).isPresent());
    }

    /**
     * When removing a block, the block header should also be removed.
     */
    @Test
    public void addAndRemoveBlockHeader() {
        BlockCache store = getSubject();
        store.addBlockHeader(blockHeaderWithHash(HASH_1));
        store.removeBlock(blockWithHash(HASH_1));

        assertFalse(store.getBlockByHash(HASH_1).isPresent());
        assertFalse(store.getBlockHeaderByHash(HASH_1).isPresent());
    }

    /**
     * The header is retrieved from the block.
     */
    @Test
    public void getBlockHeader_from_block() {
        BlockCache store = getSubject();
        Block block = blockWithHash(HASH_1);

        when(block.getHeader()).thenReturn(mock(BlockHeader.class));

        store.addBlock(block);

        assertTrue(store.getBlockHeaderByHash(HASH_1).isPresent());
    }

    /**
     * The header is retrieved.
     */
    @Test
    public void getBlockHeader_from_header() {
        BlockCache store = getSubject();
        store.addBlockHeader(blockHeaderWithHash(HASH_1));

        assertFalse(store.getBlockByHash(HASH_1).isPresent());
        assertTrue(store.getBlockHeaderByHash(HASH_1).isPresent());
    }

    /**
     * The header is not found.
     */
    @Test
    public void getBlockHeader_not_found() {
        BlockCache store = getSubject();

        assertFalse(store.getBlockHeaderByHash(HASH_1).isPresent());
    }


    private BlockCache getSubject() {
        return new BlockCache(4);
    }

    private static Block blockWithHash(Keccak256 hash) {
        Block mock = mock(Block.class);
        when(mock.getHash()).thenReturn(hash);
        return mock;
    }

    private static BlockHeader blockHeaderWithHash(Keccak256 hash) {
        BlockHeader mock = mock(BlockHeader.class);
        when(mock.getHash()).thenReturn(hash);
        return mock;
    }
}