package co.rsk.net;

import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.LightMessageHandler;
import co.rsk.net.light.LightPeer;
import co.rsk.net.light.message.GetAccountsMessage;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LightMessageHandlerTest {

    private LightMessageHandler lightMessageHandler;

    private LightPeer lightPeer1;
    private LightPeer lightPeer2;
    private ChannelHandlerContext ctx1;
    private ChannelHandlerContext ctx2;
    private LightClientHandler lightClientHandler1;
    private LightClientHandler lightClientHandler2;

    private GetAccountsMessage m1;
    private GetAccountsMessage m2;


    @Before
    public void setUp() {
        LightClientTestUtils lightClientTestUtils = new LightClientTestUtils();

        lightPeer1 = lightClientTestUtils.createPeer();
        lightPeer2 = lightClientTestUtils.createPeer();

        lightClientHandler1 = lightClientTestUtils.generateLightClientHandler(lightPeer1);
        lightClientHandler2 = lightClientTestUtils.generateLightClientHandler(lightPeer2);

        ctx1 = lightClientTestUtils.hookLightPeerToCtx(lightPeer1, lightClientHandler1);
        ctx2 = lightClientTestUtils.hookLightPeerToCtx(lightPeer2, lightClientHandler2);

        lightMessageHandler = new LightMessageHandler(lightClientTestUtils.getLightProcessor(),
                lightClientTestUtils.getLightSyncProcessor());

        m1 = new GetAccountsMessage(0, new byte[] {0x00}, new byte[] {0x00});
        m2 = new GetAccountsMessage(0, new byte[] {0x00}, new byte[] {0x00});
    }

    /**
     * We check that, given a random message,
     * the processing for that message is called
     */

    @Test
    public void lightMessageHandlerHandlesAMessageCorrectly() {
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        assertEquals(1,lightMessageHandler.getMessageQueueSize());
        lightMessageHandler.handleMessage();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerHandlesTwoMessagesCorrectly() {
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        lightMessageHandler.postMessage(lightPeer1, m2, ctx1, lightClientHandler1);
        assertEquals(2,lightMessageHandler.getMessageQueueSize());
        lightMessageHandler.handleMessage();
        lightMessageHandler.handleMessage();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerHandlesMessagesFromTwoPeersCorrectly() {
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        lightMessageHandler.postMessage(lightPeer2, m2, ctx2, lightClientHandler2);

        assertEquals(2,lightMessageHandler.getMessageQueueSize());

        lightMessageHandler.handleMessage();
        lightMessageHandler.handleMessage();

        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerServicesCorrectlyHandlesMessages() throws InterruptedException {
        lightMessageHandler.start();
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        lightMessageHandler.postMessage(lightPeer1, m2, ctx1, lightClientHandler1);

        Thread.sleep(500);

        lightMessageHandler.stop();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

}
