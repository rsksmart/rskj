/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.docs.annotation.JsonRpcDoc;
import co.rsk.rpc.docs.annotation.JsonRpcDocRequestParameter;
import co.rsk.rpc.docs.annotation.JsonRpcDocResponse;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.dto.BlockResultDTO;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;

import java.math.BigInteger;
import java.util.Map;

public interface Web3EthModule {
    default String[] eth_accounts() {
        return getEthModule().accounts();
    }

    default String eth_sign(String addr, String data) {
        return getEthModule().sign(addr, data);
    }

    @JsonRpcDoc(
        description = "Executes a new message call immediately without creating a transaction on the block chain.",
        isWriteMethod = true,
        requestExamples = "eth_call.yaml/request/default",
        requestParams = {
            @JsonRpcDocRequestParameter(
                name = "args",
                alias = "transaction",
                description = "eth_call.yaml/description/request/transaction",
                loadDescriptionFromFile = true,
                attachModel = true
            ),
            @JsonRpcDocRequestParameter(
                name = "bnOrId",
                alias = "blockNumberOrId",
                description = "**QUANTITY|TAG** - integer block number, or the string 'latest', 'earliest' or 'pending', see the default block parameter"
            )
        },
        responses = {
            @JsonRpcDocResponse(
                description = "**DATA** - the return value of executed contract.",
                code = "Success",
                examplePath = "eth_call.yaml/response/success"
            ),
            @JsonRpcDocResponse(
                description = "Method parameters invalid.",
                code = "-32602",
                examplePath = "generic.yaml/response/methodInvalid",
                success = false
            ),
            @JsonRpcDocResponse(
                description = "Something unexpected happened.",
                code = "-32603",
                examplePath = "generic.yaml/response/internalServerError",
                success = false
            )
        }
    )
    default String eth_call(Web3.CallArguments args, String bnOrId) {
        return getEthModule().call(args, bnOrId);
    }

    default String eth_estimateGas(Web3.CallArguments args) {
        return getEthModule().estimateGas(args);
    }



    default Map<String, Object> eth_bridgeState() throws Exception {
        return getEthModule().bridgeState();
    }

    default String eth_chainId() {
        return getEthModule().chainId();
    }

    EthModule getEthModule();

    String eth_protocolVersion();

    Object eth_syncing();

    String eth_coinbase();

    boolean eth_mining();

    BigInteger eth_hashrate();

    String eth_gasPrice();

    String eth_blockNumber();

    String eth_getBalance(String address, String block) throws Exception;

    String eth_getBalance(String address) throws Exception;

    String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception;

    String eth_getTransactionCount(String address, String blockId) throws Exception ;

    String eth_getBlockTransactionCountByHash(String blockHash)throws Exception;

    String eth_getBlockTransactionCountByNumber(String bnOrId)throws Exception;

    String eth_getUncleCountByBlockHash(String blockHash)throws Exception;

    String eth_getUncleCountByBlockNumber(String bnOrId)throws Exception;

    @JsonRpcDoc(
            description = "Returns the code at a given address.",
            requestExamples = "eth_getCode.yaml/request/default",
            isWriteMethod = false,
            requestParams = {
                    @JsonRpcDocRequestParameter(
                            name = "address",
                            description = "**DATA**, 20 Bytes - address."
                    ),
                    @JsonRpcDocRequestParameter(
                            name = "blockId",
                            description = "**QUANTITY | TAG** - integer block number, or the string 'latest' or 'earliest'."
                    )
            },
            responses = {
                    @JsonRpcDocResponse(
                            description = "The code present in the address",
                            code = "Success",
                            examplePath = "eth_getCode.yaml/response/success"
                    ),
                    @JsonRpcDocResponse(
                            description = "Method parameters invalid.",
                            code = "-32602",
                            examplePath = "generic.yaml/response/methodInvalid"
                    ),
                    @JsonRpcDocResponse(
                            description = "Something unexpected happened.",
                            code = "-32603",
                            examplePath = "generic.yaml/response/internalServerError"
                    )
            }
    )
    default String eth_getCode(String address, String blockId) {
        return getEthModule().getCode(address, blockId);
    }

    @JsonRpcDoc(
            description = "Creates new message call transaction or a contract creation for signed transactions.",
            summary = "Creates new message call transaction or a contract creation.",
            requestExamples = "eth_sendTransaction.yaml/request/default",
            isWriteMethod = true,
            requestParams = {
                    @JsonRpcDocRequestParameter(
                            name = "rawData",
                            description = "**DATA**, The signed transaction data."
                    )
            },
            responses = {
                    @JsonRpcDocResponse(
                            description =
                                    "**DATA**, 32 Bytes - the transaction hash, or the zero hash if the transaction is not yet available.\n" +
                                            "Use eth_getTransactionReceipt to get the contract address, after the transaction was mined, when you created a contract.",
                            code = "Success",
                            examplePath = "eth_sendRawTransaction.yaml/response/success"
                    ),
                    @JsonRpcDocResponse(
                            description = "Method parameters invalid.",
                            code = "-32602",
                            examplePath = "generic.yaml/response/methodInvalid"
                    ),
                    @JsonRpcDocResponse(
                            description = "Something unexpected happened.",
                            code = "-32603",
                            examplePath = "generic.yaml/response/internalServerError"
                    )
            }
    )
    default String eth_sendRawTransaction(String rawData) {
        return getEthModule().sendRawTransaction(rawData);
    }

    @JsonRpcDoc(
            description = "Creates new message call transaction or a contract creation, if the data field contains code.",
            summary = "Creates new message call transaction or a contract creation.",
            requestExamples = "eth_sendTransaction.yaml/request/default",
            isWriteMethod = true,
            requestParams = {
                    @JsonRpcDocRequestParameter(
                            name = "args",
                            alias = "transaction",
                            description = "eth_sendTransaction.yaml/description/request/transaction",
                            loadDescriptionFromFile = true
                    )
            },
            responses = {
                    @JsonRpcDocResponse(
                            description =
                                    "**DATA**, 32 Bytes - the transaction hash, or the zero hash if the transaction is not yet available.\n" +
                                            "Use eth_getTransactionReceipt to get the contract address, after the transaction was mined, when you created a contract.",
                            code = "Success",
                            examplePath = "eth_sendTransaction.yaml/response/success"
                    ),
                    @JsonRpcDocResponse(
                            description = "Method parameters invalid.",
                            code = "-32602",
                            examplePath = "generic.yaml/response/methodInvalid",
                            success = false
                    ),
                    @JsonRpcDocResponse(
                            description = "Something unexpected happened.",
                            code = "-32603",
                            examplePath = "generic.yaml/response/internalServerError",
                            success = false
                    )
            }
    )
    default String eth_sendTransaction(Web3.CallArguments args) {
        return getEthModule().sendTransaction(args);
    }

    BlockResultDTO eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception;

    BlockResultDTO eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception;

    TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception;

    TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception;

    TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception;

    TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception;

    BlockResultDTO eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception;

    BlockResultDTO eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception;

    String[] eth_getCompilers();

    Map<String, CompilationResultDTO> eth_compileLLL(String contract);

    Map<String, CompilationResultDTO> eth_compileSerpent(String contract);

    Map<String, CompilationResultDTO> eth_compileSolidity(String contract);

    String eth_newFilter(Web3.FilterRequest fr) throws Exception;

    String eth_newBlockFilter();

    String eth_newPendingTransactionFilter();

    boolean eth_uninstallFilter(String id);

    Object[] eth_getFilterChanges(String id);

    Object[] eth_getFilterLogs(String id);

    Object[] eth_getLogs(Web3.FilterRequest fr) throws Exception;

    BigInteger eth_netHashrate();

    boolean eth_submitWork(String nonce, String header, String mince);

    boolean eth_submitHashrate(String hashrate, String id);
}
