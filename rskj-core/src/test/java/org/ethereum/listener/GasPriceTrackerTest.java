package org.ethereum.listener;

import co.rsk.core.Coin;
import co.rsk.peg.simples.SimpleRskTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GasPriceTrackerTest {

    private final GasPriceTracker gasPriceTracker = new GasPriceTracker();

    @Test
    public void getGasPrice_TrackerNotTriggered_ReturnsDefaultPrice() {
        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(20_000_000_000L), actualResult);
    }

    @Test
    public void getGasPrice_PriceWindowNotFilled_ReturnsBlockPrice() {
        Block block = makeBlock(Coin.valueOf(30_000_000_000L), 511, i -> makeTx(Coin.valueOf(40_000_000_000L)));

        gasPriceTracker.onBlock(block, Collections.emptyList());

        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(30_000_000_000L), actualResult);
    }

    @Test
    public void getGasPrice_PriceWindowFilledAndNoBestBlock_ReturnsCalculatedPrice() {
        Block block = makeBlock(Coin.valueOf(30_000_000_000L), 512, i -> makeTx(Coin.valueOf(i * 1_000_000_000L)));

        gasPriceTracker.onBlock(block, Collections.emptyList());

        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(128_000_000_000L), actualResult);
    }

    @Test
    public void getGasPrice_PriceWindowFilledAndBestBlockWithLowerPrice_ReturnsCalculatedPrice() {
        Block bestBlock = makeBlock(Coin.valueOf(1_000_000_000L), 0, i -> null);
        Block block = makeBlock(Coin.valueOf(1_000_000_000L), 512, i -> makeTx(Coin.valueOf(i * 1_000_000_000L)));

        gasPriceTracker.onBestBlock(bestBlock, Collections.emptyList());
        gasPriceTracker.onBlock(block, Collections.emptyList());
        Coin actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(128_000_000_000L), actualResult); // calculated value has been returned

        block = makeBlock(Coin.valueOf(1_000_000_000L), 513, i -> makeTx(Coin.valueOf(2_000_000_000L)));
        gasPriceTracker.onBlock(block, Collections.emptyList());
        actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(2_000_000_000L), actualResult); // re-calculated value has been returned

        block = makeBlock(Coin.valueOf(1_000_000_000L), 511, i -> makeTx(Coin.valueOf(10_000_000_000L)));
        gasPriceTracker.onBlock(block, Collections.emptyList());
        actualResult = gasPriceTracker.getGasPrice();
        assertEquals(Coin.valueOf(2_000_000_000L), actualResult); // cached value has been returned
    }

    @Test
    public void getGasPrice_PriceWindowFilledAndBestBlockWithGreaterPrice_ReturnsBestBlockAdjustedPrice() {
        Block bestBlock = makeBlock(Coin.valueOf(50_000_000_000L), 0, i -> null);
        Block block = makeBlock(Coin.valueOf(30_000_000_000L), 512, i -> makeTx(Coin.valueOf(40_000_000_000L)));

        gasPriceTracker.onBestBlock(bestBlock, Collections.emptyList());
        gasPriceTracker.onBlock(block, Collections.emptyList());

        Coin actualResult = gasPriceTracker.getGasPrice();

        assertEquals(Coin.valueOf(55_000_000_000L), actualResult);
    }

    private static Block makeBlock(Coin mgp, int txCount, Function<Integer, Transaction> txMaker) {
        Block block = mock(Block.class);

        when(block.getMinimumGasPrice()).thenReturn(mgp);

        List<Transaction> txs = IntStream.range(0, txCount).mapToObj(txMaker::apply).collect(Collectors.toList());
        when(block.getTransactionsList()).thenReturn(txs);

        return block;
    }

    private static Transaction makeTx(Coin gasPrice) {
        return new SimpleRskTransaction(null) {
            @Override
            public Coin getGasPrice() {
                return gasPrice;
            }
        };
    }
}
