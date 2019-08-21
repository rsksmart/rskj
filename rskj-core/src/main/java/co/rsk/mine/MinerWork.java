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

package co.rsk.mine;

import co.rsk.crypto.Keccak256;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;

/**
 * Work to do.
 * Requested by MinerClient to MinerServer
 * @author Oscar Guindzberg
 */
@Immutable
public class MinerWork {
    private final Keccak256 blockHashForMergedMining;
    private final byte[] target;
    private final boolean notify;

    public MinerWork(byte[] target,
                     boolean notify,
                     Keccak256 blockHashForMergedMining) {
        this.blockHashForMergedMining = blockHashForMergedMining;
        this.target = target;
        this.notify = notify;
    }

    public Keccak256 getBlockHashForMergedMining() {
        return blockHashForMergedMining;
    }

    public byte[] getTarget() {
        return target;
    }

    public boolean getNotify() {
        return notify;
    }
}
