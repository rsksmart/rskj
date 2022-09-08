package org.ethereum.net.server;

import co.rsk.config.RskSystemProperties;
import co.rsk.net.eth.RskWireProtocol;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.util.IpUtils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.message.StaticMessages;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Objects;

import static org.mockito.Mockito.*;

public class EthereumChannelInitializerTest {

    @Test
    public void initChannel_AddressIsNotBanned_ShouldNotDisconnect() {
        InetSocketAddress address = Objects.requireNonNull(IpUtils.parseAddress("192.168.100.1:5555"));
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        doReturn(false).when(peerScoringManager).isAddressBanned(eq(address.getAddress()));
        ChannelManager channelManager = mock(ChannelManager.class);
        doReturn(true).when(channelManager).isAddressBlockAvailable(any());
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        NioSocketChannel channel = mock(NioSocketChannel.class);
        SocketChannelConfig config = mock(SocketChannelConfig.class);
        ChannelFuture channelFuture = mock(ChannelFuture.class);
        doReturn(address).when(channel).remoteAddress();
        doReturn(pipeline).when(channel).pipeline();
        doReturn(config).when(channel).config();
        doReturn(channelFuture).when(channel).closeFuture();

        EthereumChannelInitializer channelInitializer = new EthereumChannelInitializer("", mock(RskSystemProperties.class),
                channelManager, mock(CompositeEthereumListener.class), mock(ConfigCapabilities.class),
                mock(NodeManager.class), mock(RskWireProtocol.Factory.class), mock(Eth62MessageFactory.class),
                mock(StaticMessages.class), peerScoringManager);

        channelInitializer.initChannel(channel);

        verify(channel, never()).disconnect();
    }

    @Test
    public void initChannel_AddressIsBanned_ShouldDisconnect() {
        InetSocketAddress address = Objects.requireNonNull(IpUtils.parseAddress("192.168.100.1:5555"));
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        doReturn(true).when(peerScoringManager).isAddressBanned(eq(address.getAddress()));
        ChannelManager channelManager = mock(ChannelManager.class);
        doReturn(true).when(channelManager).isAddressBlockAvailable(any());
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        NioSocketChannel channel = mock(NioSocketChannel.class);
        SocketChannelConfig config = mock(SocketChannelConfig.class);
        ChannelFuture channelFuture = mock(ChannelFuture.class);
        doReturn(address).when(channel).remoteAddress();
        doReturn(pipeline).when(channel).pipeline();
        doReturn(config).when(channel).config();
        doReturn(channelFuture).when(channel).closeFuture();

        EthereumChannelInitializer channelInitializer = new EthereumChannelInitializer("", mock(RskSystemProperties.class),
                channelManager, mock(CompositeEthereumListener.class), mock(ConfigCapabilities.class),
                mock(NodeManager.class), mock(RskWireProtocol.Factory.class), mock(Eth62MessageFactory.class),
                mock(StaticMessages.class), peerScoringManager);

        channelInitializer.initChannel(channel);

        verify(channel, atLeastOnce()).disconnect();
    }
}
