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

import static org.mockito.Mockito.*;

public class PendingTransactionsNotificationEmitterTest {
    private PendingTransactionsNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;

    @Before
    public void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = new JacksonBasedRpcSerializer();
        emitter = new PendingTransactionsNotificationEmitter(ethereum, serializer);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    public void onOnePendingTransactionsReceivedTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = new SubscriptionId("0xc3b33aa549fb9a60e95d21862596617c");
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        Transaction tx = mock(Transaction.class);
        Keccak256 txHash = new Keccak256("d6fdc5cc41a9959e922f30cb772a9aef46f4daea279307bc5f7024edc4ccd7fa");
        when(tx.getHash()).thenReturn(txHash);
        List<Transaction> transactions = Arrays.asList(tx);

        listener.onPendingTransactionsReceived(transactions);

        String result = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"subscription\":\"0xc3b33aa549fb9a60e95d21862596617c\",\"result\":\"0xd6fdc5cc41a9959e922f30cb772a9aef46f4daea279307bc5f7024edc4ccd7fa\"}}";
        verify(channel).writeAndFlush(new TextWebSocketFrame(result));
    }
}