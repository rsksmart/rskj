package co.rsk.net.eth;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.MessageHandler;
import co.rsk.net.Status;
import co.rsk.net.StatusResolver;
import co.rsk.net.messages.BlockMessage;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.net.messages.Message;
import co.rsk.scoring.PeerScoringManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeStatistics;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.*;

class RskWireProtocolTest {

    private RskSystemProperties config;
    private RskWireProtocol target;
    private PeerScoringManager peerScoringManager;
    private MessageHandler messageHandler;
    private CompositeEthereumListener compositeEthereumListener;
    private Genesis genesis;
    private MessageRecorder messageRecorder;
    private StatusResolver statusResolver;
    private MessageQueue messageQueue;
    private Channel channel;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setup() {
        config = mock(RskSystemProperties.class);
        peerScoringManager = mock(PeerScoringManager.class);
        messageHandler = mock(MessageHandler.class);
        compositeEthereumListener = mock(CompositeEthereumListener.class);
        genesis = mock(Genesis.class);
        messageRecorder = mock(MessageRecorder.class);
        statusResolver = mock(StatusResolver.class);
        messageQueue = mock(MessageQueue.class);
        channel = mock(Channel.class);
        target = new RskWireProtocol(
                config,
                peerScoringManager,
                messageHandler,
                compositeEthereumListener,
                genesis,
                messageRecorder,
                statusResolver,
                messageQueue,
                channel);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(target);
        ctx = ch.pipeline().firstContext();
    }


    @Test
    void channelRead0_old_status_incompatible_protocol() throws Exception {

        NodeStatistics.StatHandler statHandler = mock(NodeStatistics.StatHandler.class);
        NodeStatistics nodeStatistics = mock(NodeStatistics.class);

        when(channel.getNodeStatistics()).thenReturn(nodeStatistics);
        when(nodeStatistics.getEthInbound()).thenReturn(statHandler);


        EthMessage message = new StatusMessage((byte) 0x0,
                0x0,
                ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10)),
                new byte[32],
                new byte[32]);
        target.channelRead0(ctx, message);

        verify(messageQueue).disconnect(ReasonCode.INCOMPATIBLE_PROTOCOL);
    }

    @Test
    void channelRead0_old_status_invalid_network() throws Exception {

        NodeStatistics.StatHandler statHandler = mock(NodeStatistics.StatHandler.class);
        NodeStatistics nodeStatistics = mock(NodeStatistics.class);

        when(channel.getNodeStatistics()).thenReturn(nodeStatistics);
        when(nodeStatistics.getEthInbound()).thenReturn(statHandler);

        EthMessage message = new StatusMessage(EthVersion.V62.getCode(),
                0x1,
                ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10)),
                new byte[32],
                new byte[32]);
        target.channelRead0(ctx, message);

        verify(messageQueue).disconnect(ReasonCode.NULL_IDENTITY);
    }

    @Test
    void channelRead0_old_status_unexpected_genesis() throws Exception {

        NodeStatistics.StatHandler statHandler = mock(NodeStatistics.StatHandler.class);
        NodeStatistics nodeStatistics = mock(NodeStatistics.class);

        when(channel.getNodeStatistics()).thenReturn(nodeStatistics);
        when(nodeStatistics.getEthInbound()).thenReturn(statHandler);

        EthMessage message = new StatusMessage(EthVersion.V62.getCode(),
                0x0,
                ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10)),
                new byte[32],
                new byte[32]);
        target.channelRead0(ctx, message);

        verify(messageQueue).disconnect(ReasonCode.UNEXPECTED_GENESIS);
    }

    @Test
    void channelRead0_get_block_message() throws Exception {

        NodeStatistics.StatHandler statHandler = mock(NodeStatistics.StatHandler.class);
        NodeStatistics nodeStatistics = mock(NodeStatistics.class);

        when(channel.getNodeStatistics()).thenReturn(nodeStatistics);
        when(nodeStatistics.getEthInbound()).thenReturn(statHandler);

        Message message = new GetBlockMessage(new byte[32]);
        EthMessage rskMessage = new RskMessage(message);

        target.channelRead0(ctx, rskMessage);

        verify(messageHandler).postMessage(channel, message);
    }

    @Test
    void channelRead0_block_message() throws Exception {

        NodeStatistics.StatHandler statHandler = mock(NodeStatistics.StatHandler.class);
        NodeStatistics nodeStatistics = mock(NodeStatistics.class);

        when(channel.getNodeStatistics()).thenReturn(nodeStatistics);
        when(nodeStatistics.getEthInbound()).thenReturn(statHandler);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(new Keccak256(new byte[32]));
        Message message = new BlockMessage(block);
        EthMessage rskMessage = new RskMessage(message);

        target.channelRead0(ctx, rskMessage);

        verify(messageHandler).postMessage(channel, message);
    }

    @Test
    void channelRead0_status_message() throws Exception {

        NodeStatistics.StatHandler statHandler = mock(NodeStatistics.StatHandler.class);
        NodeStatistics nodeStatistics = mock(NodeStatistics.class);

        when(channel.getNodeStatistics()).thenReturn(nodeStatistics);
        when(nodeStatistics.getEthInbound()).thenReturn(statHandler);

        Status status = mock(Status.class);
        when(status.getBestBlockHash()).thenReturn(new byte[32]);
        Message message = new co.rsk.net.messages.StatusMessage(status);
        EthMessage rskMessage = new RskMessage(message);

        target.channelRead0(ctx, rskMessage);

        verify(messageHandler).postMessage(channel, message);
    }

    @Test
    void activate_sendStatus() {
        BlockDifficulty blockDifficulty = new BlockDifficulty(BigInteger.valueOf(20));
        Status status = mock(Status.class);
        when(status.getTotalDifficulty()).thenReturn(blockDifficulty);
        when(statusResolver.currentStatus()).thenReturn(status);
        when(genesis.getHash()).thenReturn(new Keccak256(new byte[32]));
        NodeStatistics nodeStatistics = mock(NodeStatistics.class);
        when(nodeStatistics.getEthOutbound()).thenReturn(mock(NodeStatistics.StatHandler.class));
        when(channel.getNodeStatistics()).thenReturn(nodeStatistics);

        target.activate();

        verify(messageQueue, times(2)).sendMessage(any());
    }
}
