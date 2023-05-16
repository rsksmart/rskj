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
import okhttp3.OkHttpClient;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Web3ConnectorE2E implements Web3Connector {

    private static Web3ConnectorE2E connector;

    private final RskDebugModuleWeb3j debugModuleWeb3j;

    private final RskModuleWeb3j rskModuleWeb3j;

    private final RskTraceModuleWeb3j traceModuleWeb3j;

    private Web3ConnectorE2E(String host) {
        OkHttpClient httpClient = HttpService.getOkHttpClientBuilder()
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(120))
                .callTimeout(Duration.ofSeconds(120))
                .connectTimeout(Duration.ofSeconds(120))
                .build();
        this.debugModuleWeb3j = new RskDebugModuleWeb3j(new HttpService(host, httpClient));
        this.rskModuleWeb3j = new RskModuleWeb3j(new HttpService(host, httpClient));
        this.traceModuleWeb3j = new RskTraceModuleWeb3j(new HttpService(host));
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
            EthGetTransactionCount response = sendRequest(() -> debugModuleWeb3j.ethGetTransactionCount(address, DefaultBlockParameter.valueOf("latest")));
            return response.getTransactionCount();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public BigInteger ethGetBalance(String address, String block) throws HttpRpcException {
        try {
            EthGetBalance response = sendRequest(() -> debugModuleWeb3j.ethGetBalance(address, DefaultBlockParameter.valueOf(block)));
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
            EthSendTransaction response = sendRequest(() -> debugModuleWeb3j.ethSendRawTransaction(rawTx));
            return response.getTransactionHash();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public String ethSendTransaction(Transaction transaction) throws HttpRpcException {
        try {
            EthSendTransaction response = sendRequest(() -> debugModuleWeb3j.ethSendTransaction(transaction));
            return response.getTransactionHash();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public BigInteger ethEstimateGas(Transaction transaction) throws HttpRpcException {
        try {
            EthEstimateGas response = sendRequest(() -> debugModuleWeb3j.ethEstimateGas(transaction));
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
            EthLog response = sendRequest(() -> debugModuleWeb3j.ethGetFilterChanges(filterId));
            return response.getLogs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    @Override
    public List<EthLog.LogResult> ethGetFilterLogs(BigInteger filterId) throws HttpRpcException {
        try {
            EthLog response = sendRequest(() -> debugModuleWeb3j.ethGetFilterLogs(filterId));
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
            EthLog response = sendRequest(() -> debugModuleWeb3j.ethGetLogs(filter));
            return response.getLogs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
    }

    private String ethNewFilter(EthFilter filter) throws HttpRpcException {
        try {
            org.web3j.protocol.core.methods.response.EthFilter response = sendRequest(() -> debugModuleWeb3j.ethNewFilter(filter));
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
    public String ethGetBlockByNumber(BigInteger blockNumber) throws HttpRpcException {
        try {
            EthBlock response = sendRequest(() -> debugModuleWeb3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false));
            return response.getResult().getHash();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpRpcException(e);
        }
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
}
