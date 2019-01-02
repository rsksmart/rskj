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

package org.ethereum.config.blockchain.regtest;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.BlockHeader;

public class RegTestOrchidConfig extends RegTestGenesisConfig {

    @Override
    public boolean isRskip90() {
        return true;
    }

    @Override
    public boolean isRskip89() {
        return true;
    }

    @Override
    public boolean isRskip88() {
        return true;
    }

    @Override
    public boolean isRskip91() {
        return true;
    }

    @Override
    public boolean isRskip103() {
        return true;
    }

    @Override
    public boolean isRskip87() { return true; }

    @Override
    public boolean isRskip85() { return true; }

    @Override
    public boolean isRskip92() {
        return true;
    }

    @Override
    public boolean isRskip93() { return true; }

    @Override public boolean isRskip94() { return true; }

    @Override
    public boolean isRskip98() {
        return true;
    }

    //RSKIP97
    @Override
    public BlockDifficulty calcDifficulty(BlockHeader curBlock, BlockHeader parent) {
        return getBlockDifficulty(curBlock, parent, getConstants());
    }
}
