package co.rsk.jmh.web3.plan;

import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(Scope.Benchmark)
public class TracePlan extends BasePlan {
    private String fromBlock;
    private String toBlock;
    private List<String> fromAddresses;
    private List<String> toAddresses;
    private String transactionHash;
    private List<String> positions;

    @Override
    @Setup(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);

        this.fromBlock = getConfiguration().getString("trace.fromBlock");
        this.toBlock = getConfiguration().getString("trace.toBlock");

        this.fromAddresses = Stream.of(
                (getConfiguration().getString("trace.fromAddresses"))
                        .split(",")
        ).collect(Collectors.toList());

        this.toAddresses = Stream.of(
                (getConfiguration().getString("trace.toAddresses"))
                        .split(",")
        ).collect(Collectors.toList());

        this.transactionHash = getConfiguration().getString("trace.transactionHash");

        this.positions = Stream.of("0x0").collect(Collectors.toList());
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public String getToBlock() {
        return toBlock;
    }

    public List<String> getFromAddresses() {
        return fromAddresses;
    }

    public List<String> getToAddresses() {
        return toAddresses;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public List<String> getPositions() {
        return positions;
    }
}
