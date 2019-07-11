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
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;

/**
 * BlockStoreCache stores both blocks and block headers and has a set max size. Elements are removed in access order.
 */
public class BlockCache {

    private final Map<Keccak256, Block> blockMap;
    private final Map<Keccak256, BlockHeader> headerMap;


    public BlockCache(int cacheSize) {
        this.blockMap = new MaxSizeHashMap<>(cacheSize, true);
        this.headerMap = new MaxSizeHashMap<>(cacheSize, true);
    }

    public void removeBlock(Block block) {
        Keccak256 hash = block.getHash();

        blockMap.remove(hash);
        headerMap.remove(hash);
    }

    /**
     * Adds a block to the cache, any other block or header with the same hash is removed.
     *
     * @param block The block to store, cannot be null.
     */
    public void addBlock(@Nonnull Block block) {
        Keccak256 hash = block.getHash();
        blockMap.put(hash, block);
        headerMap.remove(hash);
    }

    /**
     * Adds a block header to the cache, any other header with the same hash is removed.
     *
     * @param blockHeader The header to store, cannot be null.
     */
    public void addBlockHeader(@Nonnull BlockHeader blockHeader) {
        Keccak256 hash = blockHeader.getHash();
        headerMap.put(hash, blockHeader);
    }

    /**
     * Retrieves a block.
     *
     * @param hash The look up key, cannot be null.
     * @return An optional, empty if the block was not found.
     */
    public Optional<Block> getBlockByHash(@Nonnull Keccak256 hash) {
        return Optional.ofNullable(blockMap.get(hash));
    }

    /**
     * Retrieves a header from the cache. If a block is found instead of a header, the block's header is returned.
     *
     * @param hash The look up key, cannot be null.
     * @return An optional, empty if the header was not found in the cache.
     */
    public Optional<BlockHeader> getBlockHeaderByHash(@Nonnull Keccak256 hash) {
        BlockHeader headerResult = headerMap.get(hash);
        if (headerResult != null) {
            return Optional.of(headerResult);
        }

        return getBlockByHash(hash).map(Block::getHeader);
    }
}
