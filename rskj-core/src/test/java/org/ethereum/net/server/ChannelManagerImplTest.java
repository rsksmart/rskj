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

package org.ethereum.net.server;

import org.ethereum.net.client.Capability;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.net.p2p.P2pMessageCodes;
import org.ethereum.net.rlpx.MessageCodesResolver;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.ethereum.net.eth.EthVersion.V62;
import static org.junit.Assert.assertEquals;

/**
 * @author Roman Mandeleil
 * @since 15.10.2014
 */
@Ignore
public class ChannelManagerImplTest {

    private ChannelManagerImpl channelManagerImpl;

    @Before
    public void setUp() {
        channelManagerImpl = new ChannelManagerImpl();
    }

    @Test
    public void getNumberOfPeersToSendStatusTo() {
        assertEquals(1, channelManagerImpl.getNumberOfPeersToSendStatusTo(1));
        assertEquals(2, channelManagerImpl.getNumberOfPeersToSendStatusTo(2));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(3));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(5));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(9));
        assertEquals(3, channelManagerImpl.getNumberOfPeersToSendStatusTo(12));
        assertEquals(4, channelManagerImpl.getNumberOfPeersToSendStatusTo(20));
        assertEquals(5, channelManagerImpl.getNumberOfPeersToSendStatusTo(25));
        assertEquals(10, channelManagerImpl.getNumberOfPeersToSendStatusTo(1000));
    }

}
