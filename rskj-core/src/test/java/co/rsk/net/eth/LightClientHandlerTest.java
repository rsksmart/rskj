package co.rsk.net.eth;

import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.message.BlockReceiptsMessage;
import co.rsk.net.light.message.GetBlockReceiptsMessage;
import co.rsk.net.light.message.TestMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentCaptor.*;
import static org.mockito.Mockito.*;

public class LightClientHandlerTest {
    private MessageQueue messageQueue;
    private LightClientHandler lightClientHandler;
    private Channel channel;
    private ChannelHandlerContext ctx;
    private LightProcessor lightProcessor;
    private Blockchain blockchain;
    private BlockStore blockStore;
    private RepositoryLocator repositoryLocator;

    @Before
    public void setup() {
        messageQueue = spy(MessageQueue.class);
        channel = mock(Channel.class);
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        repositoryLocator = mock(RepositoryLocator.class);
        lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        LightClientHandler.Factory factory = msgQueue -> new LightClientHandler(msgQueue, lightProcessor);
        lightClientHandler = factory.newInstance(messageQueue);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        ctx = ch.pipeline().firstContext();
    }

    @Test
    public void lightClientHandlerSendsMessageToQueue() throws Exception {
        TestMessage m = new TestMessage();
        lightClientHandler.channelRead0(ctx, m);
        verify(messageQueue, times(1)).sendMessage(any());
    }

    @Test
    public void lightClientHandlerSendsGetBlockReceiptsToQueue() throws Exception {
        Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        Block block = mock(Block.class);
        List<TransactionReceipt> receipts = new LinkedList<>();
        GetBlockReceiptsMessage m = new GetBlockReceiptsMessage(0, blockHash.getBytes());
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        BlockReceiptsMessage response = new BlockReceiptsMessage(0, receipts);

        lightClientHandler.channelRead0(ctx, m);

        ArgumentCaptor<BlockReceiptsMessage> argument = forClass(BlockReceiptsMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

}
