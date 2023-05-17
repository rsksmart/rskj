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

package co.rsk.jmh.web3.e2e;

import co.rsk.jmh.web3.Web3Connector;
import com.fasterxml.jackson.databind.JsonNode;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Web3ConnectorE2E implements Web3Connector {

    private static Web3ConnectorE2E connector;

    private RskDebugModuleWeb3j debugModuleWeb3j;

    private RskModuleWeb3j rskModuleWeb3j;

    private RskTraceModuleWeb3j traceModuleWeb3j;

    private Web3ConnectorE2E(String host, RskDebugModuleWeb3j debugModuleWeb3j, RskModuleWeb3j rskModuleWeb3j, RskTraceModuleWeb3j traceModuleWeb3j) {
        this.debugModuleWeb3j = debugModuleWeb3j;
        this.rskModuleWeb3j = rskModuleWeb3j;
        this.traceModuleWeb3j = traceModuleWeb3j;
    }

    public static Web3ConnectorE2E create(String host, RskDebugModuleWeb3j debugModuleWeb3j, RskModuleWeb3j rskModuleWeb3j, RskTraceModuleWeb3j traceModuleWeb3j) {
        if (connector == null) {
            connector = new Web3ConnectorE2E(host, debugModuleWeb3j, rskModuleWeb3j, traceModuleWeb3j);
        }
        return connector;
    }

    @Override
    public JsonNode ethCall(RskModuleWeb3j.EthCallArguments args, String bnOrId) throws HttpRpcException {
        try {
            RskModuleWeb3j.GenericJsonResponse response = sendRequest(() -> rskModuleWeb3j.ethCall(args, bnOrId));
            return response.getResult();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public BigInteger ethGetTransactionCount(String address) throws HttpRpcException {
        try {
            EthGetTransactionCount response = sendRequest(() -> rskModuleWeb3j.ethGetTransactionCount(address, DefaultBlockParameter.valueOf("latest")));
            return response.getTransactionCount();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public BigInteger ethGetBalance(String address, String block) throws HttpRpcException {
        try {
            EthGetBalance response = sendRequest(() -> rskModuleWeb3j.ethGetBalance(address, DefaultBlockParameter.valueOf(block)));
            return response.getBalance();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public String ethBlockNumber() throws HttpRpcException {
        try {
            EthBlockNumber response = sendRequest(debugModuleWeb3j::ethBlockNumber);
            return response.getBlockNumber().toString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public String ethSendRawTransaction(String rawTx) throws HttpRpcException {
        try {
            EthSendTransaction response = sendRequest(() -> rskModuleWeb3j.ethSendRawTransaction(rawTx));
            return response.getTransactionHash();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public String ethSendTransaction(Transaction transaction) throws HttpRpcException {
        try {
            EthSendTransaction response = sendRequest(() -> rskModuleWeb3j.ethSendTransaction(transaction));
            return response.getTransactionHash();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public BigInteger ethEstimateGas(Transaction transaction) throws HttpRpcException {
        try {
            EthEstimateGas response = sendRequest(() -> rskModuleWeb3j.ethEstimateGas(transaction));
            return response.getAmountUsed();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public List<EthLog.LogResult> ethGetLogs(DefaultBlockParameter fromBlock, DefaultBlockParameter toBlock, String address) throws HttpRpcException {
        EthFilter filter = new EthFilter(fromBlock, toBlock, address);

        return ethGetLogs(filter);
    }

    @Override
    public List<EthLog.LogResult> ethGetLogs(String blockHash) throws HttpRpcException {
        EthFilter filter = new EthFilter(blockHash);

        return ethGetLogs(filter);
    }

    @Override
    public String ethNewFilter(DefaultBlockParameter fromBlock, DefaultBlockParameter toBlock, String address) throws HttpRpcException {
        EthFilter filter = new EthFilter(fromBlock, toBlock, address);

        return ethNewFilter(filter);
    }

    @Override
    public String ethNewFilter(String blockHash) throws HttpRpcException {
        EthFilter filter = new EthFilter(blockHash);

        return ethNewFilter(filter);
    }

    @Override
    public List<EthLog.LogResult> ethGetFilterChanges(BigInteger filterId) throws HttpRpcException {
        try {
            EthLog response = sendRequest(() -> rskModuleWeb3j.ethGetFilterChanges(filterId));
            return response.getLogs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public List<EthLog.LogResult> ethGetFilterLogs(BigInteger filterId) throws HttpRpcException {
        try {
            EthLog response = sendRequest(() -> rskModuleWeb3j.ethGetFilterLogs(filterId));
            return response.getLogs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse traceTransaction(String transactionHash) throws HttpRpcException {
        return sendRequest(() -> traceModuleWeb3j.traceTransaction(transactionHash));
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse traceBlock(String blockHash) throws HttpRpcException {
        return sendRequest(() -> traceModuleWeb3j.traceBlock(blockHash));
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse traceFilter(String fromBlock, String toBlock) throws HttpRpcException {
        return sendRequest(() -> traceModuleWeb3j.traceFilter(new RskTraceModuleWeb3j.TraceFilterRequest(fromBlock, toBlock)));
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse traceFilter(String fromBlock, String toBlock, List<String> fromAddresses, List<String> toAddresses) throws HttpRpcException {
        return sendRequest(() -> traceModuleWeb3j.traceFilter(new RskTraceModuleWeb3j.TraceFilterRequest(fromBlock, toBlock, fromAddresses, toAddresses)));
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse traceGet(String transactionHash, List<String> positions) throws HttpRpcException {
        return sendRequest(() -> traceModuleWeb3j.traceGet(transactionHash, positions));
    }

    private List<EthLog.LogResult> ethGetLogs(EthFilter filter) throws HttpRpcException {
        try {
            EthLog response = sendRequest(() -> rskModuleWeb3j.ethGetLogs(filter));
            return response.getLogs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    private String ethNewFilter(EthFilter filter) throws HttpRpcException {
        try {
            org.web3j.protocol.core.methods.response.EthFilter response = sendRequest(() -> rskModuleWeb3j.ethNewFilter(filter));
            return response.getResult();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    private <R extends Response<?>> R sendRequest(Supplier<Request<?, R>> supplier) throws HttpRpcException {
        try {
            Request<?, R> request = supplier.get();
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public String ethGetBlockHashByNumber(BigInteger blockNumber) throws HttpRpcException {
        EthBlock response = ethGetBlockByNumber(blockNumber);
        return response.getResult().getHash();
    }

    @Override
    public String rskGetRawBlockHeaderByNumber(String bnOrId) throws HttpRpcException {
        try {
            RskModuleWeb3j.RawBlockHeaderByNumberResponse response = sendRequest(() -> rskModuleWeb3j.rskGetRawBlockHeaderByNumber(bnOrId));
            return response.getRawHeader();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse rskGetRawTransactionReceiptByHash(String txHash) throws HttpRpcException {
        try {
            Request<?, RskModuleWeb3j.GenericJsonResponse> request = rskModuleWeb3j.rskGetRawTransactionReceiptByHash(txHash);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse rskGetTransactionReceiptNodesByHash(String blockHash, String txHash) throws HttpRpcException {
        try {
            Request<?, RskModuleWeb3j.GenericJsonResponse> request = rskModuleWeb3j.rskGetTransactionReceiptNodesByHash(blockHash, txHash);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse rskGetRawBlockHeaderByHash(String blockHash) throws HttpRpcException {
        try {
            Request<?, RskModuleWeb3j.GenericJsonResponse> request = rskModuleWeb3j.rskGetRawBlockHeaderByHash(blockHash);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public RskModuleWeb3j.GenericJsonResponse rskGetRawBlockHeaderByNumber(BigInteger blockNumber) throws HttpRpcException {
        try {
            Request<?, RskModuleWeb3j.GenericJsonResponse> request = rskModuleWeb3j.rskGetRawBlockHeaderByNumber(DefaultBlockParameter.valueOf(blockNumber));
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }


    @Override
    public JsonNode debugTraceTransaction(String txHash) throws HttpRpcException {
        try {
            RskModuleWeb3j.GenericJsonResponse response = sendRequest(() -> debugModuleWeb3j.debugTraceTransaction(txHash));
            return response.getJson();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public JsonNode debugTraceTransaction(String txHash, Map<String, String> params) throws HttpRpcException {
        try {
            RskModuleWeb3j.GenericJsonResponse response = sendRequest(() -> debugModuleWeb3j.debugTraceTransaction(txHash, params));
            return response.getJson();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public JsonNode debugTraceBlockByHash(String txHash) throws HttpRpcException {
        try {
            RskModuleWeb3j.GenericJsonResponse response = sendRequest(() -> debugModuleWeb3j.debugTraceBlockByHash(txHash));
            return response.getJson();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public JsonNode debugTraceBlockByHash(String txHash, Map<String, String> params) throws HttpRpcException {
        try {
            RskModuleWeb3j.GenericJsonResponse response = sendRequest(() -> debugModuleWeb3j.debugTraceBlockByHash(txHash, params));
            return response.getJson();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthBlock ethGetBlockByHash(String blockHash) throws HttpRpcException {
        try {
            Request<?, EthBlock> request = rskModuleWeb3j.ethGetBlockByHash(blockHash, false);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthBlock ethGetBlockByNumber(BigInteger blockNumber) throws HttpRpcException {
        return ethGetBlockByNumber(blockNumber, false);
    }

    @Override
    public EthBlock ethGetBlockByNumber(BigInteger blockNumber, boolean returnFullTransactionObjects) throws HttpRpcException {
        try {
            Request<?, EthBlock> request = rskModuleWeb3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), returnFullTransactionObjects);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthTransaction ethGetTransactionByHash(String txHash) throws HttpRpcException {
        try {
            Request<?, EthTransaction> request = rskModuleWeb3j.ethGetTransactionByHash(txHash);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }

    }

    @Override
    public EthTransaction ethGetTransactionByBlockHashAndIndex(String blockHash, int index) throws HttpRpcException {
        try {
            Request<?, EthTransaction> request = rskModuleWeb3j.ethGetTransactionByBlockHashAndIndex(blockHash, BigInteger.valueOf(index));
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthTransaction ethGetTransactionByBlockNumberAndIndex(BigInteger blockNumber, int index) throws HttpRpcException {
        try {
            Request<?, EthTransaction> request = rskModuleWeb3j.ethGetTransactionByBlockNumberAndIndex(DefaultBlockParameter.valueOf(blockNumber), BigInteger.valueOf(index));
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthGetTransactionReceipt ethGetTransactionReceipt(String txHash) throws HttpRpcException {
        try {
            Request<?, EthGetTransactionReceipt> request = rskModuleWeb3j.ethGetTransactionReceipt(txHash);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthGetTransactionCount ethGetTransactionCount(String address, BigInteger blockNumber) throws HttpRpcException {
        try {
            Request<?, EthGetTransactionCount> request = rskModuleWeb3j.ethGetTransactionCount(address, DefaultBlockParameter.valueOf(blockNumber));
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthGetBlockTransactionCountByHash ethGetTransactionCountByHash(String blockHash) throws HttpRpcException {
        try {
            Request<?, EthGetBlockTransactionCountByHash> request = rskModuleWeb3j.ethGetBlockTransactionCountByHash(blockHash);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthGetBlockTransactionCountByNumber ethGetBlockTransactionCountByNumber(BigInteger blockNumber) throws HttpRpcException {
        try {
            Request<?, EthGetBlockTransactionCountByNumber> request = rskModuleWeb3j.ethGetBlockTransactionCountByNumber(DefaultBlockParameter.valueOf(blockNumber));
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthGetUncleCountByBlockHash ethGetUncleCountByBlockHash(String blockHash) throws HttpRpcException {
        try {
            Request<?, EthGetUncleCountByBlockHash> request = rskModuleWeb3j.ethGetUncleCountByBlockHash(blockHash);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthGetUncleCountByBlockNumber ethGetUncleCountByBlockNumber(BigInteger blockNumber) throws HttpRpcException {
        try {
            Request<?, EthGetUncleCountByBlockNumber> request = rskModuleWeb3j.ethGetUncleCountByBlockNumber(DefaultBlockParameter.valueOf(blockNumber));
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthBlock ethGetUncleByBlockHashAndIndex(String blockHash, BigInteger index) throws HttpRpcException {
        try {
            Request<?, EthBlock> request = rskModuleWeb3j.ethGetUncleByBlockHashAndIndex(blockHash, index);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public EthBlock ethGetUncleByBlockNumberAndIndex(BigInteger blockNumber, BigInteger index) throws HttpRpcException {
        try {
            Request<?, EthBlock> request = rskModuleWeb3j.ethGetUncleByBlockNumberAndIndex(DefaultBlockParameter.valueOf(blockNumber), index);
            return request.send();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

}
