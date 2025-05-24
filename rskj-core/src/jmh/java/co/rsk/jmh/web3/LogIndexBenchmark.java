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
package co.rsk.jmh.web3;

import co.rsk.jmh.web3.e2e.HttpRpcException;
import co.rsk.jmh.web3.plan.LogIndexPlan;
import org.openjdk.jmh.annotations.*;
import org.web3j.protocol.core.methods.request.EthFilter;

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.SingleShotTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10)
@Measurement(iterations = 1000)
@Timeout(time = 30)
public class LogIndexBenchmark {

    @Benchmark
    public void ethGetLogsRangeAndTopic(LogIndexPlan plan) throws HttpRpcException {
        EthFilter filter = plan.getBlockRangeAndTopicFilter();
        plan.getWeb3Connector().ethGetLogs(filter);
    }

    @Benchmark
    public void ethGetTransactionReceipt(LogIndexPlan plan) throws HttpRpcException {
        String txHsh = plan.getTxReceiptHash();
        plan.getWeb3Connector().ethGetTransactionReceipt(txHsh);
    }

    @Benchmark
    public void getLogsByBlockHash(LogIndexPlan plan) throws HttpRpcException {
        EthFilter blockFilter = plan.getBlockHashFilter();
        plan.getWeb3Connector().ethGetLogs(blockFilter);
    }
}
