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

package org.ethereum.net.submit;

import org.ethereum.core.Transaction;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Roman Mandeleil
 * @since 23.05.2014
 */
public class TransactionTask implements Callable<List<Transaction>> {

    private static final Logger logger = LoggerFactory.getLogger("net");

    private final List<Transaction> tx;
    private final ChannelManager channelManager;
    private final Channel receivedFrom;

    public TransactionTask(Transaction tx, ChannelManager channelManager) {
        this(Collections.singletonList(tx), channelManager);
    }

    public TransactionTask(List<Transaction> tx, ChannelManager channelManager) {
        this(tx, channelManager, null);
    }

    public TransactionTask(List<Transaction> tx, ChannelManager channelManager, Channel receivedFrom) {
        this.tx = tx;
        this.channelManager = channelManager;
        this.receivedFrom = receivedFrom;
    }

    @Override
    public List<Transaction> call() throws Exception {

        try {
            logger.trace("submit tx: {}", tx.toString());
            channelManager.sendTransaction(tx, receivedFrom);
            return tx;

        } catch (Throwable th) {
            logger.warn("Exception caught: {}", th);
        }
        return null;
    }
}
