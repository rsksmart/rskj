package co.rsk.core;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CoinTest {
    @Test
    public void zeroGetBytes() {
        assertThat(Coin.ZERO.getBytes(), is(new byte[]{0}));
    }

}