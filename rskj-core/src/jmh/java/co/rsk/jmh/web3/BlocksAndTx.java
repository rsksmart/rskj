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

import co.rsk.jmh.web3.e2e.HttpRpcException;
import co.rsk.jmh.web3.e2e.RskModuleWeb3j;
import co.rsk.jmh.web3.plan.BlocksPlan;
import org.openjdk.jmh.annotations.*;
import org.web3j.protocol.core.methods.response.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 5)
@Measurement(iterations = 100)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 10)
public class BlocksAndTx {

    @Benchmark
    public  EthBlock ethGetBlockByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        EthBlock response = blocksPlan.getWeb3Connector().ethGetBlockByHash(blocksPlan.getBlockHash());
        if (response.getResult() == null || !response.getResult().getHash().contentEquals(blocksPlan.getBlockHash())) {
            throw new RuntimeException("Block hash does not match");
        }
        return response;
    }

    @Benchmark
    public EthBlock ethGetBlockByNumber(BlocksPlan blocksPlan) throws HttpRpcException {
        EthBlock response = blocksPlan.getWeb3Connector().ethGetBlockByNumber(blocksPlan.getBlockNumber());
        if (response.getResult() == null || !response.getResult().getNumber().equals(blocksPlan.getBlockNumber())) {
            throw new RuntimeException("Block hash does not match");
        }
        return response;
    }

    @Benchmark
    public EthTransaction ethGetTransactionByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetTransactionByHash(blocksPlan.getTxHash());
    }

    @Benchmark
    public EthTransaction ethGetTransactionByBlockHashAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetTransactionByBlockHashAndIndex(blocksPlan.getBlockHash(), blocksPlan.getTxIndex());
    }

    @Benchmark
    public EthTransaction ethGetTransactionByBlockNumberAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetTransactionByBlockNumberAndIndex(blocksPlan.getBlockNumber(), blocksPlan.getTxIndex());
    }

    @Benchmark
    public EthGetTransactionReceipt ethGetTransactionReceipt(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetTransactionReceipt(blocksPlan.getTxHash());
    }

    @Benchmark
    public EthGetTransactionCount ethGetTransactionCount(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetTransactionCount(blocksPlan.getAddress(), blocksPlan.getBlockNumber());
    }

    @Benchmark
    public EthGetBlockTransactionCountByHash ethGetTransactionCountByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetTransactionCountByHash(blocksPlan.getBlockHash());
    }

    @Benchmark
    public EthGetBlockTransactionCountByNumber ethGetBlockTransactionCountByNumber(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetBlockTransactionCountByNumber(blocksPlan.getBlockNumber());
    }

    @Benchmark
    public EthGetUncleCountByBlockHash ethGetUncleCountByBlockHash(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetUncleCountByBlockHash(blocksPlan.getBlockHash());
    }

    @Benchmark
    public EthGetUncleCountByBlockNumber ethGetUncleCountByBlockNumber(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetUncleCountByBlockNumber(blocksPlan.getBlockNumber());
    }

    @Benchmark
    public EthBlock ethGetUncleByBlockHashAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetUncleByBlockHashAndIndex(blocksPlan.getBlockHash(), blocksPlan.getUncleIndex());
    }

    @Benchmark
    public EthBlock ethGetUncleByBlockNumberAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().ethGetUncleByBlockNumberAndIndex(blocksPlan.getBlockNumber(), blocksPlan.getUncleIndex());
    }
    
    @Benchmark
    public RskModuleWeb3j.GenericJsonResponse rskGetRawTransactionReceiptByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().rskGetRawTransactionReceiptByHash(blocksPlan.getTxHash());
    }

    @Benchmark
    public RskModuleWeb3j.GenericJsonResponse rskGetRawTransactionReceiptByBlockHashAndIndex(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().rskGetTransactionReceiptNodesByHash(blocksPlan.getBlockHash(), blocksPlan.getTxHash());
    }

    @Benchmark
    public RskModuleWeb3j.GenericJsonResponse rskGetRawBlockHeaderByHash(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().rskGetRawBlockHeaderByHash(blocksPlan.getBlockHash());
    }

    @Benchmark
    public RskModuleWeb3j.GenericJsonResponse rskGetRawBlockHeaderByNumber(BlocksPlan blocksPlan) throws HttpRpcException {
        return blocksPlan.getWeb3Connector().rskGetRawBlockHeaderByNumber(blocksPlan.getBlockNumber());
    }
}
