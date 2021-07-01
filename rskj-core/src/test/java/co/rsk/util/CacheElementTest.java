package co.rsk.util;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CacheElementTest {

    @Test
    public void hasExpired_OK() {
        CacheElement<String> cacheElement1 = new CacheElement<>("value.1", 1L);
        CacheElement<String> cacheElement2 = new CacheElement<>("value.2", 10000L);

        await().atMost(200L, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(cacheElement1.hasExpired()));
        assertFalse(cacheElement2.hasExpired());
    }

}
