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

package org.ethereum.net.eth.handler;

import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.sync.SyncStatistics;

import static org.ethereum.net.eth.EthVersion.*;

/**
 * It's quite annoying to always check {@code if (eth != null)} before accessing it. <br>
 *
 * This adapter helps to avoid such checks. It provides meaningful answers to Eth client
 * assuming that Eth hasn't been initialized yet. <br>
 *
 * Check {@link org.ethereum.net.server.Channel} for example.
 *
 * @author Mikhail Kalinin
 * @since 20.08.2015
 */
public class EthAdapter implements Eth {

    private final SyncStatistics syncStats = new SyncStatistics();

    @Override
    public SyncStatistics getStats() {
        return syncStats;
    }

    @Override
    public EthVersion getVersion() {
        return fromCode(UPPER);
    }

    @Override
    public void sendStatus() {
    }

    @Override
    public void dropConnection() {
    }

    @Override
    public void sendMessage(EthMessage message) {
    }

}
