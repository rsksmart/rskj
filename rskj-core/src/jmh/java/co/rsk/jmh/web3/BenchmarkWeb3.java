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

import co.rsk.jmh.Config;
import co.rsk.jmh.web3.e2e.Web3ConnectorE2E;
import co.rsk.jmh.web3.factory.TransactionFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;

// annotated fields at class, method or field level are providing default values that can be overriden via CLI or Runner parameters
@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 2, batchSize = 2)
@Measurement(iterations = 10, batchSize = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Timeout(time = 10)
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

        protected Config config;

        private Transaction transactionForEstimation;

        @Setup(Level.Trial)
        public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
            config = Config.create(network);

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

            transactionForEstimation = TransactionFactory.buildTransactionEstimation(config, BigInteger.ONE);
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

            String address = config.getString("sendTransaction.from");

            long nonce = Optional.ofNullable(web3Connector.ethGetTransactionCount(address))
                    .map(BigInteger::longValue)
                    .orElseThrow(() -> new BenchmarkWeb3Exception("Could not get account nonce"));

            long warmupIters = (long) params.getWarmup().getCount() * params.getWarmup().getBatchSize();
            long measurementIters = (long) params.getMeasurement().getCount() * params.getMeasurement().getBatchSize();
            long numOfTransactions = warmupIters + measurementIters;

            transactionsVT = TransactionFactory.createTransactions(TransactionFactory.TransactionType.VT, config, nonce, numOfTransactions).listIterator();
            transactionsContractCreation = TransactionFactory.createTransactions(TransactionFactory.TransactionType.CONTRACT_CREATION, config, nonce, numOfTransactions).listIterator();
            transactionsContractCall = TransactionFactory.createTransactions(TransactionFactory.TransactionType.CONTRACT_CALL, config, nonce, numOfTransactions).listIterator();
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws InterruptedException {
            waitForBlocks(config);
        }

    }

    @State(Scope.Benchmark)
    public static class DebugPlan extends BasePlan {

        private String transactionVT;
        private String transactionContractCreation;
        private String transactionContractCall;
        private String block;

        private final Map<String, String> debugParams = new HashMap<>();

        @Override
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
            super.setUp(params);

            long nonce = 0;

            String address = config.getNullableProperty("debug.txFrom");
            if (address != null) {
                nonce = Optional.ofNullable(web3Connector.ethGetTransactionCount(address))
                        .map(BigInteger::longValue)
                        .orElseThrow(() -> new BenchmarkWeb3Exception("Could not get account nonce"));
            }

            transactionVT = config.getNullableProperty("debug.transactionVT");
            if (transactionVT == null) {
                transactionVT = web3Connector.ethSendTransaction(TransactionFactory.buildTransactionVT(config, BigInteger.valueOf(nonce++)));
            }

            transactionContractCreation = config.getNullableProperty("debug.transactionContractCreation");
            if (transactionContractCreation == null) {
                transactionContractCreation = web3Connector.ethSendTransaction(TransactionFactory.buildTransactionContractCreation(config, BigInteger.valueOf(nonce++)));
            }

            transactionContractCall = config.getNullableProperty("debug.transactionContractCall");
            if (transactionContractCall == null) {
                transactionContractCall = web3Connector.ethSendTransaction(TransactionFactory.buildTransactionContractCall(config, BigInteger.valueOf(nonce)));
            }

            debugParams.put("disableMemory", "true");
            debugParams.put("disableStack", "true");
            debugParams.put("disableStorage", "true");

            try {
                waitForBlocks(config);
            } catch (InterruptedException e) { // NOSONAR
                throw new BenchmarkWeb3Exception("Error waiting for blocks: " + e.getMessage());
            }

            block = config.getNullableProperty("debug.block");
            if (block == null) {
                block = web3Connector.ethGetBlockByNumber(BigInteger.ONE); // naive, valid only for regtest mode
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() throws InterruptedException {
            // TODO(iago) revisit this while working on performance
            TimeUnit.SECONDS.sleep(10); // give node a rest after calling debug methods
        }

    }

    @Benchmark
    public void ethGetBalance(BasePlan plan) throws BenchmarkWeb3Exception {
        String address = plan.config.getString("address");
        plan.web3Connector.ethGetBalance(address, "latest");
    }

    @Benchmark
    public void ethBlockNumber(BasePlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.ethBlockNumber();
    }

    @Benchmark
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendRawTransaction(BasePlan plan) throws BenchmarkWeb3Exception {
        // We have decided to just test this method with a hardcoded tx (no nonce change, etc.) due to the complexity
        // of signing a raw tx (among other), and provided that the code for "sendRawTransaction" and "sendTransaction"
        // is the 90% same but for some previous validations that will be run with provided hardcoded tx
        // transaction nonce "issue" log expected on rskj logs for this call
        String rawTx = plan.config.getString("sendRawTransaction.tx");
        plan.web3Connector.ethSendRawTransaction(rawTx);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 30)
    public void ethGetLogsByBlockHash(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = plan.config.getString("getLogs.blockHash");
        plan.web3Connector.ethGetLogs(blockHash);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 30)
    public void ethGetLogsByBlockRange(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.config.getString("getLogs.fromBlock");
        String toBlock = plan.config.getString("getLogs.toBlock");
        String address = plan.config.getString("getLogs.address");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.web3Connector.ethGetLogs(fromDefaultBlockParameter, toDefaultBlockParameter, address);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 30)
    public void ethGetLogsByBlockRange_NullAddress(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.config.getString("getLogs.fromBlock");
        String toBlock = plan.config.getString("getLogs.toBlock");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.web3Connector.ethGetLogs(fromDefaultBlockParameter, toDefaultBlockParameter, null);
    }

    @Benchmark
    public void ethNewFilterByBlockHash(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = plan.config.getString("getLogs.blockHash");
        plan.web3Connector.ethNewFilter(blockHash);
    }

    @Benchmark
    public void ethNewFilterByBlockRange(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.config.getString("getLogs.fromBlock");
        String toBlock = plan.config.getString("getLogs.toBlock");
        String address = plan.config.getString("getLogs.address");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.web3Connector.ethNewFilter(fromDefaultBlockParameter, toDefaultBlockParameter, address);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 30)
    public void ethGetFilterChanges(BasePlan plan) throws BenchmarkWeb3Exception {
        // TODO(reynold) this generateNewFilterId has to be done on the setup
        String result = generateNewFilterId(plan);
        plan.web3Connector.ethGetFilterChanges(new BigInteger(result.replace("0x", ""), 16));
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 30)
    public void ethGetFilterLogs(BasePlan plan) throws BenchmarkWeb3Exception {
        // TODO(reynold) this generateNewFilterId has to be done on the setup
        String result = generateNewFilterId(plan);
        plan.web3Connector.ethGetFilterLogs(new BigInteger(result.replace("0x", ""), 16));
    }

    @Benchmark
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendTransaction_VT(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.transactionsVT.next();
        plan.web3Connector.ethSendTransaction(tx);
    }

    @Benchmark
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendTransaction_ContractCreation(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.transactionsContractCreation.next();
        plan.web3Connector.ethSendTransaction(tx);
    }

    @Benchmark
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 10, batchSize = TRANSACTION_BATCH_SIZE)
    public void ethSendTransaction_ContractCall(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.transactionsContractCall.next();
        plan.web3Connector.ethSendTransaction(tx);
    }

    @Benchmark
    public void ethEstimateGas(BasePlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.ethEstimateGas(plan.transactionForEstimation);
    }

    @Benchmark
    public void rskGetRawBlockHeaderByNumber(BasePlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.rskGetRawBlockHeaderByNumber("latest");
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceTransaction_VT(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceTransaction(plan.transactionVT);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceTransaction_ContractCreation(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceTransaction(plan.transactionContractCreation);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceTransaction_ContractCall(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceTransaction(plan.transactionContractCall);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceTransaction_Params_VT(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceTransaction(plan.transactionVT, plan.debugParams);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceTransaction_Params_ContractCreation(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceTransaction(plan.transactionContractCreation, plan.debugParams);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 10) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceTransaction_Params_ContractCall(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceTransaction(plan.transactionContractCall, plan.debugParams);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 5) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceBlockByHash(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceBlockByHash(plan.block);
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize = 5) // otherwise, node may become unresponsive
    @Timeout(time = 60)
    public void debugTraceBlockByHash_Params(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.web3Connector.debugTraceBlockByHash(plan.block);
    }

    private String generateNewFilterId(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = plan.config.getString("getLogs.blockHash");
        return Optional.ofNullable(plan.web3Connector.ethNewFilter(blockHash)).orElse("");
    }

    private static void waitForBlocks(Config config) throws InterruptedException {
        // wait for blocks to be mined so nonce is updated
        int blockTimeInSec = config.getInt("blockTimeInSec");
        int numOfBlocksToWait = config.getInt("blocksToWait");
        TimeUnit.SECONDS.sleep((long) blockTimeInSec * numOfBlocksToWait);
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
