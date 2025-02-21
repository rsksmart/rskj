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

import co.rsk.core.types.bytes.Bytes;
import co.rsk.core.types.bytes.BytesSlice;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import javax.annotation.Nonnull;
import java.util.Objects;

public class SuperBridgeEvent {
    private final BytesSlice operatorId;
    private final BytesSlice utxoId;

    public SuperBridgeEvent(@Nonnull BytesSlice operatorId, @Nonnull BytesSlice utxoId) {
        this.operatorId = Objects.requireNonNull(operatorId);
        this.utxoId = Objects.requireNonNull(utxoId);
    }

    public BytesSlice getOperatorId() {
        return this.operatorId;
    }

    public BytesSlice getUtxoId() {
        return this.utxoId;
    }

    public byte[] getEncoded() {
        return RLP.encodeList(
                RLP.encodeElement(this.operatorId.copyArray()),
                RLP.encodeElement(this.utxoId.copyArray())
        );
    }

    public static SuperBridgeEvent decode(byte[] data) {
        RLPList rlpList = (RLPList) RLP.decode2(data).get(0);
        checkRLPSize(rlpList);

        byte[] operatorId = rlpList.get(0).getRLPRawData();
        checkValueSize("operatorId", operatorId);

        byte[] utxoId = rlpList.get(1).getRLPRawData();
        checkValueSize("utxoId", utxoId);

        return new SuperBridgeEvent(
                Bytes.of(operatorId),
                Bytes.of(utxoId)
        );
    }

    private static void checkRLPSize(RLPList rlpList) {
        if (rlpList.size() != 2) {
            throw new DecodeException("Invalid number of RLP elements: " + rlpList.size());
        }
    }

    private static void checkValueSize(String valueName, byte[] value) {
        if (value.length != 32) {
            throw new DecodeException("Invalid size for " + valueName + ": " + value.length);
        }
    }

    public static class DecodeException extends IllegalArgumentException {
        public DecodeException(String message) {
            super(message);
        }
    }
}
