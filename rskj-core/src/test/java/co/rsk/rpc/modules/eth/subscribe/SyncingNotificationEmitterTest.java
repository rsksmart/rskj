package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.crypto.Keccak256;
import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.JsonRpcSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class SyncingNotificationEmitterTest {
    private SyncingNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;

    @Before
    public void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = new JacksonBasedRpcSerializer();
        emitter = new SyncingNotificationEmitter(ethereum, serializer);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    public void onSyncingEventTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = new SubscriptionId("0xc3b33aa549fb9a60e95d21862596617c");
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        Transaction tx = mock(Transaction.class);
        Keccak256 txHash = new Keccak256("d6fdc5cc41a9959e922f30cb772a9aef46f4daea279307bc5f7024edc4ccd7fa");
        when(tx.getHash()).thenReturn(txHash);
        List<Transaction> transactions = Arrays.asList(tx);

        listener.onSyncing(true);

        String result = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"subscription\":\"0xc3b33aa549fb9a60e95d21862596617c\",\"result\":true}}";
        verify(channel).writeAndFlush(new TextWebSocketFrame(result));
    }

    @Test
    public void onNonSyncingEventMessageIsNotTrigger() throws JsonProcessingException {
        SubscriptionId subscriptionId = new SubscriptionId("0xc3b33aa549fb9a60e95d21862596617c");
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        verify(channel, never()).writeAndFlush(any());
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

        Transaction tx = mock(Transaction.class);
        Keccak256 txHash = new Keccak256("d6fdc5cc41a9959e922f30cb772a9aef46f4daea279307bc5f7024edc4ccd7fa");
        when(tx.getHash()).thenReturn(txHash);
        List<Transaction> transactions = Arrays.asList(tx);
        listener.onSyncing(true);

        verifyNoMoreInteractions(channel);
    }
}