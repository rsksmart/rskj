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

import co.rsk.net.Status;
import org.ethereum.core.Block;
import org.ethereum.util.BIUtil;

import java.math.BigInteger;

/**
 * Created by ajlopez on 29/07/2016.
 */

public class BlockChainStatus {
    private Block bestBlock;
    private BigInteger totalDifficulty;

    public BlockChainStatus(Block bestBlock, BigInteger totalDifficulty)
    {
        this.bestBlock = bestBlock;
        this.totalDifficulty = totalDifficulty;
    }

    public Block getBestBlock() {
        return bestBlock;
    }

    public long getBestBlockNumber() {
        return bestBlock.getNumber();
    }

    public BigInteger getTotalDifficulty() {
        return totalDifficulty;
    }

    public boolean hasLowerTotalDifficultyThan(Status status) {
        return BIUtil.isLessThan(this.totalDifficulty, status.getTotalDifficulty());
    }
}
