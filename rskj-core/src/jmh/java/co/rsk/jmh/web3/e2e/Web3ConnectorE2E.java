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
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class Web3ConnectorE2E implements Web3Connector {

    private static Web3ConnectorE2E connector;

    private final RskWeb3j web3j;

    private Web3ConnectorE2E(String host) {
        this.web3j = new RskWeb3j(new HttpService(host));
    }

    public static Web3ConnectorE2E create(String host) {
        if (connector == null) {
            connector = new Web3ConnectorE2E(host);
        }
        return connector;
    }

    @Override
    public BigInteger ethGetTransactionCount(String address) throws HttpRpcException {
        try {
            Request<?, EthGetTransactionCount> request = web3j.ethGetTransactionCount(address, DefaultBlockParameter.valueOf("latest"));
            EthGetTransactionCount response = request.send();
            return response.getTransactionCount();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public BigInteger ethGetBalance(String address, String block) throws HttpRpcException {
        try {
            Request<?, EthGetBalance> request = web3j.ethGetBalance(address, DefaultBlockParameter.valueOf(block));
            EthGetBalance response = request.send();
            return response.getBalance();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public String ethBlockNumber() throws HttpRpcException {
        try {
            Request<?, EthBlockNumber> request = web3j.ethBlockNumber();
            EthBlockNumber response = request.send();
            return response.getBlockNumber().toString();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public String ethSendRawTransaction(String rawTx) throws HttpRpcException {
        try {
            Request<?, EthSendTransaction> request = web3j.ethSendRawTransaction(rawTx);
            EthSendTransaction response = request.send();
            return response.getTransactionHash();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public String ethSendTransaction(Transaction transaction) throws HttpRpcException {
        try {
            Request<?, EthSendTransaction> request = web3j.ethSendTransaction(transaction);
            EthSendTransaction response = request.send();
            return response.getTransactionHash();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public BigInteger ethEstimateGas(Transaction transaction) throws HttpRpcException {
        try {
            Request<?, EthEstimateGas> request = web3j.ethEstimateGas(transaction);
            EthEstimateGas response = request.send();
            return response.getAmountUsed();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
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
            Request<?, EthLog> request = web3j.ethGetFilterChanges(filterId);
            EthLog response = request.send();
            return response.getLogs();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public List<EthLog.LogResult> ethGetFilterLogs(BigInteger filterId) throws HttpRpcException {
        try {
            Request<?, EthLog> request = web3j.ethGetFilterLogs(filterId);
            EthLog response = request.send();
            return response.getLogs();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    private List<EthLog.LogResult> ethGetLogs(EthFilter filter) throws HttpRpcException {
        try {
            Request<?, EthLog> request = web3j.ethGetLogs(filter);
            EthLog response = request.send();
            return response.getLogs();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    private String ethNewFilter(EthFilter filter) throws HttpRpcException {
        try {
            Request<?, org.web3j.protocol.core.methods.response.EthFilter> request = web3j.ethNewFilter(filter);
            org.web3j.protocol.core.methods.response.EthFilter response = request.send();
            return response.getResult();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public String ethGetBlockByNumber(BigInteger blockNumber) throws HttpRpcException {
        try {
            Request<?, EthBlock> request = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false);
            EthBlock response = request.send();
            return response.getResult().getHash();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public String rskGetRawBlockHeaderByNumber(String bnOrId) throws HttpRpcException {
        try {
            Request<?, RskWeb3j.RawBlockHeaderByNumberResponse> request = web3j.rskGetRawBlockHeaderByNumber(bnOrId);
            RskWeb3j.RawBlockHeaderByNumberResponse response = request.send();
            return response.getRawHeader();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public JsonNode debugTraceTransaction(String txHash) throws HttpRpcException {
        try {
            Request<?, RskWeb3j.GenericJsonResponse> request = web3j.debugTraceTransaction(txHash);
            RskWeb3j.GenericJsonResponse response = request.send();
            return response.getJson();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public JsonNode debugTraceTransaction(String txHash, Map<String, String> params) throws HttpRpcException {
        try {
            Request<?, RskWeb3j.GenericJsonResponse> request = web3j.debugTraceTransaction(txHash, params);
            RskWeb3j.GenericJsonResponse response = request.send();
            return response.getJson();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public JsonNode debugTraceBlockByHash(String txHash) throws HttpRpcException {
        try {
            Request<?, RskWeb3j.GenericJsonResponse> request = web3j.debugTraceBlockByHash(txHash);
            RskWeb3j.GenericJsonResponse response = request.send();
            return response.getJson();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public JsonNode debugTraceBlockByHash(String txHash, Map<String, String> params) throws HttpRpcException {
        try {
            Request<?, RskWeb3j.GenericJsonResponse> request = web3j.debugTraceBlockByHash(txHash, params);
            RskWeb3j.GenericJsonResponse response = request.send();
            return response.getJson();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }
}
