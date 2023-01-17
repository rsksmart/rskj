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
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.listener.EthereumListener;

/**
 * @author Roman Mandeleil
 * @since 27.07.2014
 */
public interface Ethereum {

    void addListener(EthereumListener listener);

    void removeListener(EthereumListener listener);

    ImportResult addNewMinedBlock(Block block);

    /**
     * @param transaction submit transaction to the net, return option to wait for net
     * @return the result of adding the transaction to the pool.
     */
    TransactionPoolAddResult submitTransaction(Transaction transaction);

    /**
     * Calculates a 'reasonable' Gas price based on statistics of the latest transaction's Gas prices
     * Normally the price returned should be sufficient to execute a transaction since ~25% of the latest
     * transactions were executed at this or lower price.
     * If the transaction is wanted to be executed promptly with higher chances the returned price might
     * be increased at some ratio (e.g. * 1.2)
     */
    Coin getGasPrice();
}
