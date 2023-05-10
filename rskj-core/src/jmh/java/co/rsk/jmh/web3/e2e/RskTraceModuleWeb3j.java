/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh.web3.e2e;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.core.Request;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RskTraceModuleWeb3j extends JsonRpc2_0Web3j {

    public static class TraceFilterRequest {
        private String fromBlock;
        private String toBlock;
        private List<String> fromAddress;
        private List<String> toAddress;
        private Integer after;
        private Integer count = 10_000;

        public TraceFilterRequest() {
        }

        public TraceFilterRequest(String fromBlock, String toBlock) {
            this.fromBlock = fromBlock;
            this.toBlock = toBlock;
        }

        public TraceFilterRequest(String fromBlock, String toBlock, List<String> fromAddress, List<String> toAddress) {
            this.fromBlock = fromBlock;
            this.toBlock = toBlock;
            this.fromAddress = fromAddress;
            this.toAddress = toAddress;
        }

        public void setFromBlock(String fromBlock) {
            this.fromBlock = fromBlock;
        }

        public void setToBlock(String toBlock) {
            this.toBlock = toBlock;
        }

        public void setFromAddress(List<String> fromAddress) {
            this.fromAddress = fromAddress;
        }

        public void setToAddress(List<String> toAddress) {
            this.toAddress = toAddress;
        }

        public void setAfter(Integer after) {
            this.after = after;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public String getFromBlock() {
            return fromBlock;
        }

        public String getToBlock() {
            return toBlock;
        }

        public List<String> getFromAddress() {
            return this.fromAddress;
        }

        public List<String> getToAddress() {
            return toAddress;
        }

        public Integer getAfter() {
            return after;
        }

        public Integer getCount() {
            return count;
        }
    }

    public RskTraceModuleWeb3j(Web3jService web3jService) {
        super(web3jService);
    }

    public Request<?, RskWeb3j.GenericJsonResponse> traceTransaction(String transactionHash) {
        return new Request<>("trace_transaction", Collections.singletonList(transactionHash), web3jService,  RskWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskWeb3j.GenericJsonResponse> traceBlock(String blockHash) {
        return new Request<>("trace_block", Collections.singletonList(blockHash), web3jService,  RskWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskWeb3j.GenericJsonResponse> traceFilter(TraceFilterRequest request) {
        return new Request<>("trace_filter", Collections.singletonList(request), web3jService,  RskWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskWeb3j.GenericJsonResponse> traceGet(String transactionHash, List<String> positions) {
        return new Request<>("trace_get", Arrays.asList(transactionHash, positions), web3jService,  RskWeb3j.GenericJsonResponse.class);
    }
}
