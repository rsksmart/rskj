package co.rsk.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CacheElementTest {

    @Test
    public void hasExpired_OK() {
        CacheElement<String> cacheElement1 = new CacheElement<>("value.1", 1L);
        CacheElement<String> cacheElement2 = new CacheElement<>("value.2", 10000L);

        await().atMost(500L, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(cacheElement1.hasExpired()));
        assertFalse(cacheElement2.hasExpired());
    }

}
