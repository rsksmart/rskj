package co.rsk.net;

import co.rsk.net.messages.StatusMessage;
import co.rsk.net.simples.SimpleMessageSender;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessorTest {
    @Test
    public void noPeers() {
        SyncProcessor processor = new SyncProcessor();

        Assert.assertEquals(0, processor.getNoPeers());
    }

    @Test
    public void processStatus() {
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, BigInteger.TEN);

        SyncProcessor processor = new SyncProcessor();
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
    }
}
