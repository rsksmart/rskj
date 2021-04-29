package co.rsk.rpc.modules.eth.subscribe;

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

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

public class PendingTransactionsNotificationEmitterTest {
    private PendingTransactionsNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;

    @Before
    public void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = mock(JsonRpcSerializer.class);
        emitter = new PendingTransactionsNotificationEmitter(ethereum, serializer);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    public void onPendingTransactionsReceivedTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized");

        List<Transaction> transactions = new ArrayList<>();
        listener.onPendingTransactionsReceived(transactions);

        verify(channel).writeAndFlush(new TextWebSocketFrame("serialized"));
    }
}