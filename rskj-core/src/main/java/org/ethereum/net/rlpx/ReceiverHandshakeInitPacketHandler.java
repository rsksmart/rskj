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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.config.SystemProperties;
import org.ethereum.crypto.ECIESCoder;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

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
public class ReceiverHandshakeInitPacketHandler extends ByteToMessageDecoder {
    private static final Logger loggerWire = LoggerFactory.getLogger("wire");
    private static final Logger loggerNet = LoggerFactory.getLogger("net");

    private final PeerScoringManager peerScoringManager;
    private final ReceiverHandshakeFrameCodecHandler.Factory nextHandlerFactory;

    private final Channel channel;
    private final ECKey myKey;

    public ReceiverHandshakeInitPacketHandler(
            SystemProperties config,
            PeerScoringManager peerScoringManager,
            P2pHandler p2pHandler,
            MessageCodec messageCodec,
            ConfigCapabilities configCapabilities,
            Channel channel) {
        this.peerScoringManager = peerScoringManager;
        this.channel = channel;
        this.myKey = config.getMyKey();
        this.nextHandlerFactory = (c, frameCodec) -> new ReceiverHandshakeFrameCodecHandler(
                config, peerScoringManager, p2pHandler, messageCodec, configCapabilities, c, frameCodec);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channel.setInetSocketAddress((InetSocketAddress) ctx.channel().remoteAddress());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        loggerWire.debug("Decoding handshake... ({} bytes available)", in.readableBytes());
        byte[] authInitPacket = new byte[AuthInitiateMessage.getLength() + ECIESCoder.getOverhead()];
        if (!in.isReadable(authInitPacket.length)) {
            return;
        }
        in.readBytes(authInitPacket);

        EncryptionHandshake handshake = new EncryptionHandshake();

        byte[] responsePacket;
        try {
            // trying to decode as pre-EIP-8
            AuthInitiateMessage initiateMessage = handshake.decryptAuthInitiate(authInitPacket, myKey);
            loggerNet.trace("From: \t{} \tRecv: \t{}", ctx.channel().remoteAddress(), initiateMessage);

            AuthResponseMessage response = handshake.makeAuthInitiate(initiateMessage, myKey);
            loggerNet.trace("To: \t{} \tSend: \t{}", ctx.channel().remoteAddress(), response);
            responsePacket = handshake.encryptAuthResponse(response);
        } catch (Throwable t) {
            // it must be format defined by EIP-8 then
            try {
                authInitPacket = readEIP8Packet(in, authInitPacket);

                if (authInitPacket == null) {
                    return;
                }

                AuthInitiateMessageV4 initiateMessage = handshake.decryptAuthInitiateV4(authInitPacket, myKey);
                loggerNet.trace("From: \t{} \tRecv: \t{}", ctx.channel().remoteAddress(), initiateMessage);

                AuthResponseMessageV4 response = handshake.makeAuthInitiateV4(initiateMessage, myKey);
                loggerNet.trace("To: \t{} \tSend: \t{}", ctx.channel().remoteAddress(), response);
                responsePacket = handshake.encryptAuthResponseV4(response);

            } catch (InvalidCipherTextException ce) {
                loggerNet.warn(
                        "Can't decrypt AuthInitiateMessage from {}. Most likely the remote peer used wrong public key (NodeID) to encrypt message.",
                        ctx.channel().remoteAddress()
                );
                return;
            }
        }

        handshake.agreeSecret(authInitPacket, responsePacket);

        EncryptionHandshake.Secrets secrets = handshake.getSecrets();
        FrameCodec frameCodec = new FrameCodec(secrets);

        ECPoint remotePubKey = handshake.getRemotePublicKey();

        byte[] compressed = remotePubKey.getEncoded(false);

        byte[] remoteId = new byte[compressed.length - 1];
        System.arraycopy(compressed, 1, remoteId, 0, remoteId.length);
        channel.setNode(remoteId);
        channel.getNodeStatistics().rlpxInHello.add();

        // TODO(mc) consider ChannelPipeline#replace
        ctx.pipeline().addLast(nextHandlerFactory.newInstance(channel, frameCodec));
        ctx.pipeline().remove(this);

        ByteBuf byteBufMsg = ctx.alloc().buffer(responsePacket.length);
        byteBufMsg.writeBytes(responsePacket);
        ctx.writeAndFlush(byteBufMsg).sync();
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


    // TODO(mc): consolidate peer scoring events


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

    private void recordEvent(ChannelHandlerContext ctx, EventType event) {
        SocketAddress socketAddress = ctx.channel().remoteAddress();

        //TODO(mmarquez): what if it is not ??
        if (socketAddress instanceof InetSocketAddress) {
            NodeID nodeID = channel.getNodeId();

            InetAddress address = ((InetSocketAddress)socketAddress).getAddress();

            peerScoringManager.recordEvent(nodeID, address, event);
        }
    }
}
