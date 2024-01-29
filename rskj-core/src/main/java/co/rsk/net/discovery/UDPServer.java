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
import co.rsk.util.ExecState;
import com.google.common.annotations.VisibleForTesting;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created by mario on 10/02/17.
 */
public class UDPServer implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private static final int BUFFER_SIZE = 8_192;

    private final int port;
    private final String address;

    private final PeerExplorer peerExplorer;
    private final Function<Runnable, Thread> threadFactory;

    private Channel channel;

    private volatile ExecState state = ExecState.CREATED;

    public UDPServer(String address, int port, @Nonnull PeerExplorer peerExplorer) {
        this(address, port, peerExplorer, makeDefaultThreadFactory());
    }

    @VisibleForTesting
    UDPServer(String address, int port, @Nonnull PeerExplorer peerExplorer, @Nonnull Function<Runnable, Thread> threadFactory) {
        this.address = address;
        this.port = port;
        this.peerExplorer = Objects.requireNonNull(peerExplorer);
        this.threadFactory = Objects.requireNonNull(threadFactory);
    }

    @Override
    public synchronized void start() {
        if (state != ExecState.CREATED) {
            logger.warn("Cannot start UDPServer as current state is {}", state);
            return;
        }

        if (port == 0) {
            logger.error("Discovery can't be started while listen port == 0");
        } else {
            state = ExecState.RUNNING;

            startThread();
        }
    }

    @Override
    public synchronized void stop()  {
        if (state != ExecState.RUNNING) {
            logger.warn("Cannot stop UDPServer as current state is {}", state);
            return;
        }

        state = ExecState.FINISHED;

        logger.info("Closing UDPListener...");

        if (channel != null) {
            try {
                channel.close().await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Couldn't stop the UDP Server", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startThread() {
        threadFactory.apply(() -> {
            try {
                startUDPServer();
            } catch (Exception e) {
                logger.error("Discovery can't be started. ", e);
                throw new PeerDiscoveryException("Discovery can't be started. ", e);
            }
        }).start();
    }

    private void startUDPServer() throws InterruptedException {
        logger.info("Discovery UDPListener started");

        EventLoopGroup group = new NioEventLoopGroup(1);

        while (true) {
            Channel channel = createChannel(group);
            if (channel == null) {
                break;
            }
            channel.closeFuture().sync();

            if (!isRunning()) {
                logger.warn("UDP server is not running anymore. Finishing...");
                break;
            }

            logger.warn("UDP channel closed. Recreating after 5 sec pause...");
            TimeUnit.SECONDS.sleep(5);
        }

        group.shutdownGracefully().sync();
    }

    private boolean isRunning() {
        return state == ExecState.RUNNING;
    }

    @Nullable
    private synchronized Channel createChannel(EventLoopGroup group) throws InterruptedException {
        if (state != ExecState.RUNNING) {
            logger.warn("Cannot create channel as UPDServer is not running. Returning null");
            return null;
        }

        Bootstrap bootstrap = this.createBootstrap(group);
        channel = bootstrap.bind(address, port).sync().channel();
        return channel;
    }

    @VisibleForTesting
    ExecState getState() {
        return state;
    }

    private Bootstrap createBootstrap(EventLoopGroup group) {
        return new Bootstrap().group(group).channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    public void initChannel(@Nonnull NioDatagramChannel ch) {
                        DatagramChannelConfig channelConfig = ch.config();
                        channelConfig.setRecvByteBufAllocator(new FixedRecvByteBufAllocator(BUFFER_SIZE));

                        Integer defaultSndBuf = channelConfig.getOption(ChannelOption.SO_SNDBUF);
                        if (defaultSndBuf == null || defaultSndBuf < BUFFER_SIZE) {
                            logger.info("Default {} size of {} bytes is not sufficient. Changing to {}",
                                    ChannelOption.SO_SNDBUF, defaultSndBuf, BUFFER_SIZE);
                            channelConfig.setOption(ChannelOption.SO_SNDBUF, BUFFER_SIZE);
                        }

                        Integer defaultRcvBuf = channelConfig.getOption(ChannelOption.SO_RCVBUF);
                        if (defaultRcvBuf == null || defaultRcvBuf < BUFFER_SIZE) {
                            logger.info("Default {} size of {} bytes is not sufficient. Changing to {}",
                                    ChannelOption.SO_RCVBUF, defaultRcvBuf, BUFFER_SIZE);
                            channelConfig.setOption(ChannelOption.SO_RCVBUF, BUFFER_SIZE);
                        }

                        logger.info("Init channel with {}({}), {}={}, {}={}",
                                FixedRecvByteBufAllocator.class.getSimpleName(),
                                BUFFER_SIZE,
                                ChannelOption.SO_SNDBUF, channelConfig.getOption(ChannelOption.SO_SNDBUF),
                                ChannelOption.SO_RCVBUF, channelConfig.getOption(ChannelOption.SO_RCVBUF));

                        ch.pipeline().addLast(new PacketDecoder());
                        UDPChannel udpChannel = new UDPChannel(ch, peerExplorer);
                        peerExplorer.setUDPChannel(udpChannel);
                        ch.pipeline().addLast(udpChannel);
                    }
                });
    }

    private static Function<Runnable, Thread> makeDefaultThreadFactory() {
        return (Runnable r) -> new Thread(r, "UDPServer");
    }
}

