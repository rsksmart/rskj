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
import java.util.Arrays;
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

    @Test
    public void onPendingTransactionsReceivedTriggersMessageToChannelWithTxHash() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);

        String result = "{\n" +
                "        \"jsonrpc\":\"2.0\",\n" +
                "        \"method\":\"eth_subscription\",\n" +
                "        \"params\":{\n" +
                "            \"subscription\":\"0xc3b33aa549fb9a60e95d21862596617c\",\n" +
                "            \"result\":\"0xd6fdc5cc41a9959e922f30cb772a9aef46f4daea279307bc5f7024edc4ccd7fa\"\n" +
                "        }\n" +
                "   }";

        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        Transaction tx = mock(Transaction.class);
        when(serializer.serializeMessage(any())).thenReturn(result);
        List<Transaction> transactions = Arrays.asList(tx);

        listener.onPendingTransactionsReceived(transactions);

        verify(channel).writeAndFlush(new TextWebSocketFrame(result));
    }
}