/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db;

import java.util.Optional;

import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

/**
 * Encodes and decodes both block headers and blocks allowing both value types to be stored the same block store.
 */
public class BlockStoreEncoder {

    private final BlockFactory blockFactory;

    /**
     * @param blockFactory The block factory is needed to decode encoded blocks and headers, cannot be null.
     */
    public BlockStoreEncoder(BlockFactory blockFactory) {
        this.blockFactory = blockFactory;
    }

    /**
     * Encodes a header to store in the block store.
     *
     * @param blockHeader The block header to encode, cannot be null.
     * @return The encoded header, never null.
     */
    public byte[] encodeBlockHeader(BlockHeader blockHeader) {
        if (blockHeader == null) {
            throw new IllegalArgumentException("Block header to wrap cannot be null");
        }

        return RLP.encodeList(blockHeader.getEncoded());
    }

    /**
     * Encodes a block to store in the block store or the cache.
     *
     * @param block The block to encode, cannot be null.
     * @return The encoded block, never null.
     */
    public byte[] encodeBlock(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block to wrap cannot be null");
        }

        return block.getEncoded();
    }

    /**
     * Decodes a valid encoded block or block header and retrieves the block if possible.
     *
     * @param value A valid encoded block or block header.
     * @return An optional block, empty if the value doesn't decode to a block.
     */
    public Optional<Block> decodeBlock(byte[] value) {
        RLPList rlpValue = RLP.decodeList(value);
        if (rlpValue.size() != 3 && rlpValue.size() != 1) {
            throw new IllegalArgumentException("Wrapped value doesn't correspond to valid block nor header");
        }

        if (rlpValue.size() == 3) {
            return Optional.of(blockFactory.decodeBlock(value));
        }

        return Optional.empty();
    }

    /**
     * Decodes a valid encoded block or block header and retrieves the header if possible.
     *
     * @param value A valid encoded block or block header.
     * @return An optional block header. Never be empty.
     */
    public Optional<BlockHeader> decodeBlockHeader(byte[] value) {
        RLPList rlpValue = RLP.decodeList(value);
        if (rlpValue.size() != 3 && rlpValue.size() != 1) {
            throw new IllegalArgumentException("Wrapped value doesn't correspond to valid block nor header");
        }

        BlockHeader blockHeader;
        if (rlpValue.size() == 3) {
            blockHeader = blockFactory.decodeBlock(value).getHeader();
        } else {
            blockHeader = blockFactory.decodeHeader(rlpValue.get(0).getRLPData());
        }

        return Optional.of(blockHeader);
    }
}