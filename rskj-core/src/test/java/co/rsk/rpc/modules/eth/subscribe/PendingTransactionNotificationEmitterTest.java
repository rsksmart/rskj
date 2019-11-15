package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.crypto.Keccak256;
import co.rsk.rpc.JsonRpcSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.TestUtils;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PendingTransactionNotificationEmitterTest {

    private JsonRpcSerializer serializer;
    private PendingTransactionNotificationEmitter emitter;
    private EthereumListener listener;

    @Before
    public void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = mock(JsonRpcSerializer.class);
        emitter = new PendingTransactionNotificationEmitter(ethereum, serializer);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();

    }

    @Test
    public void onPendingTransactionsReceivedEventTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any())).thenReturn("serialized");

        listener.onPendingTransactionsReceived(Collections.singletonList(mock(Transaction.class)));
        verify(channel).write(new TextWebSocketFrame("serialized"));
        verify(channel).flush();
    }

    @Test
    public void onPendingTransactionsEventTriggersOneMessageToChannelPerTransaction() throws JsonProcessingException {
        SubscriptionId subscriptionId1 = mock(SubscriptionId.class);
        SubscriptionId subscriptionId2 = mock(SubscriptionId.class);
        Channel channel1 = mock(Channel.class);
        Channel channel2 = mock(Channel.class);
        emitter.subscribe(subscriptionId1, channel1);
        emitter.subscribe(subscriptionId2, channel2);

        when(serializer.serializeMessage(any()))
                .thenReturn("serializedTxHash1")
                .thenReturn("serializedTxHash2")
                .thenReturn("serializedTxHash1")
                .thenReturn("serializedTxHash2");

        listener.onPendingTransactionsReceived(testTxs(TestUtils.randomHash(), TestUtils.randomHash()));

        verify(channel1).write(new TextWebSocketFrame("serializedTxHash1"));
        verify(channel1).write(new TextWebSocketFrame("serializedTxHash2"));
        verify(channel1).flush();
        verify(channel2).write(new TextWebSocketFrame("serializedTxHash1"));
        verify(channel2).write(new TextWebSocketFrame("serializedTxHash2"));
        verify(channel2).flush();
    }

    @Test
    public void unsubscribeSucceedsForExistingSubscriptionId() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        assertThat(emitter.unsubscribe(new SubscriptionId()), is(false));
        assertThat(emitter.unsubscribe(subscriptionId), is(true));
    }

    @Test
    public void unsubscribeChannelThenNothingIsEmitted() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        emitter.unsubscribe(channel);

        listener.onPendingTransactionsReceived(testTxs(TestUtils.randomHash()));
        verifyNoMoreInteractions(channel);
    }

    private List<Transaction> testTxs(Keccak256... txHashes) {
        return Stream.of(txHashes).map(txHash -> {
            Transaction mockTx = mock(Transaction.class);
            doReturn(txHash).when(mockTx).getHash();
            return mockTx;
        }).collect(Collectors.toList());
    }
}