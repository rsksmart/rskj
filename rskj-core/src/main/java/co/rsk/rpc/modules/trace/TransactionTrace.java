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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionTrace {
    private final TraceAction action;
    private final String blockHash;
    private final long blockNumber;
    private final String transactionHash;
    private final int transactionPosition;
    private final String type;
    private final int subtraces;
    private final TraceAddress traceAddress;
    private final TraceResult result;
    private final String error;

    public TransactionTrace(
            TraceAction action,
            String blockHash,
            long blockNumber,
            String transactionHash,
            int transactionPosition,
            String type,
            int subtraces,
            TraceAddress traceAddress,
            TraceResult result,
            String error
    ) {
        this.action = action;
        this.blockHash = blockHash;
        this.blockNumber = blockNumber;
        this.transactionHash = transactionHash;
        this.transactionPosition = transactionPosition;
        this.type = type;
        this.subtraces = subtraces;
        this.traceAddress = traceAddress;
        this.result = result;
        this.error = error;
    }

    @JsonGetter("action")
    public TraceAction getAction() {
        return this.action;
    }

    @JsonGetter("blockHash")
    public String getBlockHash() {
        return this.blockHash;
    }

    @JsonGetter("blockNumber")
    public long getBlockNumber() {
        return this.blockNumber;
    }

    @JsonGetter("transactionHash")
    public String getTransactionHash() {
        return this.transactionHash;
    }

    @JsonGetter("result")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public TraceResult getResult() { return this.result; }

    @JsonGetter("transactionPosition")
    public int getTransactionPosition() {
        return this.transactionPosition;
    }

    @JsonGetter("subtraces")
    public int getSubtraces() { return this.subtraces; }

    @JsonGetter("traceAddress")
    public TraceAddress getTraceAddress() { return this.traceAddress; }

    @JsonGetter("type")
    public String getType() {
        return this.type;
    }

    @JsonGetter("error")
    public String getError() { return this.error; }
}
