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

package co.rsk.net;

import co.rsk.core.commons.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class BlockStore {
    private Map<Keccak256, Block> blocks = new HashMap<>();
    private Map<Long, Set<Block>> blocksbynumber = new HashMap<>();
    private Map<Keccak256, Set<Block>> blocksbyparent = new HashMap<>();

    private final Map<Keccak256, BlockHeader> headers = new HashMap<>();

    public synchronized void saveBlock(Block block) {
        Keccak256 key = block.getHash();
        Keccak256 pkey = block.getParentHash();
        Long nkey = Long.valueOf(block.getNumber());
        this.blocks.put(key, block);

        Set<Block> bsbynumber = this.blocksbynumber.get(nkey);

        if (bsbynumber == null) {
            bsbynumber = new HashSet<>();
            this.blocksbynumber.put(nkey, bsbynumber);
        }

        bsbynumber.add(block);

        Set<Block> bsbyphash = this.blocksbyparent.get(pkey);

        if (bsbyphash == null) {
            bsbyphash = new HashSet<>();
            this.blocksbyparent.put(pkey, bsbyphash);
        }

        bsbyphash.add(block);
    }

    public synchronized void removeBlock(Block block) {
        if (!this.hasBlock(block.getHash())) {
            return;
        }

        this.blocks.remove(block.getHash());

        removeBlockByNumber(block.getHash(), block.getNumber());
        removeBlockByParent(block.getHash(), block.getParentHash());
    }

    private void removeBlockByParent(Keccak256 key, Keccak256 pkey) {
        Set<Block> byparent = this.blocksbyparent.get(pkey);

        if (byparent != null && !byparent.isEmpty()) {
            Block toremove = null;

            for (Block blk : byparent) {
                if (blk.getHash().equals(key)) {
                    toremove = blk;
                    break;
                }
            }

            if (toremove != null){
                byparent.remove(toremove);

                if (byparent.isEmpty()) {
                    this.blocksbyparent.remove(pkey);
                }
            }
        }
    }

    private void removeBlockByNumber(Keccak256 key, Long nkey) {
        Set<Block> bynumber = this.blocksbynumber.get(nkey);

        if (bynumber != null && !bynumber.isEmpty()) {
            Block toremove = null;

            for (Block blk : bynumber) {
                if (blk.getHash().equals(key)) {
                    toremove = blk;
                    break;
                }
            }

            if (toremove != null) {
                bynumber.remove(toremove);
                if (bynumber.isEmpty()) {
                    this.blocksbynumber.remove(nkey);
                }
            }
        }
    }

    public synchronized Block getBlockByHash(Keccak256 hash) {
        return this.blocks.get(hash);
    }

    public synchronized List<Block> getBlocksByNumber(long number) {
        Long nkey = Long.valueOf(number);

        Set<Block> blockSet = this.blocksbynumber.get(nkey);

        if (blockSet == null) {
            blockSet = new HashSet<>();
        }

        return new ArrayList<>(blockSet);
    }

    public synchronized List<Block> getBlocksByParentHash(Keccak256 hash) {
        Set<Block> blockSet = this.blocksbyparent.get(hash);

        if (blockSet == null) {
            blockSet = new HashSet<>();
        }

        return new ArrayList<>(blockSet);
    }

    /**
     * getChildrenOf returns all the children of a list of blocks that are in the BlockStore.
     *
     * @param blocks a set of blocks to retrieve the children.
     * @return A list with all the children of the given list of blocks.
     */
    public List<Block> getChildrenOf(Set<Block> blocks) {
        return blocks.stream()
                .flatMap(b-> getBlocksByParentHash(b.getHash()).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public synchronized boolean hasBlock(Block block) {
        return this.blocks.containsKey(block.getHash());
    }

    public synchronized boolean hasBlock(Keccak256 hash) {
        return this.blocks.containsKey(hash);
    }

    public synchronized int size() {
        return this.blocks.size();
    }

    public synchronized long minimalHeight() {
        long value = 0;

        for (Block b : this.blocks.values()) {
            if (value == 0 || b.getNumber() < value) {
                value = b.getNumber();
            }
        }

        return value;
    }

    public synchronized long maximumHeight() {
        long value = 0;

        for (Block b : this.blocks.values()) {
            if (value == 0 || b.getNumber() > value) {
                value = b.getNumber();
            }
        }

        return value;
    }

    public synchronized void releaseRange(long from, long to) {
        for (long k = from; k <= to; k++) {
            for (Block b : this.getBlocksByNumber(k)) {
                this.removeBlock(b);
            }
        }
    }

    /**
     * hasHeader returns true if this block store has the header of the corresponding block.
     *
     * @param hash the block's hash.
     * @return true if the store has the header, false otherwise.
     */
    public synchronized boolean hasHeader(@Nonnull final Keccak256 hash) {
        return this.headers.containsKey(hash);
    }

    /**
     * saveHeader saves the given header into the block store.
     *
     * @param header the header to store.
     */
    public synchronized void saveHeader(@Nonnull final BlockHeader header) {
        this.headers.put(header.getHash(), header);
    }

    /**
     * removeHeader removes the given header from the block store.
     *
     * @param hash the header to remove.
     */
    public synchronized void removeHeader(@Nonnull final Keccak256 hash) {
        if (!this.hasHeader(hash)) {
            return;
        }

        this.headers.remove(hash);
    }
}
