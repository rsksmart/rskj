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

package org.ethereum.net.rlpx;

import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringManager;
import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.p2p.DisconnectMessage;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.p2p.P2pMessageCodes;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static org.ethereum.net.eth.EthVersion.fromCode;
import static org.ethereum.net.rlpx.FrameCodec.Frame;

/**
 * The Netty handler which manages initial negotiation with peer
 * (when either we initiating connection or remote peer initiates)
 *
 * The initial handshake includes:
 * - first AuthInitiate -> AuthResponse messages when peers exchange with secrets
 * - second P2P Hello messages when P2P protocol and subprotocol capabilities are negotiated
 *
 * After the handshake is done this handler reports secrets and other data to the Channel
 * which installs further handlers depending on the protocol parameters.
 * This handler is finally removed from the pipeline.
 */
public class InitiatorHandshakeFrameCodecHandler extends ByteToMessageDecoder {
    private static final Logger loggerWire = LoggerFactory.getLogger("wire");
    private static final Logger loggerNet = LoggerFactory.getLogger("net");

    private final PeerScoringManager peerScoringManager;
    private final P2pHandler p2pHandler;
    private final MessageCodec messageCodec;
    private final ConfigCapabilities configCapabilities;

    private final FrameCodec frameCodec;
    private final Channel channel;

    public InitiatorHandshakeFrameCodecHandler(
            PeerScoringManager peerScoringManager,
            P2pHandler p2pHandler,
            MessageCodec messageCodec,
            ConfigCapabilities configCapabilities,
            Channel channel,
            FrameCodec frameCodec) {
        this.peerScoringManager = peerScoringManager;
        this.p2pHandler = p2pHandler;
        this.messageCodec = messageCodec;
        this.configCapabilities = configCapabilities;
        this.channel = channel;
        this.frameCodec = frameCodec;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        loggerWire.debug("Decoding handshake... ({} bytes available)", in.readableBytes());
        loggerWire.debug("MessageCodec: Buffer bytes: {}", in.readableBytes());
        List<Frame> frames = frameCodec.readFrames(in);
        if (frames == null || frames.isEmpty()) {
            return;
        }
        Frame frame = frames.get(0);
        byte[] payload = ByteStreams.toByteArray(frame.getStream());
        if (frame.getType() == P2pMessageCodes.HELLO.asByte()) {
            HelloMessage helloMessage = new HelloMessage(payload);
            loggerNet.trace("From: \t{} \tRecv: \t{}", ctx.channel().remoteAddress(), helloMessage);
            processHelloMessage(ctx, helloMessage);
        } else {
            DisconnectMessage message = new DisconnectMessage(payload);
            loggerNet.trace("From: \t{} \tRecv: \t{}", channel, message);
            channel.getNodeStatistics().nodeDisconnectedRemote(message.getReason());
        }

        channel.getNodeStatistics().rlpxInHello.add();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        recordFailedHandshake(ctx);
        if (cause instanceof IOException) {
            loggerNet.info("Handshake failed: address {}", ctx.channel().remoteAddress(), cause);
        } else {
            loggerNet.warn("Handshake failed", cause);
        }
        ctx.close();
    }

    private void recordFailedHandshake(ChannelHandlerContext ctx) {
        recordEvent(ctx, EventType.FAILED_HANDSHAKE);
    }

    private void recordSuccessfulHandshake(ChannelHandlerContext ctx) {
        recordEvent(ctx, EventType.SUCCESSFUL_HANDSHAKE);
    }

    private void recordEvent(ChannelHandlerContext ctx, EventType event) {
        SocketAddress socketAddress = ctx.channel().remoteAddress();

        //TODO(mmarquez): what if it is not ??
        if (socketAddress instanceof InetSocketAddress) {
            NodeID nodeID = channel.getNodeId();

            InetAddress address = ((InetSocketAddress)socketAddress).getAddress();

            peerScoringManager.recordEvent(nodeID, address, event);
        }
    }

    private void processHelloMessage(ChannelHandlerContext ctx, HelloMessage helloMessage) {
        List<Capability> capInCommon = configCapabilities.getSupportedCapabilities(helloMessage);
        channel.initMessageCodes(capInCommon);
        for (Capability capability : capInCommon) {
            // It seems that the only supported capability is RSK, and everything else is ignored.
            if (Capability.RSK.equals(capability.getName())) {
                publicRLPxHandshakeFinished(ctx, helloMessage, fromCode(capability.getVersion()));
                return;
            }
        }

        throw new RuntimeException("The remote peer didn't support the RSK capability");
    }

    private void publicRLPxHandshakeFinished(ChannelHandlerContext ctx, HelloMessage helloRemote, EthVersion ethVersion) {
        if (!P2pHandler.isProtocolVersionSupported(helloRemote.getP2PVersion())) {
            throw new RuntimeException(String.format(
                    "The remote peer protocol version %s isn't supported", helloRemote.getP2PVersion()
            ));
        }

        loggerNet.debug("publicRLPxHandshakeFinished with {}", ctx.channel().remoteAddress());
        if (helloRemote.getP2PVersion() < 5) {
            messageCodec.setSupportChunkedFrames(false);
        }

        FrameCodecHandler frameCodecHandler = new FrameCodecHandler(frameCodec, channel);
        ctx.pipeline().addLast("medianFrameCodec", frameCodecHandler);
        ctx.pipeline().addLast("messageCodec", messageCodec);
        ctx.pipeline().addLast(Capability.P2P, p2pHandler);

        p2pHandler.setChannel(channel);
        p2pHandler.setHandshake(helloRemote);
        channel.activateEth(ctx, ethVersion);

        channel.getNodeStatistics().rlpxHandshake.add();

        recordSuccessfulHandshake(ctx);

        loggerWire.debug("Handshake done, removing HandshakeHandler from pipeline.");
        ctx.pipeline().remove(this);
    }

    public interface Factory {
        InitiatorHandshakeFrameCodecHandler newInstance(Channel channel, FrameCodec frameCodec);
    }
}
