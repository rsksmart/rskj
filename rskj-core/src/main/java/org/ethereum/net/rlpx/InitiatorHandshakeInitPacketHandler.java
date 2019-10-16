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
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.SystemProperties;
import org.ethereum.crypto.ECIESCoder;
import org.ethereum.crypto.ECKey;
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
import static org.ethereum.util.ByteUtil.bigEndianToShort;

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
public class InitiatorHandshakeHandler extends ByteToMessageDecoder {

    private final SystemProperties config;
    private final PeerScoringManager peerScoringManager;
    private final P2pHandler p2pHandler;
    private final MessageCodec messageCodec;
    private final ConfigCapabilities configCapabilities;

    private static final Logger loggerWire = LoggerFactory.getLogger("wire");
    private static final Logger loggerNet = LoggerFactory.getLogger("net");

    private FrameCodec frameCodec;
    private ECKey myKey;
    private byte[] nodeId;
    private byte[] remoteId;
    private EncryptionHandshake handshake;
    private byte[] initiatePacket;
    private Channel channel;

    public InitiatorHandshakeHandler(
            SystemProperties config,
            PeerScoringManager peerScoringManager,
            P2pHandler p2pHandler,
            MessageCodec messageCodec,
            ConfigCapabilities configCapabilities,
            String remoteId,
            Channel channel) {
        this.config = config;
        this.peerScoringManager = peerScoringManager;
        this.p2pHandler = p2pHandler;
        this.messageCodec = messageCodec;
        this.configCapabilities = configCapabilities;
        this.remoteId = Hex.decode(remoteId);
        this.channel = channel;
        this.myKey = config.getMyKey();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel.setInetSocketAddress((InetSocketAddress) ctx.channel().remoteAddress());
        channel.setNode(remoteId);
        initiate(ctx);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        loggerWire.debug("Decoding handshake... ({} bytes available)", in.readableBytes());
        decodeHandshake(ctx, in);
    }

    private void initiate(ChannelHandlerContext ctx) throws Exception {

        loggerNet.trace("RLPX protocol activated");

        nodeId = myKey.getNodeId();

        byte[] remotePublicBytes = new byte[remoteId.length + 1];
        System.arraycopy(remoteId, 0, remotePublicBytes, 1, remoteId.length);
        remotePublicBytes[0] = 0x04; // uncompressed
        ECPoint remotePublic = ECKey.fromPublicOnly(remotePublicBytes).getPubKeyPoint();
        handshake = new EncryptionHandshake(remotePublic);

        Object msg;
        if (config.eip8()) {
            AuthInitiateMessageV4 initiateMessage = handshake.createAuthInitiateV4(myKey);
            initiatePacket = handshake.encryptAuthInitiateV4(initiateMessage);
            msg = initiateMessage;
        } else {
            AuthInitiateMessage initiateMessage = handshake.createAuthInitiate(null, myKey);
            initiatePacket = handshake.encryptAuthMessage(initiateMessage);
            msg = initiateMessage;
        }

        final ByteBuf byteBufMsg = ctx.alloc().buffer(initiatePacket.length);
        byteBufMsg.writeBytes(initiatePacket);
        ctx.writeAndFlush(byteBufMsg).sync();

        channel.getNodeStatistics().rlpxAuthMessagesSent.add();

        loggerNet.trace("To: \t{} \tSend: \t{}", ctx.channel().remoteAddress(), msg);
    }

    // consume handshake, producing no resulting message to upper layers
    private void decodeHandshake(final ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        if (frameCodec == null) {
            byte[] responsePacket = new byte[AuthResponseMessage.getLength() + ECIESCoder.getOverhead()];
            if (!buffer.isReadable(responsePacket.length)) {
                return;
            }
            buffer.readBytes(responsePacket);

            try {
                // trying to decode as pre-EIP-8
                AuthResponseMessage response = handshake.handleAuthResponse(myKey, initiatePacket, responsePacket);
                loggerNet.trace("From: \t{} \tRecv: \t{}", ctx.channel().remoteAddress(), response);

            } catch (Throwable t) {
                // it must be format defined by EIP-8 then
                responsePacket = readEIP8Packet(buffer, responsePacket);
                if (responsePacket == null) {
                    return;
                }

                AuthResponseMessageV4 response = handshake.handleAuthResponseV4(myKey, initiatePacket, responsePacket);
                loggerNet.trace("From: \t{} \tRecv: \t{}", ctx.channel().remoteAddress(), response);
            }

            EncryptionHandshake.Secrets secrets = this.handshake.getSecrets();
            this.frameCodec = new FrameCodec(secrets);

            loggerNet.trace("auth exchange done");
            channel.sendHelloMessage(ctx, frameCodec, Hex.toHexString(nodeId), null);
        } else {
            loggerWire.debug("MessageCodec: Buffer bytes: {}", buffer.readableBytes());
            List<Frame> frames = frameCodec.readFrames(buffer);
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
        }

        channel.getNodeStatistics().rlpxInHello.add();
    }

    private byte[] readEIP8Packet(ByteBuf buffer, byte[] plainPacket) {

        int size = bigEndianToShort(plainPacket);
        if (size < plainPacket.length) {
            throw new IllegalArgumentException("AuthResponse packet size is too low");
        }

        int bytesLeft = size - plainPacket.length + 2;
        byte[] restBytes = new byte[bytesLeft];

        if (!buffer.isReadable(restBytes.length)) {
            return null;
        }

        buffer.readBytes(restBytes);

        byte[] fullResponse = new byte[size + 2];
        System.arraycopy(plainPacket, 0, fullResponse, 0, plainPacket.length);
        System.arraycopy(restBytes, 0, fullResponse, plainPacket.length, restBytes.length);

        return fullResponse;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
}
