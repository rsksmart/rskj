/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.rpc;

import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;

import java.util.Arrays;
import java.util.Map;

public interface Web3 {

    public class SyncingResult {
        public String startingBlock;
        public String currentBlock;
        public String highestBlock;
    }

    public class CallArguments {
        public String from;
        public String to;
        public String gas;
        public String gasPrice;
        public String value;
        public String data; // compiledCode
        public String nonce;

        @Override
        public String toString() {
            return "CallArguments{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    ", gasLimit='" + gas + '\'' +
                    ", gasPrice='" + gasPrice + '\'' +
                    ", value='" + value + '\'' +
                    ", data='" + data + '\'' +
                    ", nonce='" + nonce + '\'' +
                    '}';
        }
    }

    class BlockInformationResult {
        public String hash;
        public String totalDifficulty;
        public boolean inMainChain;
    }

    class BlockResult {
        public String number; // QUANTITY - the block number. null when its pending block.
        public String hash; // DATA, 32 Bytes - hash of the block. null when its pending block.
        public String parentHash; // DATA, 32 Bytes - hash of the parent block.
        public String sha3Uncles; // DATA, 32 Bytes - SHA3 of the uncles data in the block.
        public String logsBloom; // DATA, 256 Bytes - the bloom filter for the logs of the block. null when its pending block.
        public String transactionsRoot; // DATA, 32 Bytes - the root of the transaction trie of the block.
        public String stateRoot; // DATA, 32 Bytes - the root of the final state trie of the block.
        public String receiptsRoot; // DATA, 32 Bytes - the root of the receipts trie of the block.
        public String miner; // DATA, 20 Bytes - the address of the beneficiary to whom the mining rewards were given.
        public String difficulty; // QUANTITY - integer of the difficulty for this block.
        public String totalDifficulty; // QUANTITY - integer of the total difficulty of the chain until this block.
        public String extraData; // DATA - the "extra data" field of this block
        public String size;//QUANTITY - integer the size of this block in bytes.
        public String gasLimit;//: QUANTITY - the maximum gas allowed in this block.
        public String gasUsed; // QUANTITY - the total used gas by all transactions in this block.
        public String timestamp; //: QUANTITY - the unix timestamp for when the block was collated.
        public Object[] transactions; //: Array - Array of transaction objects, or 32 Bytes transaction hashes depending on the last given parameter.
        public String[] uncles; //: Array - Array of uncle hashes.
        public String minimumGasPrice;

        @Override
        public String toString() {
            return "BlockResult{" +
                    "number='" + number + '\'' +
                    ", hash='" + hash + '\'' +
                    ", parentHash='" + parentHash + '\'' +
                    ", sha3Uncles='" + sha3Uncles + '\'' +
                    ", logsBloom='" + logsBloom + '\'' +
                    ", transactionsRoot='" + transactionsRoot + '\'' +
                    ", stateRoot='" + stateRoot + '\'' +
                    ", receiptsRoot='" + receiptsRoot + '\'' +
                    ", miner='" + miner + '\'' +
                    ", difficulty='" + difficulty + '\'' +
                    ", totalDifficulty='" + totalDifficulty + '\'' +
                    ", extraData='" + extraData + '\'' +
                    ", size='" + size + '\'' +
                    ", gasLimit='" + gasLimit + '\'' +
                    ", minimumGasPrice='" + minimumGasPrice + '\'' +
                    ", gasUsed='" + gasUsed + '\'' +
                    ", timestamp='" + timestamp + '\'' +
                    ", transactions=" + Arrays.toString(transactions) +
                    ", uncles=" + Arrays.toString(uncles) +
                    '}';
        }
    }

    class FilterRequest {
        public String fromBlock;
        public String toBlock;
        public Object address;
        public Object[] topics;

        @Override
        public String toString() {
            return "FilterRequest{" +
                    "fromBlock='" + fromBlock + '\'' +
                    ", toBlock='" + toBlock + '\'' +
                    ", address=" + address +
                    ", topics=" + Arrays.toString(topics) +
                    '}';
        }
    }

    String web3_clientVersion();
    String web3_sha3(String data) throws Exception;
    String net_version();
    String net_peerCount();
    boolean net_listening();
    String rsk_protocolVersion();
    String eth_protocolVersion();
    Object eth_syncing();
    String eth_coinbase();
    boolean eth_mining();
    String eth_hashrate();
    String eth_gasPrice();
    String[] eth_accounts();
    String eth_blockNumber();
    String eth_getBalance(String address, String block) throws Exception;
    String eth_getBalance(String address) throws Exception;
    String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception;
    String eth_getTransactionCount(String address, String blockId) throws Exception ;
    String eth_getBlockTransactionCountByHash(String blockHash)throws Exception;
    String eth_getBlockTransactionCountByNumber(String bnOrId)throws Exception;
    String eth_getUncleCountByBlockHash(String blockHash)throws Exception;
    String eth_getUncleCountByBlockNumber(String bnOrId)throws Exception;
    String eth_getCode(String addr, String bnOrId)throws Exception;
    String eth_sign(String addr,String data) throws Exception;
    String eth_sendTransaction(CallArguments transactionArgs) throws Exception;
    String eth_sendRawTransaction(String rawData) throws Exception;
    String eth_call(CallArguments args, String bnOrId) throws Exception;
    String eth_estimateGas(CallArguments args) throws Exception;
    BlockResult eth_getBlockByHash(String blockHash,Boolean fullTransactionObjects) throws Exception;
    BlockResult eth_getBlockByNumber(String bnOrId,Boolean fullTransactionObjects) throws Exception;
    TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception;
    TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash,String index) throws Exception;
    TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId,String index) throws Exception;
    TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception;
    BlockResult eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception;
    BlockResult eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception;
    String[] eth_getCompilers();
    Map<String, CompilationResultDTO> eth_compileLLL(String contract);
    Map<String, CompilationResultDTO> eth_compileSolidity(String contract) throws Exception;
    Map<String, CompilationResultDTO> eth_compileSerpent(String contract);

    String eth_newFilter(FilterRequest fr) throws Exception;
    String eth_newBlockFilter();
    String eth_newPendingTransactionFilter();
    boolean eth_uninstallFilter(String id);
    Object[] eth_getFilterChanges(String id);
    Object[] eth_getFilterLogs(String id);
    Object[] eth_getLogs(FilterRequest fr) throws Exception;

    // methods required by dev environments
    Map<String, String> rpc_modules();

    void db_putString();
    void db_getString();
    boolean eth_submitWork(String nonce, String header, String mince);
    boolean eth_submitHashrate(String hashrate, String id);
    void db_putHex();
    void db_getHex();

    String eth_addAccount(String privKey);

    String personal_newAccountWithSeed(String seed);
    String personal_newAccount(String passphrase);
    String[] personal_listAccounts();
    String personal_importRawKey(String key, String passphrase);
    String personal_sendTransaction(CallArguments transactionArgs, String passphrase) throws Exception;
    boolean personal_unlockAccount(String key, String passphrase, String duration);
    boolean personal_lockAccount(String key);
    String personal_dumpRawKey(String address) throws Exception;

    String eth_netHashrate();
    String[] net_peerList();
    Map<String, Object> eth_bridgeState() throws Exception;

    String evm_snapshot();
    boolean evm_revert(String snapshotId);
    void evm_reset();
    void evm_mine();
    String evm_increaseTime(String seconds);

    void sco_addBannedAddress(String address);
    void sco_removeBannedAddress(String address);
}
