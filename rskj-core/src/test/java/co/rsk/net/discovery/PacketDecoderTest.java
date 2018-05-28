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

//import co.rsk.net.discovery.message.GetNodeMessage;
//import co.rsk.net.discovery.message.SendNodesMessage;
import co.rsk.net.discovery.message.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.spongycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Created by mario on 15/02/17.
 */
public class PacketDecoderTest {

    private static final OptionalInt NETWORK_ID = OptionalInt.of(1);
    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";

    @Test
    public void decode() throws Exception {

        ECKey key1 = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        String check = UUID.randomUUID().toString();
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);

        PacketDecoder decoder = new PacketDecoder();

        //Decode Ping Message
        PingPeerMessage nodeMessage = PingPeerMessage.create("localhost", 44035, check, key1, NETWORK_ID);
        InetSocketAddress sender = new InetSocketAddress("localhost", 44035);
        this.assertDecodedMessage(decoder.decodeMessage(ctx, nodeMessage.getPacket(), sender), sender, DiscoveryMessageType.PING);

        //Decode Pong Message
        PongPeerMessage pongPeerMessage = PongPeerMessage.create("localhost", 44036, check, key1, NETWORK_ID);
        sender = new InetSocketAddress("localhost", 44036);
        this.assertDecodedMessage(decoder.decodeMessage(ctx, pongPeerMessage.getPacket(), sender), sender, DiscoveryMessageType.PONG);

        //Decode Find Node Message
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(key1.getNodeId(), check, key1, NETWORK_ID);
        sender = new InetSocketAddress("localhost", 44037);
        this.assertDecodedMessage(decoder.decodeMessage(ctx, findNodePeerMessage.getPacket(), sender), sender, DiscoveryMessageType.FIND_NODE);

        //Decode Neighbors Message
        NeighborsPeerMessage neighborsPeerMessage = NeighborsPeerMessage.create(new ArrayList<>(), check, key1, NETWORK_ID);
        sender = new InetSocketAddress("localhost", 44038);
        this.assertDecodedMessage(decoder.decodeMessage(ctx, neighborsPeerMessage.getPacket(), sender), sender, DiscoveryMessageType.NEIGHBORS);

    }

    @Test(expected = PeerDiscoveryException.class)
    public void decodeInvalidMessage() throws Exception {

        PacketDecoder decoder = new PacketDecoder();
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        InetSocketAddress sender = new InetSocketAddress("localhost", 44035);
        Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.remoteAddress()).thenReturn(sender);

        decoder.decodeMessage(ctx, new byte[] {11}, sender);

        Assert.fail();
    }

    private void assertDecodedMessage(DiscoveryEvent event, InetSocketAddress sender, DiscoveryMessageType messageType) {
        Assert.assertEquals(messageType, event.getMessage().getMessageType());
        Assert.assertEquals(sender, event.getAddress());
        Assert.assertNotNull(event.getMessage().getPacket());
        Assert.assertNotNull(event.getMessage().getMdc());
        Assert.assertNotNull(event.getMessage().getSignature());
        Assert.assertNotNull(event.getMessage().getType());
        Assert.assertNotNull(event.getMessage().getData());
    }
}
