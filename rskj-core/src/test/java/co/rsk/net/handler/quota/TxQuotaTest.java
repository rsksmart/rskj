package co.rsk.net.handler.quota;

import co.rsk.util.TimeProvider;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TxQuotaTest {

    private static final long MAX_GAS_PER_SECOND = Math.round(6_800_000 * 0.9);
    private static final long MAX_QUOTA = MAX_GAS_PER_SECOND * 2000;

    private TimeProvider timeProvider;

    @Before
    public void setUp() {
        timeProvider = mock(TimeProvider.class);
    }

    @Test
    public void acceptVirtualGasConsumption() {
        TxQuota txQuota = TxQuota.createNew("tx1", 10, System::currentTimeMillis);

        assertTrue(txQuota.acceptVirtualGasConsumption(9));
        assertFalse(txQuota.acceptVirtualGasConsumption(2));
        assertTrue(txQuota.acceptVirtualGasConsumption(1));
    }

    @Test
    public void refresh() {
        long currentTime = System.currentTimeMillis();
        when(timeProvider.currentTimeMillis()).thenReturn(currentTime);

        TxQuota txQuota = TxQuota.createNew("tx1", MAX_QUOTA, timeProvider);
        assertFalse("should reject tx over initial limit", txQuota.acceptVirtualGasConsumption(MAX_QUOTA + 1));
        assertTrue("should accept tx below initial limit", txQuota.acceptVirtualGasConsumption(MAX_QUOTA - 1));

        long timeElapsed = 1;
        double accumulatedGasApprox = timeElapsed / 1000d * MAX_GAS_PER_SECOND;
        when(timeProvider.currentTimeMillis()).thenReturn(currentTime += timeElapsed);
        txQuota.refresh(MAX_GAS_PER_SECOND, MAX_QUOTA);
        assertFalse("should reject tx over refreshed limit (not enough quiet time)", txQuota.acceptVirtualGasConsumption(accumulatedGasApprox + 1000));

        timeElapsed = 30;
        accumulatedGasApprox = timeElapsed / 1000d * MAX_GAS_PER_SECOND;
        when(timeProvider.currentTimeMillis()).thenReturn(currentTime += timeElapsed);
        txQuota.refresh(MAX_GAS_PER_SECOND, MAX_QUOTA);
        assertTrue("should accept tx when enough gas accumulated (enough quiet time)", txQuota.acceptVirtualGasConsumption(accumulatedGasApprox));
    }
}
