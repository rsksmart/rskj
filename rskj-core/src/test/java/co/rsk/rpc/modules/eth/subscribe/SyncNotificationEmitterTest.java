package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.rpc.JsonRpcSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Blockchain;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SyncNotificationEmitterTest {
    private SyncNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;
    private NodeBlockProcessor nodeBlockProcessor;
    private Blockchain blockchain;

    @Before
    public void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = mock(JsonRpcSerializer.class);
        nodeBlockProcessor = mock(NodeBlockProcessor.class);
        blockchain = mock(Blockchain.class);

        emitter = new SyncNotificationEmitter(ethereum, serializer, nodeBlockProcessor, blockchain);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    public void onSyncStartedTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1");

        listener.onLongSyncStarted();

        verify(channel).writeAndFlush(new TextWebSocketFrame("serialized1"));
    }

    @Test
    public void onSyncStartedTriggersMessageToChannelMultipleSubscribers() throws JsonProcessingException {
        SubscriptionId subscriptionId1 = mock(SubscriptionId.class);
        Channel channel1 = mock(Channel.class);
        SubscriptionId subscriptionId2 = mock(SubscriptionId.class);
        Channel channel2 = mock(Channel.class);
        emitter.subscribe(subscriptionId1, channel1);
        emitter.subscribe(subscriptionId2, channel2);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1")
                .thenReturn("serialized2");

        listener.onLongSyncStarted();

        verify(channel2).writeAndFlush(new TextWebSocketFrame("serialized1"));
        verify(channel1).writeAndFlush(new TextWebSocketFrame("serialized2"));
    }

    @Test
    public void onSyncEndedTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1");

        listener.onLongSyncDone();

        verify(channel).writeAndFlush(new TextWebSocketFrame("serialized1"));
    }
}
