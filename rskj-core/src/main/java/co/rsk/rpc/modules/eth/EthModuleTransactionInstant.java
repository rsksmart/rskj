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

package co.rsk.rpc.modules.eth;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Wallet;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.transactionRevertedExecutionError;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.unknownError;

public class EthModuleTransactionInstant extends EthModuleTransactionBase {

    private final MinerServer minerServer;
    private final MinerClient minerClient;
    private final Blockchain blockchain;

    public EthModuleTransactionInstant(RskSystemProperties config, Wallet wallet, TransactionPool transactionPool, MinerServer minerServer, MinerClient minerClient, Blockchain blockchain) {
        super(config,wallet, transactionPool);

        this.minerServer = minerServer;
        this.minerClient = minerClient;
        this.blockchain = blockchain;
    }

    @Override
    public synchronized String sendTransaction(Web3.CallArguments args) {
        String txHash = super.sendTransaction(args);
        mineTransaction();
        return getReturnMessage(txHash);
    }

    @Override
    public String sendRawTransaction(String rawData) {
        String txHash = super.sendRawTransaction(rawData);
        mineTransaction();
        return getReturnMessage(txHash);
    }

    private void mineTransaction() {
        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        minerClient.mineBlock();
    }

    /**
     * When insta-mining we can query the transaction status and return an error response immediately like Ganache.
     * This does not apply during regular operation because queued transactions are not immediately executed.
     */
    private String getReturnMessage(String txHash) {
        TransactionInfo transactionInfo = blockchain.getTransactionInfo(TypeConverter.stringHexToByteArray(txHash));
        if (transactionInfo == null) {
            throw unknownError("Unknown error when sending transaction: transaction wasn't mined");
        }

        if (!transactionInfo.getReceipt().isSuccessful()) {
            throw transactionRevertedExecutionError();
        }

        return txHash;
    }
}
