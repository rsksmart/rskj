package co.rsk.jmh.web3;

import co.rsk.jmh.web3.e2e.HttpRpcException;
import co.rsk.jmh.web3.plan.BlocksPlan;
import org.openjdk.jmh.annotations.*;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 1)
@Measurement(iterations = 20)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 10)
public class BlocksAndTx {

    @Benchmark
    public void ethGetBlockByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        Response<EthBlock.Block> response = blocksPlan.getWeb3Connector().ethGetBlockByHash(blocksPlan.getBlockHash());
        if (response.getResult() == null || !response.getResult().getHash().contentEquals(blocksPlan.getBlockHash())) {
            throw new RuntimeException("Block hash does not match");
        }
    }

    @Benchmark
    public void ethGetBlockByNumber(BlocksPlan blocksPlan) throws HttpRpcException {
        EthBlock response = blocksPlan.getWeb3Connector().ethGetBlockByNumber(blocksPlan.getBlockNumber());
        if (response.getResult() == null || !response.getResult().getNumber().equals(blocksPlan.getBlockNumber())) {
            throw new RuntimeException("Block hash does not match");
        }
    }

    @Benchmark
    public void ethGetTransactionByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetTransactionByHash(blocksPlan.getTxHash());
    }

    @Benchmark
    public void ethGetTransactionByBlockHashAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetTransactionByBlockHashAndIndex(blocksPlan.getBlockHash(), blocksPlan.getTxIndex());
    }

    @Benchmark
    public void ethGetTransactionByBlockNumberAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetTransactionByBlockNumberAndIndex(blocksPlan.getBlockNumber(), blocksPlan.getTxIndex());
    }

    @Benchmark
    public void ethGetTransactionReceipt(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetTransactionReceipt(blocksPlan.getTxHash());
    }

    @Benchmark
    public void ethGetTransactionCount(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetTransactionCount(blocksPlan.getAddress(), blocksPlan.getBlockNumber());
    }

    @Benchmark
    public void ethGetTransactionCountByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetTransactionCountByHash(blocksPlan.getBlockHash());
    }

    @Benchmark
    public void ethGetBlockTransactionCountByNumber(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetBlockTransactionCountByNumber(blocksPlan.getBlockNumber());
    }

    @Benchmark
    public void ethGetUncleCountByBlockHash(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetUncleCountByBlockHash(blocksPlan.getBlockHash());
    }

    @Benchmark
    public void ethGetUncleCountByBlockNumber(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetUncleCountByBlockNumber(blocksPlan.getBlockNumber());
    }

    @Benchmark
    public void ethGetUncleByBlockHashAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetUncleByBlockHashAndIndex(blocksPlan.getBlockHash(), blocksPlan.getUncleIndex());
    }

    @Benchmark
    public void ethGetUncleByBlockNumberAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        blocksPlan.getWeb3Connector().ethGetUncleByBlockNumberAndIndex(blocksPlan.getBlockNumber(), blocksPlan.getUncleIndex());
    }

}

/*






eth_getTransactionReceipt

eth_getTransactionCount

eth_getBlockTransactionCountByHash

eth_getBlockTransactionCountByNumber

eth_getUncleCountByBlockHash

eth_getUncleCountByBlockNumber

eth_getUncleByBlockHashAndIndex

eth_getUncleByBlockNumberAndIndex

 */