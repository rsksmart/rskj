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

package co.rsk.jmh.web3;

import co.rsk.jmh.web3.e2e.Web3ConnectorE2E;
import org.openjdk.jmh.annotations.*;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

// TODO(iago) create README.md with run modes, Gradle tasks, etc.

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime) // TODO(iago) check more modes
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 1, time = 1)
@Timeout(time = 20)
public class BenchmarkWeb3 {

    // to ensure we are explicitly providing a value when running
    private static final String PARAM_DEFAULT_NONE = "-1";

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        private static final String SUITE_E2E = "e2e";

        private static final String SUITE_INTEGRATION = "int";

        @Param({PARAM_DEFAULT_NONE})
        public String suite;

        @Param({PARAM_DEFAULT_NONE})
        public String host;

        @Param({"false"})
        public boolean logEnabled;

        private Web3ConnectorE2E web3Connector;

        @Setup(Level.Invocation)
        public void setUp() throws BenchmarkWeb3Exception {
            if (SUITE_E2E.equals(suite)) {
                web3Connector = Web3ConnectorE2E.create(host);
            } else if (SUITE_INTEGRATION.equals(suite)) {
                throw new UnsupportedOperationException("Not implemented yet");
            } else {
                throw new BenchmarkWeb3Exception("Unknown suite: " + suite);
            }
        }

    }

    @Benchmark
    public void ethGetBalance(ExecutionPlan plan) throws BenchmarkWeb3Exception {
        // TODO(iago) think the best way to set up params like addresses so it always work
        BigInteger balance = plan.web3Connector.ethGetBalance("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826", "latest");
        if (plan.logEnabled) {
            System.out.println("ethGetBalance response: " + balance);
        }
    }

    @Benchmark
    public void ethBlockNumber(ExecutionPlan plan) throws BenchmarkWeb3Exception {
        String blockNumber = plan.web3Connector.ethBlockNumber();
        if (plan.logEnabled) {
            System.out.println("ethBlockNumber response: " + blockNumber);
        }
    }

//    @Benchmark
//    public void ethSendRawTransaction(ExecutionPlan plan) throws BenchmarkWeb3Exception {
//        String txHash = plan.web3Connector.ethSendRawTransaction();
//        if (plan.logEnabled) {
//            System.out.println("ethSendRawTransaction response: " + txHash);
//        }
//    }

}