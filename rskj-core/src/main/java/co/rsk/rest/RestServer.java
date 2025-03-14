/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.rest;

import co.rsk.config.InternalService;
import co.rsk.rest.dto.RestModuleConfigDTO;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

public class RestServer implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger(RestServer.class);

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    private final InetAddress inetHost;
    private final int inetPort;
    private final RestModuleConfigDTO restModuleConfigDTO;

    public RestServer(InetAddress inetHost, int inetPort, RestModuleConfigDTO restModuleConfigDTO) {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        this.inetHost = inetHost;
        this.inetPort = inetPort;
        this.restModuleConfigDTO = restModuleConfigDTO;
    }

    @Override
    public void start() {

        try {

            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new RestServerInitializer(restModuleConfigDTO));

            logger.info("REST Server Ready");

            ChannelFuture channelFuture = serverBootstrap.bind(inetHost, inetPort).sync();
            channelFuture.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            logger.error("REST server couldn't be started", e);
            Thread.currentThread().interrupt();
        } finally {
            stop();
        }

    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        logger.info("REST Server Stopped");
    }

}
