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

import co.rsk.core.Coin;
import co.rsk.net.TransactionGateway;
import org.ethereum.core.*;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.net.server.ChannelManager;

import javax.annotation.Nonnull;
import java.math.BigDecimal;

public class EthereumImpl implements Ethereum {

    private final ChannelManager channelManager;
    private final TransactionGateway transactionGateway;
    private final CompositeEthereumListener compositeEthereumListener;
    private final Blockchain blockchain;
    private final GasPriceTracker gasPriceTracker;
    private final Double minGasPriceMultiplier;

    public EthereumImpl(
            ChannelManager channelManager,
            TransactionGateway transactionGateway,
            CompositeEthereumListener compositeEthereumListener,
            Blockchain blockchain,
            GasPriceTracker gasPriceTracker,
            Double minGasPriceMultiplier) {
        this.channelManager = channelManager;
        this.transactionGateway = transactionGateway;

        this.compositeEthereumListener = compositeEthereumListener;
        this.blockchain = blockchain;

        this.gasPriceTracker = gasPriceTracker;
        compositeEthereumListener.addListener(gasPriceTracker);
        this.minGasPriceMultiplier = minGasPriceMultiplier;
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
    public TransactionPoolAddResult submitTransaction(Transaction transaction) {
        return transactionGateway.receiveTransaction(transaction);
    }

    @Override
    public Coin getGasPrice() {
        if (gasPriceTracker.isFeeMarketWorking()) {
            return gasPriceTracker.getGasPrice();
        }
        double estimatedGasPrice = blockchain.getBestBlock().getMinimumGasPrice().asBigInteger().doubleValue() * minGasPriceMultiplier;
        return new Coin(BigDecimal.valueOf(estimatedGasPrice).toBigInteger());
    }
}
