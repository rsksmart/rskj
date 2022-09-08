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

package co.rsk.net.discovery;

import co.rsk.net.discovery.message.PingPeerMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Created by mario on 15/02/17.
 */
public class UDPChannelTest {

    private static final int NETWORK_ID = 1;

    @Test
    public void create() {
        Channel channel = Mockito.mock(Channel.class);
        PeerExplorer peerExplorer = Mockito.mock(PeerExplorer.class);
        UDPChannel udpChannel = new UDPChannel(channel, peerExplorer);

        Assertions.assertNotNull(udpChannel);
    }

    @Test
    public void channelRead0() throws Exception {
        Channel channel = Mockito.mock(Channel.class);
        PeerExplorer peerExplorer = Mockito.mock(PeerExplorer.class);
        UDPChannel udpChannel = new UDPChannel(channel, peerExplorer);

        DiscoveryEvent event = Mockito.mock(DiscoveryEvent.class);
        udpChannel.channelRead0(Mockito.mock(ChannelHandlerContext.class), event);

        Mockito.verify(peerExplorer, Mockito.times(1)).handleMessage(event);
    }

    @Test
    public void write() {
        String check = UUID.randomUUID().toString();
        ECKey key = new ECKey();
        PingPeerMessage nodeMessage = PingPeerMessage.create("localhost", 80, check, key, NETWORK_ID);

        Channel channel = Mockito.mock(Channel.class);
        PeerExplorer peerExplorer = Mockito.mock(PeerExplorer.class);
        UDPChannel udpChannel = new UDPChannel(channel, peerExplorer);

        udpChannel.write(new DiscoveryEvent(nodeMessage, new InetSocketAddress("localhost", 8080)));

        Mockito.verify(channel, Mockito.times(1)).write(Mockito.any());
        Mockito.verify(channel, Mockito.times(1)).flush();
    }

    @Test
    public void channelActive() throws Exception {
        Channel channel = Mockito.mock(Channel.class);
        PeerExplorer peerExplorer = Mockito.mock(PeerExplorer.class);
        UDPChannel udpChannel = new UDPChannel(channel, peerExplorer);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);

        udpChannel.channelActive(ctx);
        Mockito.verify(peerExplorer, Mockito.times(1)).start();
    }

}
