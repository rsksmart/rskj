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

import co.rsk.jmh.web3.e2e.RskModuleWeb3j;
import co.rsk.jmh.web3.plan.*;
import org.openjdk.jmh.annotations.*;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthAccounts;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthHashrate;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO Try maybe some of the methods with org.openjdk.jmh.annotations.Measurement.batchSize, to test simultaneous calls

// annotated fields at class, method or field level are providing default values that can be overriden via CLI or Runner parameters
@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 1)
@Measurement(iterations = 20)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 10)
public class BenchmarkWeb3 {

    private static final int TRANSACTION_ACCOUNT_SLOTS = 16; // transaction.accountSlots = 16

    @Benchmark
    @Timeout(time = 60)
    public void ethCallForSpecificBlock(TransactionPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().ethCall(plan.getEthCallArguments(), plan.getConfiguration().getString("eth.blockNumber"));
    }

    @Benchmark
    @Timeout(time = 60)
    public void ethCallForPendingBlock(TransactionPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().ethCall(plan.getEthCallArguments(), "pending");
    }

    @Benchmark
    public void ethGetBalance(BasePlan plan) throws BenchmarkWeb3Exception {
        String address = plan.getConfiguration().getString("address");
        plan.getWeb3Connector().ethGetBalance(address, "latest");
    }

    @Benchmark
    public void ethBlockNumber(BasePlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().ethBlockNumber();
    }

    @Benchmark
    @Measurement(iterations = TRANSACTION_ACCOUNT_SLOTS)
    public void ethSendRawTransaction(BasePlan plan) throws BenchmarkWeb3Exception {
        // We have decided to just test this method with a hardcoded tx (no nonce change, etc.) due to the complexity
        // of signing a raw tx (among other), and provided that the code for "sendRawTransaction" and "sendTransaction"
        // is the 90% same but for some previous validations that will be run with provided hardcoded tx
        // transaction nonce "issue" log expected on rskj logs for this call
        String rawTx = plan.getConfiguration().getString("sendRawTransaction.tx");
        plan.getWeb3Connector().ethSendRawTransaction(rawTx);
    }

    @Benchmark
    @Timeout(time = 30)
    public void ethGetLogsByBlockHash(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = plan.getConfiguration().getString("getLogs.blockHash");
        plan.getWeb3Connector().ethGetLogs(blockHash);
    }

    @Benchmark
    @Timeout(time = 30)
    public void ethGetLogsByBlockRange(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.getConfiguration().getString("getLogs.fromBlock");
        String toBlock = plan.getConfiguration().getString("getLogs.toBlock");
        String address = plan.getConfiguration().getString("getLogs.address");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.getWeb3Connector().ethGetLogs(fromDefaultBlockParameter, toDefaultBlockParameter, address);
    }

    @Benchmark
    @Timeout(time = 30)
    public void ethGetLogsByBlockRange_NullAddress(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.getConfiguration().getString("getLogs.fromBlock");
        String toBlock = plan.getConfiguration().getString("getLogs.toBlock");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.getWeb3Connector().ethGetLogs(fromDefaultBlockParameter, toDefaultBlockParameter, null);
    }

    @Benchmark
    public void ethNewFilterByBlockHash(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = plan.getConfiguration().getString("getLogs.blockHash");
        plan.getWeb3Connector().ethNewFilter(blockHash);
    }

    @Benchmark
    public void ethNewFilterByBlockRange(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.getConfiguration().getString("getLogs.fromBlock");
        String toBlock = plan.getConfiguration().getString("getLogs.toBlock");
        String address = plan.getConfiguration().getString("getLogs.address");

        DefaultBlockParameter fromDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(fromBlock));
        DefaultBlockParameter toDefaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(toBlock));

        plan.getWeb3Connector().ethNewFilter(fromDefaultBlockParameter, toDefaultBlockParameter, address);
    }

    @Benchmark
    @Timeout(time = 30)
    public void ethGetFilterChanges(GetLogsPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().ethGetFilterChanges(new BigInteger(plan.getEthFilterId().replace("0x", ""), 16));
    }

    @Benchmark
    @Timeout(time = 30)
    public void ethGetFilterLogs(GetLogsPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().ethGetFilterLogs(new BigInteger(plan.getEthFilterId().replace("0x", ""), 16));
    }

    @Benchmark
    public void ethEstimateGas(EstimatePlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().ethEstimateGas(plan.getTransactionForEstimation());
    }

    @Benchmark
    public void rskGetRawBlockHeaderByNumber(BasePlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().rskGetRawBlockHeaderByNumber("latest");
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceTransaction_VT(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceTransaction(plan.getTransactionVT());
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceTransaction_ContractCreation(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceTransaction(plan.getTransactionContractCreation());
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceTransaction_ContractCall(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceTransaction(plan.getTransactionContractCall());
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceTransaction_Params_VT(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceTransaction(plan.getTransactionVT(), plan.getDebugParams());
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceTransaction_Params_ContractCreation(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceTransaction(plan.getTransactionContractCreation(), plan.getDebugParams());
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceTransaction_Params_ContractCall(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceTransaction(plan.getTransactionContractCall(), plan.getDebugParams());
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceBlockByHash(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceBlockByHash(plan.getBlock());
    }

    @Benchmark
    @Timeout(time = 30)
    public void debugTraceBlockByHash_Params(DebugPlan plan) throws BenchmarkWeb3Exception {
        plan.getWeb3Connector().debugTraceBlockByHash(plan.getBlock());
    }

    @Benchmark
    public void traceTransaction(BasePlan plan) throws BenchmarkWeb3Exception {
        String transactionHash = plan.getConfiguration().getString("trace.transactionHash");
        plan.getWeb3Connector().traceTransaction(transactionHash);
    }

    @Benchmark
    public void traceBlock(BasePlan plan) throws BenchmarkWeb3Exception {
        String blockHash = plan.getConfiguration().getString("trace.blockHash");
        plan.getWeb3Connector().traceBlock(blockHash);
    }

    @Benchmark
    @Timeout(time = 60)
    public void traceFilterBetweenBlockRange(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.getConfiguration().getString("trace.fromBlock");
        String toBlock = plan.getConfiguration().getString("trace.toBlock");
        plan.getWeb3Connector().traceFilter(fromBlock, toBlock);
    }

    @Benchmark
    @Timeout(time = 60)
    public void traceFilterBetweenAddresses(BasePlan plan) throws BenchmarkWeb3Exception {
        String fromBlock = plan.getConfiguration().getString("trace.fromBlock");
        String toBlock = plan.getConfiguration().getString("trace.toBlock");
        List<String> fromAddresses = Stream.of(
                (plan.getConfiguration().getString("trace.fromAddresses"))
                        .split(",")
        ).collect(Collectors.toList());
        List<String> toAddresses = Stream.of(
                (plan.getConfiguration().getString("trace.toAddresses"))
                        .split(",")
        ).collect(Collectors.toList());
        plan.getWeb3Connector().traceFilter(fromBlock, toBlock, fromAddresses, toAddresses);
    }

    @Benchmark
    public void traceGet(BasePlan plan) throws BenchmarkWeb3Exception {
        String transactionHash = plan.getConfiguration().getString("trace.transactionHash");
        plan.getWeb3Connector().traceGet(transactionHash, Stream.of("0x0").collect(Collectors.toList()));
    }


    @Benchmark
    public void ethSign(BasePlan plan) throws BenchmarkWeb3Exception {
        String address = plan.getEthMethodsConfig().getEthSignAddress();
        String message = plan.getEthMethodsConfig().getEthSignMessage();
        plan.getWeb3Connector().ethSign(address, message);
    }

    @Benchmark
    public void ethGetStorageAt(BasePlan plan) throws BenchmarkWeb3Exception {
        String address = plan.getEthMethodsConfig().getEthGetStorageAtAddress();
        Long position = plan.getEthMethodsConfig().getEthGetStorageAtPosition();
        plan.getWeb3Connector().ethGetStorageAt(address, BigInteger.valueOf(position), DefaultBlockParameter.valueOf("latest"));
    }

    @Benchmark
    public void ethGetCode(BasePlan plan) throws BenchmarkWeb3Exception {
        String address = plan.getEthMethodsConfig().getEthGetCodeAddress();
        DefaultBlockParameter defaultBlockParameter = plan.getEthMethodsConfig().getLatestBlock();
        plan.getWeb3Connector().ethGetCode(address, defaultBlockParameter);
    }

    @Benchmark
    @Timeout(time = 30)
    public EthAccounts ethAccounts(BasePlan plan) throws BenchmarkWeb3Exception {
        return plan.getWeb3Connector().ethAccounts();
    }

    @Benchmark
    @Timeout(time = 30)
    public EthHashrate ethHashrate(BasePlan plan) throws BenchmarkWeb3Exception {
        return plan.getWeb3Connector().ethHashrate();
    }

    @Benchmark
    @Timeout(time = 30)
    public EthGasPrice ethGasPrice(BasePlan plan) throws BenchmarkWeb3Exception {
        return plan.getWeb3Connector().ethGasPrice();
    }
    @Benchmark
    @Timeout(time = 30)
    public RskModuleWeb3j.GenericJsonResponse ethBridgeState(BasePlan plan) throws BenchmarkWeb3Exception {
        return plan.getWeb3Connector().ethBridgeState();
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
