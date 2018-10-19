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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class UDPChannel extends SimpleChannelInboundHandler<DiscoveryEvent> {
    static final Logger logger = LoggerFactory.getLogger(UDPChannel.class);

    private Channel channel;

    private PeerExplorer peerExplorer;

    public UDPChannel(Channel ch, PeerExplorer peerExplorer) {
        this.channel = ch;
        this.peerExplorer = peerExplorer;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, DiscoveryEvent event) throws Exception {
        this.peerExplorer.handleMessage(event);
    }

    public void write(DiscoveryEvent discoveryEvent) {
        InetSocketAddress address = discoveryEvent.getAddress();
        sendPacket(discoveryEvent.getMessage().getPacket(), address);
    }

    void sendPacket(byte[] wire, InetSocketAddress address) {
        DatagramPacket packet = new DatagramPacket(Unpooled.copiedBuffer(wire), address);
        channel.write(packet);
        channel.flush();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.debug("Discover channel error", cause);
        // We don't close the channel because we can keep serving requests.
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        peerExplorer.start();
    }

}
