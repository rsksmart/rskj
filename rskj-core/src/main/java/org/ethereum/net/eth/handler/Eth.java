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
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.sync.SyncStatistics;

/**
 * Describes interface required by Eth peer clients
 *
 * @see org.ethereum.net.server.Channel
 *
 * @author Mikhail Kalinin
 * @since 20.08.2015
 */
public interface Eth {

    /**
     * @return sync statistics
     */
    SyncStatistics getStats();

    /**
     * @return protocol version
     */
    EthVersion getVersion();

    /**
     * Sends {@link EthMessageCodes#STATUS} message
     */
    void sendStatus();

    /**
     * Drops connection with remote peer.
     * It should be called when peer don't behave
     */
    void dropConnection();

    // Send eth message directly
    void sendMessage(EthMessage message);

}
