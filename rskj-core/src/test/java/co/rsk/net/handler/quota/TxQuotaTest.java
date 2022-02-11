package co.rsk.net.handler.quota;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TxQuotaTest {

    @Test
    public void acceptVirtualGasConsumption() {
        TxQuota txQuota = TxQuota.createNew(10);

        assertTrue(txQuota.acceptVirtualGasConsumption(9));
        assertFalse(txQuota.acceptVirtualGasConsumption(2));
        assertTrue(txQuota.acceptVirtualGasConsumption(1));
    }

    @Test
    public void refresh() throws InterruptedException {
        TxQuota txQuota = TxQuota.createNew(10);
        assertFalse("should reject tx over initial limit", txQuota.acceptVirtualGasConsumption(10.1));
        assertTrue("should accept tx below initial limit", txQuota.acceptVirtualGasConsumption(9.9));

        Thread.sleep(1);
        txQuota.refresh(10, 10);
        assertFalse("should reject tx over refreshed limit (not enough quiet time)", txQuota.acceptVirtualGasConsumption(10.1));

        Thread.sleep(30);
        txQuota.refresh(10, 10);
        assertTrue("should accept tx when enough gas accumulated (enough quiet time)", txQuota.acceptVirtualGasConsumption(10.1));
    }
}
