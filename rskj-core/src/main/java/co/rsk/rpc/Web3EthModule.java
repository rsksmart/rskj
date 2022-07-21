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
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.FilterRequest;
import org.ethereum.rpc.dto.BlockResultDTO;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;

import java.math.BigInteger;
import java.util.Map;

// TODO:I check, in all modules, if we have doc for all methods

public interface Web3EthModule {
    default String[] eth_accounts() {
        return getEthModule().accounts();
    }

    default String eth_sign(String addr, String data) {
        return getEthModule().sign(addr, data);
    }

    default String eth_call(CallArguments args, String bnOrId) { // TODO:I create doc for bnOrId param (or simply define it in this method if not used anywhere else)
        return getEthModule().call(args, bnOrId);
    }

    default String eth_estimateGas(CallArguments args) {
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

    String eth_call(CallArguments args, Map<String, String> blockRef) throws Exception; // NOSONAR // TODO:I missing doc

    String eth_getBalance(String address, String block) throws Exception;

    String eth_getBalance(String address) throws Exception;

    String eth_getBalance(String address, Map<String, String> blockRef) throws Exception; // NOSONAR // TODO:I missing doc

    String eth_getStorageAt(String address, String storageIdx, Map<String, String> blockRef) throws Exception; // NOSONAR

    String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception;

    String eth_getTransactionCount(String address, Map<String, String> blockRef) throws Exception; // NOSONAR

    String eth_getTransactionCount(String address, String blockId) throws Exception ;

    String eth_getBlockTransactionCountByHash(String blockHash)throws Exception;

    String eth_getBlockTransactionCountByNumber(String bnOrId)throws Exception;

    String eth_getUncleCountByBlockHash(String blockHash)throws Exception;

    String eth_getUncleCountByBlockNumber(String bnOrId)throws Exception;

    default String eth_getCode(String address, String blockId) {
        return getEthModule().getCode(address, blockId);
    }

    String eth_getCode(String address, Map<String, String> blockRef) throws Exception; // NOSONAR

    default String eth_sendRawTransaction(String rawData) {
        return getEthModule().sendRawTransaction(rawData);
    }

    default String eth_sendTransaction(CallArguments args) {
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

    String eth_newFilter(FilterRequest fr) throws Exception;

    String eth_newBlockFilter();

    String eth_newPendingTransactionFilter();

    boolean eth_uninstallFilter(String id);

    Object[] eth_getFilterChanges(String id);

    Object[] eth_getFilterLogs(String id);

    Object[] eth_getLogs(FilterRequest fr) throws Exception;

    BigInteger eth_netHashrate();

    boolean eth_submitWork(String nonce, String header, String mince);

    boolean eth_submitHashrate(String hashrate, String id);
}
