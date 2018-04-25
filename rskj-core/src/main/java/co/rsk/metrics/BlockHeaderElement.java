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

package co.rsk.metrics;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.BlockHeader;

import java.util.Objects;

/**
 * Created by mario on 09/09/2016.
 */
public class BlockHeaderElement {

    private final BlockHeader blockHeader;
    private final BlockDifficulty difficulty;

    public BlockHeaderElement(BlockHeader blockHeader, BlockDifficulty difficulty) {
        this.blockHeader = blockHeader;
        this.difficulty = difficulty;
    }

    public BlockHeader getBlockHeader() {
        return blockHeader;
    }

    public BlockDifficulty getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        BlockHeaderElement that = (BlockHeaderElement) o;

        return Objects.equals(blockHeader, that.blockHeader) &&
                Objects.equals(difficulty, that.difficulty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockHeader, difficulty);
    }
}
