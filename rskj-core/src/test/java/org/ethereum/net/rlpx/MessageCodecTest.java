package org.ethereum.net.rlpx;

import co.rsk.net.light.message.StatusMessage;
import co.rsk.net.rlpx.LCMessageFactory;
import io.netty.channel.ChannelHandlerContext;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.BlockFactory;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.NodeStatistics;
import org.ethereum.net.client.Capability;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.p2p.P2pMessage;
import org.ethereum.net.p2p.P2pMessageFactory;
import org.ethereum.net.server.Channel;
import org.junit.Before;
import org.junit.Test;

import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageCodecTest {

    private EthereumListener ethereumListener;
    private SystemProperties config;
    private ChannelHandlerContext ctx;
    private SocketAddress socketAddress;
    private io.netty.channel.Channel nettyChannel;
    private Channel peer;
    private NodeStatistics nodeStatistics;

    @Before
    public void setUp() {
        ethereumListener = mock(EthereumListener.class);
        config = mock(SystemProperties.class);
        ctx = mock(ChannelHandlerContext.class);
        socketAddress = mock(SocketAddress.class);
        nettyChannel = mock(io.netty.channel.Channel.class);
        peer = mock(Channel.class);
        nodeStatistics = new NodeStatistics();

        when(ctx.channel()).thenReturn(nettyChannel);
        when(nettyChannel.remoteAddress()).thenReturn(socketAddress);
        when(config.rlpxMaxFrameSize()).thenReturn(MessageCodec.NO_FRAMING);
        when(peer.getNodeStatistics()).thenReturn(nodeStatistics);
    }

    @Test
    public void encodeDecodeP2PFrameAndShouldReturnP2PMessage() {

        P2pMessage p2pTestMessage = StaticMessages.PING_MESSAGE;
        List<Capability> cap = new LinkedList<>();
        cap.add(new Capability(Capability.P2P, P2pHandler.VERSION));
        MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
        messageCodec.initMessageCodes(cap);
        messageCodec.setChannel(peer);
        messageCodec.setP2pMessageFactory(new P2pMessageFactory());

        List<Object> outFrame = new LinkedList<>();
        List<Object> outMsg = new LinkedList<>();

        try {
            messageCodec.encode(ctx, p2pTestMessage, outFrame);
            assertEquals(1, outFrame.size());

            FrameCodec.Frame p2pFrame = (FrameCodec.Frame) outFrame.get(0);

            messageCodec.decode(ctx, p2pFrame, outMsg);
            assertEquals(1, outMsg.size());

            P2pMessage p2pMessage =  (P2pMessage) outMsg.get(0);
            assertEquals(p2pTestMessage, p2pMessage);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void encodeDecodeETHFrameAndShouldReturnETHMessage() {

        byte[] payload = Hex.decode("f84927808425c60144a0832056d3c93ff2739ace7199952e5365aa29f18805be05634c4db125c5340216a0955f36d073ccb026b78ab3424c15cf966a7563aa270413859f78702b9e8e22cb");
        EthMessage ethTestMessage = new org.ethereum.net.eth.message.StatusMessage(payload);
        BlockFactory blockFactory = mock(BlockFactory.class);
        List<Capability> cap = new LinkedList<>();
        cap.add(new Capability(Capability.RSK, EthVersion.UPPER));
        MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
        messageCodec.initMessageCodes(cap);
        messageCodec.setChannel(peer);
        messageCodec.setEthMessageFactory(new Eth62MessageFactory(blockFactory));
        messageCodec.setEthVersion(EthVersion.V62);

        List<Object> outFrame = new LinkedList<>();
        List<Object> outMsg = new LinkedList<>();

        try {
            messageCodec.encode(ctx, ethTestMessage, outFrame);
            assertEquals(1, outFrame.size());

            FrameCodec.Frame ethFrame = (FrameCodec.Frame) outFrame.get(0);
            messageCodec.decode(ctx, ethFrame, outMsg);
            assertEquals(1, outMsg.size());

            EthMessage ethMessage =  (EthMessage) outMsg.get(0);
            assertArrayEquals(ethTestMessage.getEncoded(), ethMessage.getEncoded());


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void encodeDecodeLCFrameAndShouldReturnLCMessage() {

        StatusMessage lcStatusMessage = new StatusMessage();
        List<Capability> cap = new LinkedList<>();
        cap.add(new Capability(Capability.LC, (byte) 0));
        MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
        messageCodec.initMessageCodes(cap);
        messageCodec.setChannel(peer);
        messageCodec.setLCMessageFactory(new LCMessageFactory(mock(BlockFactory.class)));

        List<Object> outFrame = new LinkedList<>();
        List<Object> outMsg = new LinkedList<>();

        try {
            messageCodec.encode(ctx, lcStatusMessage, outFrame);
            assertEquals(1, outFrame.size());

            FrameCodec.Frame ethFrame = (FrameCodec.Frame) outFrame.get(0);
            messageCodec.decode(ctx, ethFrame, outMsg);
            assertEquals(1, outMsg.size());

            StatusMessage lcMessage =  (StatusMessage) outMsg.get(0);
            assertArrayEquals(lcStatusMessage.getEncoded(), lcMessage.getEncoded());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
