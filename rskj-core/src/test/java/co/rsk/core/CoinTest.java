package co.rsk.core;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class CoinTest {
    @Test
    public void zeroGetBytes() {
        assertThat(Coin.ZERO.getBytes(), is(new byte[]{0}));
    }

}