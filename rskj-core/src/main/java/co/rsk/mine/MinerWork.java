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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Work to do.
 * Requested by MinerClient to MinerServer
 * @author Oscar Guindzberg
 */
@Immutable
public class MinerWork {
    private final String blockHashForMergedMining;
    private final String target;
    private final String feesPaidToMiner;
    private final boolean notify;
    private final String parentBlockHash;

    public MinerWork(@Nonnull final String blockHashForMergedMining, @Nonnull final String target,
                     final String paidFees, final boolean notify, @Nonnull final String parentBlockHash) {
        this.blockHashForMergedMining = blockHashForMergedMining;
        this.target = target;
        this.feesPaidToMiner = paidFees;
        this.notify = notify;
        this.parentBlockHash = parentBlockHash;
    }

    public String getBlockHashForMergedMining() {
        return blockHashForMergedMining;
    }

    public String getTarget() {
        return target;
    }

    public String getFeesPaidToMiner() {
        return feesPaidToMiner;
    }

    public boolean getNotify() {
        return notify;
    }

    public String getParentBlockHash() {
        return parentBlockHash;
    }
}
