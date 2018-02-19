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

package co.rsk.net.handler;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TxHandler validates the incoming transactions
 * It does not check the nonce sequence by account,
 * TransactionPool controls that sequence, with two
 * list of transactions: pendig (in sequence), queued (out of sequence)
 */
public class TxHandlerImpl implements TxHandler {
    private final RskSystemProperties config;
    private Repository repository;
    private Blockchain blockchain;

    public TxHandlerImpl(RskSystemProperties config, CompositeEthereumListener compositeEthereumListener, Repository repository, Blockchain blockchain) {
        this.config = config;
        this.blockchain = blockchain;
        this.repository = repository;
    }

    @VisibleForTesting
    TxHandlerImpl(RskSystemProperties config) {
        // Only for testing
        this.config = config;
    }

    @Override
    public List<Transaction> retrieveValidTxs(List<Transaction> txs) {
        return new TxValidator(config, repository, blockchain).filterTxs(txs);
    }
}
