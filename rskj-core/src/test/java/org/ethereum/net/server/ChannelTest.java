package org.ethereum.net.server;

import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.eth.RskWireProtocol;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.rlpx.MessageCodec;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class ChannelTest {

    private MessageQueue messageQueue;
    private MessageCodec messageCodec;
    private NodeManager nodeManager;
    private RskWireProtocol.Factory rskWireProtocolFactory;
    private Eth62MessageFactory eth62MessageFactory;
    private LightClientHandler.Factory lightClientHandlerFactory;
    private LCMessageFactory lcMessageFactory;
    private StaticMessages staticMessages;
    private String remoteId;
    private Channel target;

    @Before
    public void setup() {
        remoteId = "remoteId";
        messageQueue = mock(MessageQueue.class);
        messageCodec = mock(MessageCodec.class);
        nodeManager = mock(NodeManager.class);
        rskWireProtocolFactory = mock(RskWireProtocol.Factory.class);
        eth62MessageFactory = mock(Eth62MessageFactory.class);
        staticMessages = mock(StaticMessages.class);
        lightClientHandlerFactory = mock(LightClientHandler.Factory.class);
        lcMessageFactory = mock(LCMessageFactory.class);
        target = new Channel(
                messageQueue,
                messageCodec,
                nodeManager,
                rskWireProtocolFactory,
                lightClientHandlerFactory,
                eth62MessageFactory,
                lcMessageFactory,
                staticMessages, remoteId);
    }


    @Test
    public void equals_true() {
        InetSocketAddress inetSocketAddress = mock(InetSocketAddress.class);
        Channel otherChannel = new Channel(
                messageQueue,
                messageCodec,
                nodeManager,
                rskWireProtocolFactory,
                lightClientHandlerFactory,
                eth62MessageFactory,
                lcMessageFactory,
                staticMessages, remoteId);

        target.setInetSocketAddress(inetSocketAddress);
        otherChannel.setInetSocketAddress(inetSocketAddress);

        assertEquals(target, otherChannel);
    }

    @Test
    public void equals_false() {
        InetSocketAddress inetSocketAddress = mock(InetSocketAddress.class);
        Channel otherChannel = new Channel(
                messageQueue,
                messageCodec,
                nodeManager,
                rskWireProtocolFactory,
                lightClientHandlerFactory,
                eth62MessageFactory,
                lcMessageFactory,
                staticMessages, remoteId);

        target.setInetSocketAddress(inetSocketAddress);

        assertNotEquals(target, otherChannel);
    }

    @Test
    public void equals_getInetAddress() {
        InetAddress inetAddress = InetAddress.getLoopbackAddress();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 500);

        target.setInetSocketAddress(inetSocketAddress);
        
        assertEquals(inetAddress, target.getAddress());
    }
}