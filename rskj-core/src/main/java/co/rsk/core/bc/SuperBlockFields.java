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

package co.rsk.core.bc;

import co.rsk.core.types.bytes.BytesSlice;
import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;

public class SuperBlockFields {

    private final BytesSlice parentHash;
    private final long blockNumber;
    private final SuperBridgeEvent bridgeEvent;

    public SuperBlockFields(@Nullable BytesSlice parentHash, long blockNumber, @Nullable SuperBridgeEvent bridgeEvent) {
        this.parentHash = parentHash;
        this.blockNumber = blockNumber;
        this.bridgeEvent = bridgeEvent;
    }

    public BytesSlice getParentHash() {
        return parentHash;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public SuperBridgeEvent getBridgeEvent() {
        return bridgeEvent;
    }

    public byte[] getEncoded() {
        return RLP.encodeList(
                RLP.encodeElement(parentHash != null ? parentHash.copyArray() : null),
                RLP.encodeBigInteger(BigInteger.valueOf(blockNumber)),
                RLP.encodeElement(bridgeEvent != null ? bridgeEvent.getEncoded() : null)
        );
    }

    @Nonnull
    public Keccak256 getHash() {
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(parentHash != null ? parentHash.copyArray() : null),
                RLP.encodeBigInteger(BigInteger.valueOf(blockNumber)),
                RLP.encodeElement(bridgeEvent != null ? bridgeEvent.getEncoded() : null)
        );

        return new Keccak256(HashUtil.keccak256(encoded));
    }
}
