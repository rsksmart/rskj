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

import co.rsk.core.RskAddress;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.core.types.bytes.BytesSlice;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.rpc.AddressesTopicsFilter;
import org.ethereum.rpc.Topic;
import org.ethereum.solidity.SolidityType;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public class SuperBridgeEvent {

    public static final String NAME = "super_bridge_event";

    public static final CallTransaction.Function SIGNATURE = CallTransaction.Function.fromEventSignature(NAME, new CallTransaction.Param[]{
            new CallTransaction.Param(true, "operatorId", SolidityType.getType("bytes32")),
            new CallTransaction.Param(true, "utxoId", SolidityType.getType("bytes32"))
    });

    public static final AddressesTopicsFilter FILTER = new AddressesTopicsFilter(
            new RskAddress[] { PrecompiledContracts.BRIDGE_ADDR },
            new Topic[][] {{ new Topic(SIGNATURE.encodeSignatureLong()) }}
    );

    private static final Function<TransactionReceipt, SuperBridgeEvent> FIND_EVENT_FN = (TransactionReceipt txReceipt) -> {
        for (LogInfo logInfo : txReceipt.getLogInfoList()) {
            if (SuperBridgeEvent.FILTER.matchesExactly(logInfo)) {
                return makeSuperBridgeEvent(logInfo);
            }
        }
        return null;
    };

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

    @Nullable
    public static SuperBridgeEvent decode(@Nullable byte[] data) {
        if (data == null) {
            return null;
        }

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

    public static boolean equalEvents(@Nullable SuperBridgeEvent event1, @Nullable SuperBridgeEvent event2) {
        if (event1 == null) {
            return event2 == null;
        }
        if (event2 == null) {
            return false;
        }
        return Bytes.equalByteSlices(event1.operatorId, event2.operatorId) && Bytes.equalByteSlices(event1.utxoId, event2.utxoId);
    }

    public static SuperBridgeEvent findEvent(Stream<TransactionReceipt> receiptsStream) {
        return receiptsStream
                .map(FIND_EVENT_FN)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static SuperBridgeEvent makeSuperBridgeEvent(LogInfo logInfo) {
        Object[] args = SuperBridgeEvent.SIGNATURE.decodeEventData(logInfo.getData());
        return new SuperBridgeEvent(Bytes.of((byte[]) (args[0])), Bytes.of((byte[]) (args[1])));
    }
}
