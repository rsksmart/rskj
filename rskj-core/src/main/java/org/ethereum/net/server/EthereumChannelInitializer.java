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

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthereumChannelInitializer extends ChannelInitializer<NioSocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger("net");

    private final String remoteId;
    private final ChannelManager channelManager;
    private final ChannelFactory channelFactory;

    public EthereumChannelInitializer(String remoteId, ChannelManager channelManager, ChannelFactory channelFactory) {
        this.remoteId = remoteId;
        this.channelManager = channelManager;
        this.channelFactory = channelFactory;
    }

    @Override
    public void initChannel(NioSocketChannel ch) throws Exception {
        try {
            logger.info("Open {} connection, channel: {}", isInbound() ? "inbound" : "outbound", ch);

            if (isInbound() && channelManager.isRecentlyDisconnected(ch.remoteAddress().getAddress())) {
                // avoid too frequent connection attempts
                logger.info("Drop connection - the same IP was disconnected recently, channel: {}", ch);
                ch.disconnect();
                return;
            }

            final Channel channel = channelFactory.newInstance();
            channel.init(ch.pipeline(), remoteId);
            channelManager.add(channel);

            // limit the size of receiving buffer to 1024
            ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(16_777_216));
            ch.config().setOption(ChannelOption.SO_RCVBUF, 16_777_216);
            ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);

            // be aware of channel closing
            ch.closeFuture().addListener(f -> channelManager.notifyDisconnect(channel));

        } catch (Exception e) {
            logger.error("Unexpected error: ", e);
        }
    }

    private boolean isInbound() {
        return remoteId == null || remoteId.isEmpty();
    }

    public interface ChannelFactory {
        Channel newInstance();
    }
}
