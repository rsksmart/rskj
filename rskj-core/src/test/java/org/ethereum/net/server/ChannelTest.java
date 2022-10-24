package org.ethereum.net.server;

import co.rsk.net.eth.RskWireProtocol;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.rlpx.MessageCodec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

class ChannelTest {

    private MessageQueue messageQueue;
    private MessageCodec messageCodec;
    private NodeManager nodeManager;
    private RskWireProtocol.Factory rskWireProtocolFactory;
    private Eth62MessageFactory eth62MessageFactory;
    private StaticMessages staticMessages;
    private String remoteId;
    private Channel target;

    @BeforeEach
    void setup() {
        remoteId = "remoteId";
        messageQueue = mock(MessageQueue.class);
        messageCodec = mock(MessageCodec.class);
        nodeManager = mock(NodeManager.class);
        rskWireProtocolFactory = mock(RskWireProtocol.Factory.class);
        eth62MessageFactory = mock(Eth62MessageFactory.class);
        staticMessages = mock(StaticMessages.class);
        target = new Channel(
                messageQueue,
                messageCodec,
                nodeManager,
                rskWireProtocolFactory,
                eth62MessageFactory,
                staticMessages,
                remoteId);
    }


    @Test
    void equals_true() {
        InetSocketAddress inetSocketAddress = mock(InetSocketAddress.class);
        Channel otherChannel = new Channel(
                messageQueue,
                messageCodec,
                nodeManager,
                rskWireProtocolFactory,
                eth62MessageFactory,
                staticMessages,
                remoteId);

        target.setInetSocketAddress(inetSocketAddress);
        otherChannel.setInetSocketAddress(inetSocketAddress);

        Assertions.assertEquals(target, otherChannel);
    }

    @Test
    void equals_false() {
        InetSocketAddress inetSocketAddress = mock(InetSocketAddress.class);
        Channel otherChannel = new Channel(
                messageQueue,
                messageCodec,
                nodeManager,
                rskWireProtocolFactory,
                eth62MessageFactory,
                staticMessages,
                remoteId);

        target.setInetSocketAddress(inetSocketAddress);

        assertNotEquals(target, otherChannel);
    }

    @Test
    void equals_getInetAddress() {
        InetAddress inetAddress = InetAddress.getLoopbackAddress();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 500);

        target.setInetSocketAddress(inetSocketAddress);

        Assertions.assertEquals(inetAddress, target.getAddress());
    }
}
