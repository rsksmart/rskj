package org.ethereum.facade;

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EthereumImplTest {


    @Test
    void getGasPrice_returns_GasPriceTrackerValue_when_feeMarketWorking_is_true() {
        GasPriceTracker gasPriceTracker = mock(GasPriceTracker.class);

        when(gasPriceTracker.isFeeMarketWorking()).thenReturn(true);
        when(gasPriceTracker.getGasPrice()).thenReturn(Coin.valueOf(10));

        Ethereum ethereum = new EthereumImpl(null, null, new CompositeEthereumListener(), null, gasPriceTracker, null);
        Coin price = ethereum.getGasPrice();

        assertEquals(10, price.asBigInteger().intValue());
    }

    @Test
    void getGasPrice_returns_correctedBestBlockValue_when_feeMarketWorking_is_false(){
        GasPriceTracker gasPriceTracker = mock(GasPriceTracker.class);
        Blockchain blockchain = mock(Blockchain.class);
        Double minGasPriceMultiplier = 1.2;
        Block bestBlock = mock(Block.class);

        when(gasPriceTracker.isFeeMarketWorking()).thenReturn(false);
        when(bestBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(10));
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        Ethereum ethereum = new EthereumImpl(null, null, new CompositeEthereumListener(), blockchain, gasPriceTracker, minGasPriceMultiplier);
        Coin price = ethereum.getGasPrice();

        assertEquals(12, price.asBigInteger().intValue());
    }
}