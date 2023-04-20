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

import co.rsk.jmh.ConfigHelper;
import co.rsk.jmh.web3.e2e.Web3ConnectorE2E;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

// annotated fields at class, method or field level are providing default values that can be overriden via CLI or Runner parameters
@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 1, batchSize = 5)
@Measurement(iterations = 100, batchSize = 5)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Timeout(time = 20)
public class BenchmarkWeb3 {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkWeb3.class);

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        @Param("regtest")
        public String network;

        @Param({"E2E"})
        public Suites suite;

        @Param("http://localhost:4444")
        public String host;

        @Param("true")
        public boolean logEnabled;

        private Web3ConnectorE2E web3Connector;

        private Properties properties;

        @Setup(Level.Trial)
        public void setUp() throws BenchmarkWeb3Exception {
            properties = ConfigHelper.build(network);

            switch (suite) {
                case E2E:
                    web3Connector = Web3ConnectorE2E.create(host);
                    break;
                case INT:
                case UNIT:
                    throw new BenchmarkWeb3Exception("Suite not implemented yet: " + suite);
                default:
                    throw new BenchmarkWeb3Exception("Unknown suite: " + suite);
            }
        }

    }

    @Benchmark
    public void ethGetBalance(ExecutionPlan plan) throws BenchmarkWeb3Exception {
        String address = (String) plan.properties.get("address");

        BigInteger balance = plan.web3Connector.ethGetBalance(address, "latest");
        if (plan.logEnabled) {
            logger.info("ethGetBalance response: {}", balance);
        }
    }

    @Benchmark
    public void ethBlockNumber(ExecutionPlan plan) throws BenchmarkWeb3Exception {
        String blockNumber = plan.web3Connector.ethBlockNumber();
        if (plan.logEnabled) {
            logger.info("ethBlockNumber response: {}", blockNumber);
        }
    }

//    @Benchmark
//    public void ethSendRawTransaction(ExecutionPlan plan) throws BenchmarkWeb3Exception {
//        String txHash = plan.web3Connector.ethSendRawTransaction();
//        if (plan.logEnabled) {
//            System.out.println("ethSendRawTransaction response: " + txHash);
//        }
//    }

    public enum Suites {
        // performing actual RPC calls to a running node (this node should be disconnected from other nodes, etc.)
        E2E,

        // TODO:
        //  calling org.ethereum.rpc.Web3Impl.Web3Impl methods directly, this will require spinning un a potentially
        //  simplified RSKj node with a RskContext and some preloaded data
        INT,

        // TODO:
        // calling org.ethereum.rpc.Web3Impl.Web3Impl methods directly to unitarily benchmark them, potentially mocking
        // any dependency that is not relevant for the measurement
        UNIT
    }

}