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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Created by mario on 10/02/17.
 */
public class UDPServer {
    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private int port;
    private String address;

    private Channel channel;
    private volatile boolean shutdown = false;

    private PeerExplorer peerExplorer;

    public UDPServer(String address, int port, PeerExplorer peerExplorer) {
        this.address = address;
        this.port = port;
        this.peerExplorer = peerExplorer;
    }

    public void start() {
        if (port == 0) {
            logger.error("Discovery can't be started while listen port == 0");
        } else {
            new Thread("UDPServer") {
                @Override
                public void run() {
                    try {
                        UDPServer.this.startUDPServer();
                    } catch (Exception e) {
                        logger.error("Discovery can't be started. ", e);
                        throw new PeerDiscoveryException("Discovery can't be started. ", e);
                    }
                }
            }.start();
        }
    }

    public void startUDPServer() throws InterruptedException {
        logger.info("Discovery UDPListener started");
        EventLoopGroup group = new NioEventLoopGroup(1);

        while (!shutdown) {
            Bootstrap bootstrap = this.createBootstrap(group);

            channel = bootstrap.bind(address, port).sync().channel();
            channel.closeFuture().sync();

            logger.warn("UDP channel closed. Recreating after 5 sec pause...");
            TimeUnit.SECONDS.sleep(5);
        }

        group.shutdownGracefully().sync();
    }

    public void stop() throws InterruptedException {
        logger.info("Closing UDPListener...");
        shutdown = true;

        if (channel != null) {
            try {
                channel.close().await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("Problems closing UDPServer", e);
                throw e;
            }
        }
    }

    private Bootstrap createBootstrap(EventLoopGroup group) {
        return new Bootstrap().group(group).channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    public void initChannel(NioDatagramChannel ch)
                            throws Exception {
                        ch.pipeline().addLast(new PacketDecoder());
                        UDPChannel udpChannel = new UDPChannel(ch, peerExplorer);
                        peerExplorer.setUDPChannel(udpChannel);
                        ch.pipeline().addLast(udpChannel);
                    }
                });
    }
}

