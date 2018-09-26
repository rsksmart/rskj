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
import org.ethereum.rpc.Web3;

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
        return txHash;
    }

    @Override
    public String sendRawTransaction(String rawData) {
        String txHash = super.sendRawTransaction(rawData);
        mineTransaction();
        return txHash;
    }

    private void mineTransaction() {
        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        minerClient.mineBlock();
    }
}
