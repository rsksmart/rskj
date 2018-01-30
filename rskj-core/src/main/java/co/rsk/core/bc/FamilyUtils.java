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

package co.rsk.core.bc;

import co.rsk.core.commons.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;

import javax.annotation.Nonnull;
import java.util.*;

import static java.lang.Math.max;

/**
 * Created by ajlopez on 12/08/2016.
 */
public class FamilyUtils {

    /**
     * Calculate the set of hashes of ancestors of a block
     *
     * @param blockStore    the block store to use
     * @param block         the block to use
     * @param limitNum      maximum number of ancestors to retrieve
     *
     * @return set of ancestors block hashes
     */
    public static Set<Keccak256> getAncestors(BlockStore blockStore, Block block, int limitNum) {
        return getAncestors(blockStore, block.getNumber(), block.getParentHash(), limitNum);
    }

    public static Set<Keccak256> getAncestors(BlockStore blockStore, long blockNumber, Keccak256 parentHash, int limitNum) {
        Set<Keccak256> ret = new HashSet<>();

        if (blockStore == null) {
            return ret;
        }

        int limit = (int) max(0, blockNumber - limitNum);
        Block it = blockStore.getBlockByHash(parentHash);

        while(it != null && it.getNumber() >= limit) {
            ret.add(it.getHash());
            it = blockStore.getBlockByHash(it.getParentHash());
        }

        return ret;
    }

    /**
     * Calculate the set of already used hashes in the chain of a block
     *
     * @param blockStore    the block store to use
     * @param block         the block to use
     * @param limitNum      maximum number of ancestors to examine
     *
     * @return set of already used uncles block hashes
     */
    public static Set<Keccak256> getUsedUncles(BlockStore blockStore, Block block, int limitNum) {
        return getUsedUncles(blockStore, block.getNumber(), block.getParentHash(), limitNum);
    }

    public static Set<Keccak256> getUsedUncles(BlockStore blockStore, long blockNumber, Keccak256 parentHash, int limitNum) {
        Set<Keccak256> ret = new HashSet<>();

        if (blockStore == null) {
            return ret;
        }

        long minNumber = max(0, blockNumber - limitNum);
        Block it = blockStore.getBlockByHash(parentHash);

        while(it != null && it.getNumber() >= minNumber) {
            for (BlockHeader uncle : it.getUncleList()) {
                ret.add(uncle.getHash());
            }
            it = blockStore.getBlockByHash(it.getParentHash());
        }

        return ret;
    }

    public static List<BlockHeader> getUnclesHeaders(BlockStore store, Block block, int levels) {
        return getUnclesHeaders(store, block.getNumber(), block.getParentHash(), levels);
    }

    public static List<BlockHeader> getUnclesHeaders(@Nonnull  BlockStore store, long blockNumber, Keccak256 parentHash, int levels) {
        List<BlockHeader> uncles = new ArrayList<>();
        Set<Keccak256> unclesHeaders = getUncles(store, blockNumber, parentHash, levels);

        for (Keccak256 uncleHash : unclesHeaders) {
            Block uncle = store.getBlockByHash(uncleHash);

            if (uncle != null) {
                uncles.add(uncle.getHeader());
            }
        }

        return uncles;
    }

    public static Set<Keccak256> getUncles(BlockStore store, Block block, int levels) {
        return getUncles(store, block.getNumber(), block.getParentHash(), levels);
    }

    public static Set<Keccak256> getUncles(BlockStore store, long blockNumber, Keccak256 parentHash, int levels) {
        Set<Keccak256> family = getFamily(store, blockNumber, parentHash, levels);
        Set<Keccak256> ancestors = getAncestors(store, blockNumber, parentHash, levels);
        family.removeAll(ancestors);
        family.removeAll(getUsedUncles(store, blockNumber, parentHash, levels));

        return family;
    }

    public static Set<Keccak256> getFamily(BlockStore store, Block block, int levels) {
        return getFamily(store, block.getNumber(), block.getParentHash(), levels);
    }

    public static Set<Keccak256> getFamily(BlockStore store, long blockNumber, Keccak256 parentHash, int levels) {
        long minNumber = max(0, blockNumber - levels);

        Set<Keccak256> family = new HashSet<>();
        List<Block> ancestors = new ArrayList<>();

        Block parent = store.getBlockByHash(parentHash);

        while (parent != null && parent.getNumber() >= minNumber) {
            ancestors.add(0, parent);
            parent = store.getBlockByHash(parent.getParentHash());
        }

        for (Block b : ancestors) {
            family.add(b.getHash());
        }

        for (int k = 1; k < ancestors.size(); k++) {
            Block ancestorParent = ancestors.get(k - 1);
            Block ancestor = ancestors.get(k);
            List<Block> uncles = store.getChainBlocksByNumber(ancestor.getNumber());

            for (Block uncle : uncles) {
                // TODO quick fix, the block storage should be reviewed
                if (uncle == null) {
                    continue;
                }

                if (!ancestorParent.getHash().equals(uncle.getParentHash())) {
                    continue;
                }

                if (ancestor.getHash().equals(uncle.getHash())) {
                    continue;
                }

                family.add(uncle.getHash());
            }
        }

        return family;
    }
}
