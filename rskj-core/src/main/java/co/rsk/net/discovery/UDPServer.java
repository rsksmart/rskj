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

import co.rsk.config.InternalService;
import co.rsk.net.discovery.upnp.UpnpProtocol;
import co.rsk.net.discovery.upnp.UpnpService;
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
public class UDPServer implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);
    private static final String PEER_DISCOVERY_PORT_MAPPING_DESCRIPTION = "RSK peer discovery";

    private int port;
    private String address;

    private Channel channel;
    private volatile boolean shutdown = false;

    private PeerExplorer peerExplorer;
    private UpnpService upnpService;

    public UDPServer(String address, int port, PeerExplorer peerExplorer) {
        this(address, port, peerExplorer, null);
    }

    public UDPServer(String address, int port, PeerExplorer peerExplorer, UpnpService upnpService) {
        this.address = address;
        this.port = port;
        this.peerExplorer = peerExplorer;
        this.upnpService = upnpService;
    }

    @Override
    public void start() {
        if (port == 0) {
            logger.error("Discovery can't be started while listen port == 0");
        } else {
            new Thread("UDPServer") {
                @Override
                public void run() {
                    try {
                        UDPServer.this.doPortMappingIfEnabled();
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

    private void doPortMappingIfEnabled() {
        if (upnpService != null) {
            upnpService.findGateway(address)
                    .ifPresent(gateway -> gateway.addPortMapping(
                            port,
                            port,
                            UpnpProtocol.UDP,
                            PEER_DISCOVERY_PORT_MAPPING_DESCRIPTION
                    ));
        }
    }

    @Override
    public void stop()  {
        logger.info("Closing UDPListener...");
        shutdown = true;

        if (channel != null) {
            try {
                channel.close().await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Couldn't stop the UDP Server", e);
                Thread.currentThread().interrupt();
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

