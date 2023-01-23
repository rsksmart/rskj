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

package org.ethereum.net.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.ethereum.config.SystemProperties;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.EthereumChannelInitializerFactory;
import org.ethereum.net.server.EthereumChannelInitializer;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This class creates the connection to an remote address using the Netty framework
 *
 * @see <a href="http://netty.io">http://netty.io</a>
 */
public class PeerClient {

    private final SystemProperties config;
    private final EthereumListener ethereumListener;
    private final EthereumChannelInitializerFactory ethereumChannelInitializerFactory;

    public PeerClient(SystemProperties config, EthereumListener ethereumListener, EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        this.config = config;
        this.ethereumListener = ethereumListener;
        this.ethereumChannelInitializerFactory = ethereumChannelInitializerFactory;
    }

    private static EventLoopGroup workerGroup = new NioEventLoopGroup(0, new ThreadFactory() {
        AtomicInteger cnt = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "EthJClientWorker-" + cnt.getAndIncrement());
        }
    });

    public ChannelFuture connectAsync(String host, int port, String remoteId) {
        ethereumListener.trace("Connecting to: " + host + ":" + port);

        EthereumChannelInitializer ethereumChannelInitializer = ethereumChannelInitializerFactory.newInstance(remoteId);

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);

        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.peerConnectionTimeout());
        b.remoteAddress(host, port);

        b.handler(ethereumChannelInitializer);

        // Start the client.
        return b.connect();
    }

}
