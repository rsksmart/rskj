package co.rsk.net.discovery;

import co.rsk.RskContext;
import co.rsk.config.TestSystemProperties;
import co.rsk.net.eth.RskMessage;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.p2p.P2pMessageFactory;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Tag;

public class MessageCodecFuzzTest {

    @Tag("MessageCodecFuzzMessageCodec")
    @FuzzTest
    public void messageCodecFuzz(FuzzedDataProvider data) {

        SystemProperties systemProperties = new TestSystemProperties();
        BlockFactory blockFactory = new BlockFactory(systemProperties.getActivationConfig());
        Eth62MessageFactory e62 = new Eth62MessageFactory(blockFactory);
        P2pMessageFactory p2p = new P2pMessageFactory();

        long start = System.currentTimeMillis();
        //TODO this actually tries to decode now. The only current problem is that not all messages are handled here
        // that can probably be hardcoded for test
        try {
            Message msg = e62.create((byte)data.consumeInt(0, 8), data.consumeBytes(4096));
            Method method = msg.getClass().getMethod("parse");
            method.invoke(msg, null);
        } catch (Exception e) {
        }

        try {
            Message msg = p2p.create((byte)data.consumeInt(0, 8), data.consumeBytes(4096));
            Method method = msg.getClass().getMethod("parse");
            method.invoke(msg, null);
        } catch (Exception e) {
        }
        long end = System.currentTimeMillis();
        if (end - start > 500) {
            System.out.println("[COIN] We should pay attention to the time here");
        }
    }
}
