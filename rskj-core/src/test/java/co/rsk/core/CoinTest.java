package co.rsk.core;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

public class CoinTest {

    private static final Coin ONE_COIN = Coin.valueOf(1L);
    private static final Coin TWO_COINS = Coin.valueOf(2L);

    @Test
    public void zeroGetBytes() {
        assertThat(Coin.ZERO.getBytes(), is(new byte[]{0}));
    }

    @Test
    public void maxOfTwoCoins() {
        Coin actualResult = Coin.max(TWO_COINS, ONE_COIN);
        assertEquals(TWO_COINS, actualResult);

        actualResult = Coin.max(ONE_COIN, TWO_COINS);
        assertEquals(TWO_COINS, actualResult);

        actualResult = Coin.max(ONE_COIN, ONE_COIN);
        assertEquals(ONE_COIN, actualResult);
    }
}
