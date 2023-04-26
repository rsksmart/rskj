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
import co.rsk.jmh.web3.factory.TransactionFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

// annotated fields at class, method or field level are providing default values that can be overriden via CLI or Runner parameters
@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 5, batchSize = 5)
@Measurement(iterations = 100, batchSize = 5)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Timeout(time = 20)
public class BenchmarkWeb3 {

    private static final int TRANSACTION_BATCH_SIZE = 16; // transaction.accountSlots = 16

    @State(Scope.Benchmark)
    public static class BasePlan {

        @Param("regtest")
        public String network;

        @Param({"E2E"})
        public Suites suite;

        @Param("http://localhost:4444")
        public String host;

        protected Web3ConnectorE2E web3Connector;

        protected Properties properties;

        private Transaction transactionForEstimation;

        @Setup(Level.Trial)
        public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
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

            transactionForEstimation = TransactionFactory.buildTransactionEstimation(properties, BigInteger.ONE);
        }

    }

    @State(Scope.Benchmark)
    public static class TransactionPlan extends BasePlan {

        private Iterator<Transaction> transactionsVT;
        private Iterator<Transaction> transactionsContractCreation;
        private Iterator<Transaction> transactionsContractCall;

        @Override
        @Setup(Level.Iteration)
        public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
            super.setUp(params);

            String address = properties.getProperty("sendTransaction.from");

            long nonce = Optional.ofNullable(web3Connector.ethGetTransactionCount(address))
                    .map(BigInteger::longValue)
                    .orElseThrow(() -> new BenchmarkWeb3Exception("Could not get account nonce"));

            long warmupIters = (long) params.getWarmup().getCount() * params.getWarmup().getBatchSize();
            long measurementIters = (long) params.getMeasurement().getCount() * params.getMeasurement().getBatchSize();
            long numOfTransactions = warmupIters + measurementIters;

            transactionsVT = TransactionFactory.createTransactions(TransactionFactory.TransactionType.VT, properties, nonce, numOfTransactions).listIterator();
            transactionsContractCreation = TransactionFactory.createTransactions(TransactionFactory.TransactionType.CONTRACT_CREATION, properties, nonce, numOfTransactions).listIterator();
            transactionsContractCall = TransactionFactory.createTransactions(TransactionFactory.TransactionType.CONTRACT_CALL, properties, nonce, numOfTransactions).listIterator();
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws InterruptedException {
            // wait for blocks to be mined so nonce is updated
            int blockTimeInSec = Integer.parseInt(properties.getProperty("blockTimeInSec"));
            long numOfBlocksToWait = 2; // wait for 2 blocks
            TimeUnit.SECONDS.sleep(blockTimeInSec * numOfBlocksToWait);
        }

    }

    @Benchmark
    public void ethGetBalance(BasePlan plan) throws BenchmarkWeb3Exception {
        String address = plan.properties.getProperty("address");
        plan.web3Connector.ethGetBalance(address, "latest");
    }

    @Benchmark
    public void ethBlockNumber(BasePlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.ethBlockNumber();
    }

    @Benchmark
    @Warmup(iterations = 3, batchSize = TRANSACTION_BATCH_SIZE)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendRawTransaction(BasePlan plan) throws BenchmarkWeb3Exception {
        // We have decided to just test this method with a hardcoded tx (no nonce change, etc.) due to the complexity
        // of signing a raw tx (among other), and provided that the code for "sendRawTransaction" and "sendTransaction"
        // is the 90% same but for some previous validations that will be run with provided hardcoded tx
        // "transaction nonce too low" log expected on rskj logs for this call
        String rawTx = plan.properties.getProperty("sendRawTransaction.tx");
        plan.web3Connector.ethSendRawTransaction(rawTx);
    }

    @Benchmark
    @Timeout(time = 60)
    public void ethGetLogsByBlockHash(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = (String) plan.properties.get("getLogs.blockHash");
        plan.web3Connector.ethGetLogs(blockHash);
    }

    @Benchmark
    @Timeout(time = 60)
    public void ethGetLogsByBlockRange(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = (String) plan.properties.get("getLogs.fromBlock");
        String toBlock = (String) plan.properties.get("getLogs.toBlock");
        String address = (String) plan.properties.get("getLogs.ethLogAddress");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.web3Connector.ethGetLogs(fromDefaultBlockParameter, toDefaultBlockParameter, address);
    }

    @Benchmark
    @Timeout(time = 60)
    public void ethNewFilterByBlockHash(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = (String) plan.properties.get("getLogs.blockHash");
        plan.web3Connector.ethNewFilter(blockHash);
    }

    @Benchmark
    @Timeout(time = 60)
    public void ethNewFilterByBlockRange(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = (String) plan.properties.get("getLogs.fromBlock");
        String toBlock = (String) plan.properties.get("getLogs.toBlock");
        String address = (String) plan.properties.get("getLogs.address");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.web3Connector.ethNewFilter(fromDefaultBlockParameter, toDefaultBlockParameter, address);
    }

    @Benchmark
    @Timeout(time = 60)
    public void ethGetFilterChanges(BasePlan plan) throws BenchmarkWeb3Exception {
        String result = generateNewFilterId(plan);
        plan.web3Connector.ethGetFilterChanges(new BigInteger(result.replace("0x", ""), 16));
    }

    @Benchmark
    @Timeout(time = 60)
    public void ethGetFilterLogs(BasePlan plan) throws BenchmarkWeb3Exception {
        String result = generateNewFilterId(plan);
        plan.web3Connector.ethGetFilterLogs(new BigInteger(result.replace("0x", ""), 16));
    }

    @Benchmark
    @Warmup(iterations = 3, batchSize = TRANSACTION_BATCH_SIZE)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendTransaction_VT(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.transactionsVT.next();
        plan.web3Connector.ethSendTransaction(tx);
    }

    @Benchmark
    @Warmup(iterations = 3, batchSize = TRANSACTION_BATCH_SIZE)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendTransaction_ContractCreation(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.transactionsContractCreation.next();
        plan.web3Connector.ethSendTransaction(tx);
    }

    @Benchmark
    @Warmup(iterations = 3, batchSize = TRANSACTION_BATCH_SIZE)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendTransaction_ContractCall(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.transactionsContractCall.next();
        plan.web3Connector.ethSendTransaction(tx);
    }

    @Benchmark
    public void ethEstimateGas(BasePlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.ethEstimateGas(plan.transactionForEstimation);
    }

    private String generateNewFilterId(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = (String) plan.properties.get("getLogs.blockHash");
        return Optional.ofNullable(plan.web3Connector.ethNewFilter(blockHash)).orElse("");
    }

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
