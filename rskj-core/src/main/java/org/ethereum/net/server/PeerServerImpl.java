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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import org.ethereum.config.SystemProperties;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.EthereumChannelInitializerFactory;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class establishes a listener for incoming connections.
 * See <a href="http://netty.io">http://netty.io</a>.
 */
public class PeerServerImpl implements PeerServer {

    private static final Logger logger = LoggerFactory.getLogger("net");

    private final SystemProperties config;
    private final EthereumListener ethereumListener;
    private final EthereumChannelInitializerFactory ethereumChannelInitializerFactory;

    // TODO review this variable use
    private boolean listening;
    private ExecutorService peerServiceExecutor;

    public PeerServerImpl(SystemProperties config, EthereumListener ethereumListener, EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        this.config = config;
        this.ethereumListener = ethereumListener;
        this.ethereumChannelInitializerFactory = ethereumChannelInitializerFactory;
    }

    @Override
    public void start() {
        if (config.getPeerPort() > 0) {
            peerServiceExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Peer Server");
                thread.setUncaughtExceptionHandler(
                        (exceptionThread, exception) -> logger.error("Unable to start peer server", exception)
                );
                return thread;
            });
            peerServiceExecutor.execute(() -> start(config.getBindAddress(), config.getPeerPort()));
        }

        logger.info("RskJ node started: enode://{}@{}:{}" , ByteUtil.toHexString(config.nodeId()), config.getPublicIp(), config.getPeerPort());
    }

    @Override
    public void stop() {
        if (peerServiceExecutor != null) {
            peerServiceExecutor.shutdown();
        }
    }

    private void start(InetAddress host, int port) {
        // TODO review listening use
        listening = true;

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        EthereumChannelInitializer ethereumChannelInitializer = ethereumChannelInitializerFactory.newInstance("");

        ethereumListener.trace("Listening on port " + port);


        try {
            ServerBootstrap b = new ServerBootstrap();

            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);

            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
            b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.peerConnectionTimeout());

            b.handler(new LoggingHandler());
            b.childHandler(ethereumChannelInitializer);

            // Start the client.
            logger.info("Listening for incoming connections, hosts: {}, port: [{}] ", host, port);
            logger.info("NodeId: [{}] ", ByteUtil.toHexString(config.nodeId()));

            ChannelFuture f = b.bind(host, port).sync();

            // Wait until the connection is closed.
            f.channel().closeFuture().sync();
            logger.debug("Connection is closed");

            // TODO review listening use
            listening = false;
        } catch (Exception e) {
            logger.debug("Exception: {} ({})", e.getMessage(), e.getClass().getName());
            throw new Error("Server Disconnected");
        } finally {
            workerGroup.shutdownGracefully();

        }
    }

    public boolean isListening() {
        return listening;
    }
}
