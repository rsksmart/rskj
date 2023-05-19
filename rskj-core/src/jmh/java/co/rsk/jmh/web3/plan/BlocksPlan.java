package co.rsk.jmh.web3.plan;

import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.math.BigInteger;

@State(Scope.Benchmark)
public class BlocksPlan extends BasePlan {
    private String blockHash;
    private BigInteger blockNumber;
    private String txHash;
    private int txIndex;
    //TODO get a valid address
    private String address;
    private BigInteger uncleIndex;

    @Override
    @Setup(Level.Trial)
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);
        try {
            initParams();
        } catch (Exception e) {
            throw new BenchmarkWeb3Exception("Could not initialize plan. ", e);
        }
    }

    private void initParams() {
        blockHash = configuration.getString("block.hash");
        blockNumber = BigInteger.valueOf(configuration.getLong("block.number"));
        txHash = configuration.getString("tx.hash");
        txIndex = configuration.getInt("tx.index");
        address = configuration.getString("address");
        uncleIndex = BigInteger.valueOf(configuration.getInt("uncle.index"));
    }

    public String getBlockHash() {
        return blockHash;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }

    public String getTxHash() {
        return txHash;
    }

    public int getTxIndex() {
        return txIndex;
    }

    public String getAddress() {
        return address;
    }

    public BigInteger getUncleIndex() {
        return uncleIndex;
    }
}
