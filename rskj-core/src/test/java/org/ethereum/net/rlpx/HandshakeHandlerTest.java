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
package org.ethereum.net.rlpx;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.scoring.PeerScoringManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.NodeStatistics;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilitiesImpl;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.server.Channel;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.ethereum.net.client.Capability.RSK;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class HandshakeHandlerTest {

    private HandshakeHandler handler;
    private ECKey hhKey;
    private Channel channel;
    private EmbeddedChannel ch;
    private ChannelHandlerContext ctx;

    @BeforeEach
    public void setup() {
        RskSystemProperties config = new TestSystemProperties();
        hhKey = config.getMyKey();
        handler = new HandshakeHandler(
                config,
                mock(PeerScoringManager.class),
                mock(P2pHandler.class),
                mock(MessageCodec.class),
                // this needs to be the real object so we can test changing the HELLO message
                new ConfigCapabilitiesImpl(config)
        );
        channel = mock(Channel.class);
        when(channel.getNodeStatistics()).thenReturn(new NodeStatistics());

        // We don't pass the handler to the constructor to avoid calling HandshakeHandler.channelActive
        ch = new EmbeddedChannel();
        ch.pipeline().addLast(handler);
        ctx = ch.pipeline().firstContext();
    }

    @Test
    public void shouldActivateEthIfHandshakeIsSuccessful() throws Exception {
        simulateHandshakeStartedByPeer(Collections.singletonList(new Capability(RSK, (byte) 62)));
        verify(channel, times(1)).activateEth(ctx, EthVersion.V62);
        assertTrue(ch.isOpen());
    }

    @Test
    public void shouldDisconnectIfNoCapabilityIsPresent() throws Exception {
        simulateHandshakeStartedByPeer(Collections.emptyList());
        // this will only happen when an exception is raised
        assertFalse(ch.isOpen());
    }

    @Test
    public void shouldDisconnectIfRskCapabilityIsMissing() throws Exception {
        simulateHandshakeStartedByPeer(Collections.singletonList(new Capability("eth", (byte) 62)));
        // this will only happen when an exception is raised
        assertFalse(ch.isOpen());
    }

    // This is sort of an integration test. It interacts with the handshake handler and multiple other objects to
    // simulate a handshake initiated by a remote peer.
    // In the future, the handshake classes should be rewritten to allow unit testing.
    private void simulateHandshakeStartedByPeer(List<Capability> capabilities) throws Exception {
        ECKey remoteKey = new ECKey();
        handler.setRemoteId("", channel);
        handler.internalChannelActive(ctx);

        EncryptionHandshake handshake = new EncryptionHandshake(hhKey.getPubKeyPoint());
        AuthInitiateMessageV4 initiateMessage = handshake.createAuthInitiateV4(remoteKey);
        byte[] initiatePacket = handshake.encryptAuthInitiateV4(initiateMessage);
        ch.writeInbound(Unpooled.copiedBuffer(initiatePacket));

        ByteBuf responsePacketByteBuf = (ByteBuf) ch.readOutbound();
        byte[] responsePacket = new byte[responsePacketByteBuf.readableBytes()];
        responsePacketByteBuf.readBytes(responsePacket);
        handshake.handleAuthResponseV4(remoteKey, initiatePacket, responsePacket);

        HelloMessage helloMessage = new HelloMessage(P2pHandler.VERSION, "", capabilities, 4321, ByteUtil.toHexString(HashUtil.randomPeerId()));
        byte[] payload = helloMessage.getEncoded();
        FrameCodec frameCodec = new FrameCodec(handshake.getSecrets());
        ByteBuf byteBufMsg = ch.alloc().buffer();
        frameCodec.writeFrame(new FrameCodec.Frame(helloMessage.getCode(), payload), byteBufMsg);
        ch.writeInbound(byteBufMsg);
    }
}
