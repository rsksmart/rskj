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

package co.rsk.logfilter;

import org.ethereum.core.Bloom;

/**
 * Created by ajlopez on 29/01/2019.
 */
public class BlocksBloom {
    private final Bloom bloom;
    private long fromBlock = -1;
    private long toBlock = -1;

    public BlocksBloom() {
        this.bloom = new Bloom();
        this.fromBlock = -1;
        this.toBlock = -1;
    }

    public BlocksBloom(long fromBlock, long toBlock, Bloom bloom) {
        this.bloom = bloom;
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
    }

    public Bloom getBloom() { return this.bloom; }

    public long fromBlock() { return this.fromBlock; }

    public long toBlock() { return this.toBlock; }

    public long size() {
        if (this.fromBlock == -1) {
            return 0;
        }

        return this.toBlock - this.fromBlock + 1;
    }

    public void addBlockBloom(long blockNumber, Bloom blockBloom) {
        if (fromBlock == -1) {
            fromBlock = blockNumber;
            toBlock = blockNumber;
        }
        else if (blockNumber == toBlock + 1) {
            toBlock = blockNumber;
        }
        else {
            throw new UnsupportedOperationException("Block out of sequence");
        }

        this.bloom.or(blockBloom);
    }

    public boolean matches(Bloom bloom) {
        return this.bloom.matches(bloom);
    }
}
