package co.rsk.jmh.web3;

import co.rsk.jmh.web3.e2e.HttpRpcException;
import co.rsk.jmh.web3.plan.BlocksPlan;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 1)
@Measurement(iterations = 20)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 10)
public class BlocksAndTx {

    @Benchmark
    public void ethGetBlockByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetBlockByHash(blocksPlan.getBlockHash());
    }
}
