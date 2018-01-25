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

import co.rsk.crypto.Keccak256;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class Status {
    private long bestBlockNumber;
    private Keccak256 bestBlockHash;
    private Keccak256 bestBlockParentHash;
    private BigInteger totalDifficulty;

    public Status(long bestBlockNumber, Keccak256 bestBlockHash) {
        this.bestBlockNumber = bestBlockNumber;
        this.bestBlockHash = bestBlockHash;
    }

    public Status(long bestBlockNumber, Keccak256 bestBlockHash, Keccak256 bestBlockParentHash, BigInteger totalDifficulty) {
        this.bestBlockNumber = bestBlockNumber;
        this.bestBlockHash = bestBlockHash;
        this.bestBlockParentHash = bestBlockParentHash;
        this.totalDifficulty = totalDifficulty;
    }

    public long getBestBlockNumber() {
        return this.bestBlockNumber;
    }

    public Keccak256 getBestBlockHash() {
        return this.bestBlockHash;
    }

    @Nullable
    public Keccak256 getBestBlockParentHash() { return this.bestBlockParentHash; }

    @Nullable
    public BigInteger getTotalDifficulty() { return this.totalDifficulty; }
}
