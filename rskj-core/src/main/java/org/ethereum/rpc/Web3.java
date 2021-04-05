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

import co.rsk.config.InternalService;
import co.rsk.rpc.*;
import co.rsk.scoring.PeerScoringInformation;
import co.rsk.scoring.PeerScoringReputationSummary;

import java.util.Arrays;
import java.util.Map;

public interface Web3 extends InternalService, Web3TxPoolModule, Web3EthModule, Web3EvmModule, Web3MnrModule, Web3DebugModule, Web3TraceModule, Web3RskModule {
    class SyncingResult {
        public String startingBlock;
        public String currentBlock;
        public String highestBlock;
    }

    class CallArguments {
        public String from;
        public String to;
        public String gas;
        public String gasPrice;
        public String value;
        public String data; // compiledCode
        public String nonce;
        public String chainId; //NOSONAR

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
                    ", chainId='" + chainId + '\'' +
                    '}';
        }
    }

    class BlockInformationResult {
        public String hash;
        public String totalDifficulty;
        public boolean inMainChain;
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
    String[] net_peerList();
    String rsk_protocolVersion();

    // methods required by dev environments
    Map<String, String> rpc_modules();

    void db_putString();
    void db_getString();

    void db_putHex();
    void db_getHex();

    String personal_newAccountWithSeed(String seed);
    String personal_newAccount(String passphrase);
    String[] personal_listAccounts();
    String personal_importRawKey(String key, String passphrase);
    String personal_sendTransaction(CallArguments transactionArgs, String passphrase) throws Exception;
    boolean personal_unlockAccount(String key, String passphrase, String duration);
    boolean personal_lockAccount(String key);
    String personal_dumpRawKey(String address) throws Exception;

    void sco_banAddress(String address);
    void sco_unbanAddress(String address);
    PeerScoringInformation[] sco_peerList();
    String[] sco_bannedAddresses();
    PeerScoringReputationSummary sco_reputationSummary();
}
