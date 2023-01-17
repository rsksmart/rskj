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

package org.ethereum.rpc.Simples;

import co.rsk.core.Coin;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.listener.TestCompositeEthereumListener;

import javax.annotation.Nonnull;

import static org.mockito.Mockito.mock;

/**
 * Created by Ruben Altman on 09/06/2016.
 */
public class SimpleEthereum implements Ethereum {

    public Transaction tx;
    public Blockchain blockchain;
    private EthereumListener listener;

    public SimpleEthereum() {
        this(mock(Blockchain.class));
    }

    public SimpleEthereum(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    @Override
    public void addListener(EthereumListener listener) {
        if (this.listener == null) {
            this.listener = new TestCompositeEthereumListener();
        }
        ((CompositeEthereumListener) this.listener).addListener(listener);
    }

    @Override
    public void removeListener(EthereumListener listener) {

    }

    @Override
    public ImportResult addNewMinedBlock(final @Nonnull Block block) {
        final ImportResult importResult = blockchain.tryToConnect(block);

        return importResult;
    }

    @Override
    public TransactionPoolAddResult submitTransaction(Transaction transaction) {
        tx = transaction;
        return TransactionPoolAddResult.okPendingTransaction(transaction);
    }

    @Override
    public Coin getGasPrice() {
        return GasPriceTracker.create(mock(BlockStore.class)).getGasPrice();
    }
}
