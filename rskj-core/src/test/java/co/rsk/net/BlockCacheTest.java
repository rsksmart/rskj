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
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

class BlockCacheTest {

    private static final byte[] HASH_1 = HashUtil.sha256(new byte[]{1});
    private static final byte[] HASH_2 = HashUtil.sha256(new byte[]{2});
    private static final byte[] HASH_3 = HashUtil.sha256(new byte[]{3});
    private static final byte[] HASH_4 = HashUtil.sha256(new byte[]{4});
    private static final byte[] HASH_5 = HashUtil.sha256(new byte[]{5});

    @Test
    void getUnknownBlockAsNull() {
        BlockCache store = getSubject();
        assertThat(store.getBlockByHash(HASH_1), nullValue());
    }

    @Test
    void putAndGetValue() {
        BlockCache store = getSubject();
        Block block = blockWithHash(new Keccak256(HASH_1));
        store.addBlock(block);

        assertThat(store.getBlockByHash(HASH_1), is(block));
    }

    @Test
    void putMoreThanSizeAndCheckCleanup() {
        BlockCache store = getSubject();
        store.addBlock(blockWithHash(new Keccak256(HASH_1)));
        store.addBlock(blockWithHash(new Keccak256(HASH_2)));
        store.addBlock(blockWithHash(new Keccak256(HASH_3)));
        store.addBlock(blockWithHash(new Keccak256(HASH_4)));
        store.addBlock(blockWithHash(new Keccak256(HASH_5)));

        assertThat(store.getBlockByHash(HASH_1), nullValue());
        assertThat(store.getBlockByHash(HASH_2), notNullValue());
        assertThat(store.getBlockByHash(HASH_3), notNullValue());
        assertThat(store.getBlockByHash(HASH_4), notNullValue());
        assertThat(store.getBlockByHash(HASH_5), notNullValue());
    }

    @Test
    void repeatingValueAtEndPreventsCleanup() {
        BlockCache store = getSubject();
        store.addBlock(blockWithHash(new Keccak256(HASH_1)));
        store.addBlock(blockWithHash(new Keccak256(HASH_2)));
        store.addBlock(blockWithHash(new Keccak256(HASH_3)));
        store.addBlock(blockWithHash(new Keccak256(HASH_4)));
        store.addBlock(blockWithHash(new Keccak256(HASH_1)));
        store.addBlock(blockWithHash(new Keccak256(HASH_5)));

        assertThat(store.getBlockByHash(HASH_1), notNullValue());
        assertThat(store.getBlockByHash(HASH_2), nullValue());
        assertThat(store.getBlockByHash(HASH_3), notNullValue());
        assertThat(store.getBlockByHash(HASH_4), notNullValue());
        assertThat(store.getBlockByHash(HASH_5), notNullValue());
    }

    @Test
    void addAndRetrieveBlock() {
        BlockCache store = getSubject();
        Block block = Mockito.mock(Block.class);
        when(block.getHash()).thenReturn(new Keccak256(HASH_1));
        store.addBlock(block);

        assertThat(store.getBlockByHash(HASH_1), is(block));
    }

    @Test
    void addAndRemoveBlock() {
        BlockCache store = getSubject();
        Block block = Mockito.mock(Block.class);
        when(block.getHash()).thenReturn(new Keccak256(HASH_1));
        store.addBlock(block);
        store.removeBlock(block);

        assertThat(store.getBlockByHash(HASH_1), nullValue());
    }

    private BlockCache getSubject() {
        return new BlockCache(4);
    }


    private static Block blockWithHash(Keccak256 hash) {
        Block mock = Mockito.mock(Block.class);
        when(mock.getHash()).thenReturn(hash);
        return mock;
    }
}
