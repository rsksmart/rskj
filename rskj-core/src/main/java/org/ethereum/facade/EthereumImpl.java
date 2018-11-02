/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.facade;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import org.ethereum.core.*;
import org.ethereum.core.TransactionPool;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.util.ByteUtil;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public class EthereumImpl implements Ethereum {

    private final ChannelManager channelManager;
    private final TransactionPool transactionPool;
    private final RskSystemProperties config;
    private final CompositeEthereumListener compositeEthereumListener;
    private final Blockchain blockchain;

    private GasPriceTracker gasPriceTracker = new GasPriceTracker();

    public EthereumImpl(
            RskSystemProperties config,
            ChannelManager channelManager,
            TransactionPool transactionPool,
            CompositeEthereumListener compositeEthereumListener,
            Blockchain blockchain) {
        this.channelManager = channelManager;
        this.transactionPool = transactionPool;

        this.config = config;
        this.compositeEthereumListener = compositeEthereumListener;
        this.blockchain = blockchain;

        compositeEthereumListener.addListener(gasPriceTracker);
    }

    @Override
    public ImportResult addNewMinedBlock(final @Nonnull Block block) {
        final ImportResult importResult = blockchain.tryToConnect(block);

        if (blockchain.getBlockByHash(block.getHash().getBytes()) != null) {
            channelManager.broadcastBlock(block);
        }
        return importResult;
    }

    @Override
    public void addListener(EthereumListener listener) {
        compositeEthereumListener.addListener(listener);
    }

    @Override
    public void removeListener(EthereumListener listener) {
        compositeEthereumListener.removeListener(listener);
    }

    @Override
    public Transaction createTransaction(BigInteger nonce,
                                         BigInteger gasPrice,
                                         BigInteger gas,
                                         byte[] receiveAddress,
                                         BigInteger value, byte[] data) {

        byte[] nonceBytes = ByteUtil.bigIntegerToBytes(nonce);
        byte[] gasPriceBytes = ByteUtil.bigIntegerToBytes(gasPrice);
        byte[] gasBytes = ByteUtil.bigIntegerToBytes(gas);
        byte[] valueBytes = ByteUtil.bigIntegerToBytes(value);
        byte chainId = config.getBlockchainConfig().getCommonConstants().getChainId();

        return new Transaction(nonceBytes, gasPriceBytes, gasBytes,
                receiveAddress, valueBytes, data, chainId);
    }

    @Override
    public void submitTransaction(Transaction transaction) {
        transactionPool.addTransaction(transaction);
    }

    @Override
    public Coin getGasPrice() {
        return gasPriceTracker.getGasPrice();
    }
}
