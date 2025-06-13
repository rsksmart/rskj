/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package co.rsk.net.messages;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;

import java.util.LinkedList;

/**
 * Holds cached data for SnapStatus requests.
 */
public record CachedSnapStatusData(
    Keccak256 blockHash,
    LinkedList<Block> blocks,
    LinkedList<BlockDifficulty> difficulties,
    long trieSize
) {
    @Override
    public String toString() {
        return "CachedSnapStatusData{" +
               "blockHash=" + blockHash +
               ", blocksSize=" + blocks.size() +
               ", difficultiesSize=" + difficulties.size() +
               ", trieSize=" + trieSize +
               '}';
    }
}
