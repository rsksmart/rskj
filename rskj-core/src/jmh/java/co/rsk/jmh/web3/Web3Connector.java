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
import com.fasterxml.jackson.databind.JsonNode;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface Web3Connector {

    BigInteger ethGetTransactionCount(String address) throws HttpRpcException;

    BigInteger ethGetBalance(String address, String block) throws HttpRpcException;

    String ethBlockNumber() throws HttpRpcException;

    String ethSendRawTransaction(String rawTx) throws HttpRpcException;

    String ethSendTransaction(Transaction transaction) throws HttpRpcException;

    BigInteger ethEstimateGas(Transaction transaction) throws HttpRpcException;

    List<EthLog.LogResult> ethGetLogs(DefaultBlockParameter fromBlock, DefaultBlockParameter toBlock, String address) throws HttpRpcException;

    List<EthLog.LogResult> ethGetLogs(String blockHash) throws HttpRpcException;

    String ethNewFilter(DefaultBlockParameter fromBlock, DefaultBlockParameter toBlock, String address) throws HttpRpcException;

    String ethNewFilter(String blockHash) throws HttpRpcException;

    List<EthLog.LogResult> ethGetFilterChanges(BigInteger filterId) throws HttpRpcException;

    List<EthLog.LogResult> ethGetFilterLogs(BigInteger filterId) throws HttpRpcException;

    String ethGetBlockHashByNumber(BigInteger blockNumber) throws HttpRpcException;

    String rskGetRawBlockHeaderByNumber(String bnOrId) throws HttpRpcException;

    JsonNode debugTraceTransaction(String txHash) throws HttpRpcException;

    JsonNode debugTraceTransaction(String txHash, Map<String, String> params) throws HttpRpcException;

    JsonNode debugTraceBlockByHash(String txHash) throws HttpRpcException;

    JsonNode debugTraceBlockByHash(String txHash, Map<String, String> params) throws HttpRpcException;

    Response<EthBlock.Block> ethGetBlockByHash(String blockHash) throws HttpRpcException;

    EthBlock ethGetBlockByNumber(BigInteger blockNumber) throws HttpRpcException;

    EthTransaction ethGetTransactionByHash(String txHash) throws HttpRpcException;

    EthTransaction ethGetTransactionByBlockHashAndIndex(String blockHash, int index) throws HttpRpcException;

    EthTransaction ethGetTransactionByBlockNumberAndIndex(BigInteger blockNumber, int index) throws HttpRpcException;

    EthGetTransactionReceipt ethGetTransactionReceipt(String txHash) throws HttpRpcException;

    EthGetTransactionCount ethGetTransactionCount(String address, BigInteger blockNumber) throws HttpRpcException;

    EthGetBlockTransactionCountByHash ethGetTransactionCountByHash(String blockHash) throws HttpRpcException;

    EthGetBlockTransactionCountByNumber ethGetBlockTransactionCountByNumber(BigInteger blockNumber) throws HttpRpcException;

    EthGetUncleCountByBlockHash ethGetUncleCountByBlockHash(String blockHash) throws HttpRpcException;

    EthGetUncleCountByBlockNumber ethGetUncleCountByBlockNumber(BigInteger blockNumber) throws HttpRpcException;

    EthBlock ethGetUncleByBlockHashAndIndex(String blockHash, BigInteger index) throws HttpRpcException;

    EthBlock ethGetUncleByBlockNumberAndIndex(BigInteger blockNumber, BigInteger index) throws HttpRpcException;
}
