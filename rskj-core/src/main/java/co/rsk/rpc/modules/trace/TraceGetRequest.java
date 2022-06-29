/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc.modules.trace;


import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TraceGetRequest {
    private List<String> tracePositions;
    private String transactionHash;

    public TraceGetRequest(String transactionHash, List<String> tracePositions) {
        if (transactionHash == null || transactionHash.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("'transactionHash' cannot be null or empty");
        }

        if (tracePositions == null || tracePositions.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("'positions' cannot be null or empty");
        }

        if (tracePositions.size() > 1) {
            throw RskJsonRpcRequestException.invalidParamError("'positions' accepts only one index");
        }

        this.tracePositions = tracePositions;
        this.transactionHash = transactionHash;
    }

    public List<String> getTracePositions() {
        return tracePositions;
    }

    public void setTracePositions(List<String> tracePositions) {
        this.tracePositions = tracePositions;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public byte[] getTransactionHashAsByteArray() {
        return HexUtils.stringHexToByteArray(this.transactionHash);
    }

    public List<Integer> getTracePositionsAsListOfIntegers() {
        if (this.tracePositions == null) {
            return Stream.of(0).collect(Collectors.toList());
        }

        return this.tracePositions.stream()
                .map(HexUtils::stringHexToBigInteger)
                .map(BigInteger::intValue)
                .collect(Collectors.toList());
    }
}
