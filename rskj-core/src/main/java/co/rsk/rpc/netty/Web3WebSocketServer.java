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
package co.rsk.rpc.netty;

import co.rsk.config.InternalService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

public class Web3WebSocketServer implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger(Web3WebSocketServer.class);

    private final InetAddress host;
    private final int port;
    private final RskWebSocketJsonRpcHandler webSocketJsonRpcHandler;
    private final JsonRpcWeb3ServerHandler web3ServerHandler;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private @Nullable ChannelFuture webSocketChannel;
    private final int serverWriteTimeoutSeconds;
    private final int maxFrameSize;
    private final int maxAggregatedFrameSize;

    public Web3WebSocketServer(
            InetAddress host,
            int port,
            RskWebSocketJsonRpcHandler webSocketJsonRpcHandler,
            JsonRpcWeb3ServerHandler web3ServerHandler,
            int serverWriteTimeoutSeconds,
            int maxFrameSize,
            int maxAggregatedFrameSize) {
        this.host = host;
        this.port = port;
        this.webSocketJsonRpcHandler = webSocketJsonRpcHandler;
        this.web3ServerHandler = web3ServerHandler;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
        this.serverWriteTimeoutSeconds = serverWriteTimeoutSeconds;
        this.maxFrameSize = maxFrameSize;
        this.maxAggregatedFrameSize = maxAggregatedFrameSize;
    }

    @Override
    public void start() {
        logger.info("RPC WebSocket enabled");
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(maxAggregatedFrameSize));
                    p.addLast(new WriteTimeoutHandler(serverWriteTimeoutSeconds, TimeUnit.SECONDS));
                    p.addLast(new RskWebSocketServerProtocolHandler("/", maxFrameSize));
                    p.addLast(new WebSocketFrameAggregator(maxAggregatedFrameSize));
                    p.addLast(webSocketJsonRpcHandler);
                    p.addLast(web3ServerHandler);
                    p.addLast(new Web3ResultWebSocketResponseHandler());
                }
            });
        webSocketChannel = b.bind(host, port);
        try {
            webSocketChannel.sync();
        } catch (InterruptedException e) {
            logger.error("The RPC WebSocket server couldn't be started", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        try {
            webSocketChannel.channel().close().sync();
        } catch (InterruptedException e) {
            logger.error("Couldn't stop the RPC WebSocket server", e);
            Thread.currentThread().interrupt();
        }
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }
}
