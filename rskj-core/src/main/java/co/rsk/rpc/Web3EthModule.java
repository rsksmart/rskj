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
import org.ethereum.rpc.dto.BlockResultDTO;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.rpc.parameters.BlockHashParam;
import org.ethereum.rpc.parameters.FilterRequestParam;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.BlockRefParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.rpc.parameters.HexIndexParam;
import org.ethereum.rpc.parameters.TxHashParam;

import java.math.BigInteger;
import java.util.Map;

public interface Web3EthModule {
    default String[] eth_accounts() {
        return getEthModule().accounts();
    }

    default String eth_sign(HexAddressParam addr, HexDataParam data) {
        return getEthModule().sign(addr, data);
    }

    default String eth_call(CallArgumentsParam args, BlockIdentifierParam bnOrId) {
        return getEthModule().call(args, bnOrId);
    }

    default String eth_estimateGas(CallArgumentsParam args) {
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

    String eth_hashrate();

    String eth_gasPrice();

    String eth_blockNumber();

    String eth_call(CallArgumentsParam args, Map<String, String> blockRef) throws Exception; // NOSONAR

    String eth_getBalance(HexAddressParam address, BlockRefParam blockRefParam) throws Exception;

    String eth_getBalance(HexAddressParam address) throws Exception;

    String eth_getStorageAt(String address, String storageIdx, Map<String, String> blockRef) throws Exception; // NOSONAR

    String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception;

    String eth_getTransactionCount(HexAddressParam address, BlockRefParam blockRefParam) throws Exception;

    String eth_getBlockTransactionCountByHash(BlockHashParam blockHash)throws Exception;

    String eth_getBlockTransactionCountByNumber(String bnOrId)throws Exception;

    String eth_getUncleCountByBlockHash(String blockHash)throws Exception;

    String eth_getUncleCountByBlockNumber(String bnOrId)throws Exception;

    default String eth_getCode(String address, String blockId) {
        return getEthModule().getCode(address, blockId);
    }

    String eth_getCode(String address, Map<String, String> blockRef) throws Exception; // NOSONAR

    default String eth_sendRawTransaction(HexDataParam rawData) {
        return getEthModule().sendRawTransaction(rawData);
    }

    default String eth_sendTransaction(CallArgumentsParam args) {
        return getEthModule().sendTransaction(args);
    }

    BlockResultDTO eth_getBlockByHash(BlockHashParam blockHash, Boolean fullTransactionObjects) throws Exception;

    BlockResultDTO eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception;

    TransactionResultDTO eth_getTransactionByHash(TxHashParam transactionHash) throws Exception;

    TransactionResultDTO eth_getTransactionByBlockHashAndIndex(BlockHashParam blockHash, HexIndexParam index) throws Exception;

    TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception;

    TransactionReceiptDTO eth_getTransactionReceipt(TxHashParam transactionHash) throws Exception;

    BlockResultDTO eth_getUncleByBlockHashAndIndex(BlockHashParam blockHash, HexIndexParam uncleIdx) throws Exception;

    BlockResultDTO eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception;

    String[] eth_getCompilers();

    Map<String, CompilationResultDTO> eth_compileLLL(String contract);

    Map<String, CompilationResultDTO> eth_compileSerpent(String contract);

    Map<String, CompilationResultDTO> eth_compileSolidity(String contract);

    String eth_newFilter(FilterRequestParam fr) throws Exception;

    String eth_newBlockFilter();

    String eth_newPendingTransactionFilter();

    boolean eth_uninstallFilter(HexIndexParam id);

    Object[] eth_getFilterChanges(HexIndexParam id);

    Object[] eth_getFilterLogs(HexIndexParam id);

    Object[] eth_getLogs(FilterRequestParam fr) throws Exception;

    BigInteger eth_netHashrate();

    boolean eth_submitWork(String nonce, String header, String mince);

    boolean eth_submitHashrate(String hashrate, String id);
}
