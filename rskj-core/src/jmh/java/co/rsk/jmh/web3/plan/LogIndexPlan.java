package co.rsk.jmh.web3.plan;

import co.rsk.jmh.Config;
import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;

import java.util.Collections;

@State(Scope.Benchmark)
public class LogIndexPlan extends BasePlan{

    private EthFilter blockRangeAndTopicFilter;
    private EthFilter bockHashFilter;
    private String txReceiptHash;

    @Setup(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);
        Config config = getConfiguration();

        long from = config.getLong("getLogs.fromBlock");
        long to = config.getLong("getLogs.toBlock");
        String topic = config.getString("getLogs.topic1");
        blockRangeAndTopicFilter = new EthFilter(new DefaultBlockParameterNumber(from), new DefaultBlockParameterNumber(to), Collections.emptyList());
        blockRangeAndTopicFilter.addSingleTopic(topic);

        String blockHash = config.getString("getLogs.blockHash");
        bockHashFilter = new EthFilter(blockHash);

        txReceiptHash = config.getString("getLogs.transactionHash");
    }

    public EthFilter getBlockRangeAndTopicFilter() {
        return blockRangeAndTopicFilter;
    }

    public String getTxReceiptHash() {
        return txReceiptHash;
    }

    public EthFilter getBockHashFilter() {
        return bockHashFilter;
    }
}
