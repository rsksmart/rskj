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


import co.rsk.net.discovery.message.MessageDecoder;
import co.rsk.net.discovery.message.PeerDiscoveryMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.util.List;

public class PacketDecoder extends MessageToMessageDecoder<DatagramPacket> {
    private static final Logger logger = LoggerFactory.getLogger(PacketDecoder.class);

    @Override
    public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        ByteBuf buf = packet.content();
        byte[] encoded = new byte[buf.readableBytes()];
        buf.readBytes(encoded);
        out.add(this.decodeMessage(ctx, encoded, packet.sender()));
    }

    public DiscoveryEvent decodeMessage(ChannelHandlerContext ctx, byte[] encoded, InetSocketAddress sender) {
        try {
            PeerDiscoveryMessage msg = MessageDecoder.decode(encoded);
            return new DiscoveryEvent(msg, sender);
        } catch (Exception e) {
            logger.error("Exception processing inbound message from {} : {}", ctx.channel().remoteAddress(), Hex.toHexString(encoded), e);
            throw e;
        }
    }
}
