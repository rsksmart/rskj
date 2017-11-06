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

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.ByteArrayWrapper;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class BlockStore {
    private Map<ByteArrayWrapper, Block> blocks = new HashMap<>();
    private Map<Long, Set<Block>> blocksbynumber = new HashMap<>();
    private Map<ByteArrayWrapper, Set<Block>> blocksbyparent = new HashMap<>();

    private final Map<ByteArrayWrapper, BlockHeader> headers = new HashMap<>();
    private final Map<Long, Set<ByteArrayWrapper>> headersbynumber = new HashMap<>();
    private final Map<ByteArrayWrapper, Set<ByteArrayWrapper>> headersbyparent = new HashMap<>();

    public synchronized void saveBlock(Block block) {
        ByteArrayWrapper key = new ByteArrayWrapper(block.getHash());
        ByteArrayWrapper pkey = new ByteArrayWrapper(block.getParentHash());
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
        if (!this.hasBlock(block.getHash()))
            return;

        ByteArrayWrapper key = new ByteArrayWrapper(block.getHash());
        ByteArrayWrapper pkey = new ByteArrayWrapper(block.getParentHash());
        Long nkey = Long.valueOf(block.getNumber());

        this.blocks.remove(key);

        removeBlockByNumber(key, nkey);
        removeBlockByParent(key, pkey);
    }

    private void removeBlockByParent(ByteArrayWrapper key, ByteArrayWrapper pkey) {
        Set<Block> byparent = this.blocksbyparent.get(pkey);

        if (byparent != null && !byparent.isEmpty()) {
            Block toremove = null;

            for (Block blk : byparent) {
                if (new ByteArrayWrapper(blk.getHash()).equals(key)) {
                    toremove = blk;
                    break;
                }
            }

            if (toremove != null){
                byparent.remove(toremove);

                if (byparent.isEmpty())
                    this.blocksbyparent.remove(pkey);
            }
        }
    }

    private void removeBlockByNumber(ByteArrayWrapper key, Long nkey) {
        Set<Block> bynumber = this.blocksbynumber.get(nkey);

        if (bynumber != null && !bynumber.isEmpty()) {
            Block toremove = null;

            for (Block blk : bynumber) {
                if (new ByteArrayWrapper(blk.getHash()).equals(key)) {
                    toremove = blk;
                    break;
                }
            }

            if (toremove != null) {
                bynumber.remove(toremove);
                if (bynumber.isEmpty())
                    this.blocksbynumber.remove(nkey);
            }
        }
    }

    public synchronized Block getBlockByHash(byte[] hash) {
        ByteArrayWrapper key = new ByteArrayWrapper(hash);

        return this.blocks.get(key);
    }

    public synchronized List<Block> getBlocksByNumber(long number) {
        Long nkey = Long.valueOf(number);

        Set<Block> blockSet = this.blocksbynumber.get(nkey);

        if (blockSet == null)
            blockSet = new HashSet<>();

        return new ArrayList<>(blockSet);
    }

    public synchronized List<Block> getBlocksByParentHash(byte[] hash) {
        ByteArrayWrapper key = new ByteArrayWrapper(hash);

        Set<Block> blockSet = this.blocksbyparent.get(key);

        if (blockSet == null)
            blockSet = new HashSet<>();

        return new ArrayList<>(blockSet);
    }

    /**
     * getChildrenOf returns all the children of a list of blocks that are in the BlockStore.
     *
     * @param blocks the list of blocks to retrieve the children.
     * @return A list with all the children of the given list of blocks.
     */
    public List<Block> getChildrenOf(List<Block> blocks) {
        Set<Block> children = blocks.stream()
                .flatMap(b-> getBlocksByParentHash(new ByteArrayWrapper(b.getHash()).getData()).stream())
                .collect(Collectors.toSet());

        return children.stream()
                .collect(Collectors.toList());
    }

    public synchronized boolean hasBlock(Block block) {
        return this.blocks.containsKey(new ByteArrayWrapper(block.getHash()));
    }

    public synchronized boolean hasBlock(byte[] hash) {
        return this.blocks.containsKey(new ByteArrayWrapper(hash));
    }

    public synchronized int size() {
        return this.blocks.size();
    }

    public synchronized long minimalHeight() {
        long value = 0;

        for (Block b : this.blocks.values())
            if (value == 0 || b.getNumber() < value)
                value = b.getNumber();

        return value;
    }

    public synchronized long maximumHeight() {
        long value = 0;

        for (Block b : this.blocks.values())
            if (value == 0 || b.getNumber() > value)
                value = b.getNumber();

        return value;
    }

    public synchronized void releaseRange(long from, long to) {
        for (long k = from; k <= to; k++)
            for (Block b : this.getBlocksByNumber(k))
                this.removeBlock(b);
    }

    /**
     * hasHeader returns true if this block store has the header of the corresponding block.
     *
     * @param hash the block's hash.
     * @return true if the store has the header, false otherwise.
     */
    public synchronized boolean hasHeader(@Nonnull final byte[] hash) {
        return this.headers.containsKey(new ByteArrayWrapper(hash));
    }

    /**
     * saveHeader saves the given header into the block store.
     *
     * @param header the header to store.
     */
    public synchronized void saveHeader(@Nonnull final BlockHeader header) {
        ByteArrayWrapper key = new ByteArrayWrapper(header.getHash());
        ByteArrayWrapper pkey = new ByteArrayWrapper(header.getParentHash());
        Long nkey = Long.valueOf(header.getNumber());
        this.headers.put(key, header);

        Set<ByteArrayWrapper> hsbynumber = this.headersbynumber.get(nkey);
        if (hsbynumber == null) {
            hsbynumber = new HashSet<>();
            this.headersbynumber.put(nkey, hsbynumber);
        }
        hsbynumber.add(key);

        Set<ByteArrayWrapper> hsbyphash = this.headersbyparent.get(pkey);
        if (hsbyphash == null) {
            hsbyphash = new HashSet<>();
            this.headersbyparent.put(pkey, hsbyphash);
        }
        hsbyphash.add(key);
    }

    /**
     * removeHeader removes the given header from the block store.
     *
     * @param header the header to remove.
     */
    public synchronized void removeHeader(@Nonnull final BlockHeader header) {
        if (!this.hasHeader(header.getHash()))
            return;

        ByteArrayWrapper key = new ByteArrayWrapper(header.getHash());
        ByteArrayWrapper pkey = new ByteArrayWrapper(header.getParentHash());
        Long nkey = Long.valueOf(header.getNumber());

        this.headers.remove(key);

        Set<ByteArrayWrapper> byNumber = this.headersbynumber.get(nkey);
        if (byNumber != null) {
            byNumber.remove(key);
            if (byNumber.isEmpty()) {
                this.headersbynumber.remove(byNumber);
            }
        }

        Set<ByteArrayWrapper> byParent = this.headersbyparent.get(pkey);
        if (byParent != null) {
            byParent.remove(key);
            if (byParent.isEmpty()) {
                this.headersbyparent.remove(byParent);
            }
        }
    }
}
