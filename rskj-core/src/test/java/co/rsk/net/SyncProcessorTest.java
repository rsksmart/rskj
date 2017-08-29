package co.rsk.net;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessorTest {
    @Test
    public void noPeers() {
        SyncProcessor processor = new SyncProcessor();

        Assert.assertEquals(0, processor.getNoPeers());
    }
}
