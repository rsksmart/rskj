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
    public void ethGetLogsRandAndTopic(LogIndexPlan plan) throws HttpRpcException {
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
        EthFilter blockFilter = plan.getBockHashFilter();
        plan.getWeb3Connector().ethGetLogs(blockFilter);
    }


}
