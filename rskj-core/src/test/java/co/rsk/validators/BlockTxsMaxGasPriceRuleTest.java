/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.validators;

import co.rsk.core.Coin;
import com.google.common.collect.ImmutableList;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BlockTxsMaxGasPriceRuleTest {

    private static final long BEST_BLOCK_NUMBER = 100L;

    private static final Coin MIN_GAS_PRICE = Coin.valueOf(2L);

    private static final long GAS_PRICE_CAP = MIN_GAS_PRICE.asBigInteger().multiply(TxGasPriceCap.FOR_BLOCK.timesMinGasPrice).longValue();

    @Mock
    private ActivationConfig activationConfig;

    @Mock
    private Block block;

    private BlockTxsMaxGasPriceRule validator;

    @Before
    public void setUp() {
        when(block.getNumber()).thenReturn(BEST_BLOCK_NUMBER);

        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        lenient().when(block.getMinimumGasPrice()).thenReturn(MIN_GAS_PRICE);

        validator = new BlockTxsMaxGasPriceRule(activationConfig);
    }

    @Test
    public void isValid_whenRskip252DisabledThenBlockValidRegardlessGasPrice() {
        when(activationConfig.isActive(ConsensusRule.RSKIP252, BEST_BLOCK_NUMBER)).thenReturn(false);

        Transaction txLessGasPriceThanCap = Mockito.mock(Transaction.class);
        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        lenient().when(txLessGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP - 1));

        Transaction txMoreGasPriceThanCap = Mockito.mock(Transaction.class);
        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        lenient().when(txMoreGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP + 1_000_000_000_000L));

        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        lenient().when(block.getTransactionsList()).thenReturn(ImmutableList.of(txLessGasPriceThanCap, txMoreGasPriceThanCap));

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void isValid_whenRskip252EnabledThenBlockInvalidDueToTxWithMoreGasPriceThanCap() {
        when(activationConfig.isActive(ConsensusRule.RSKIP252, BEST_BLOCK_NUMBER)).thenReturn(true);

        Transaction txLessGasPriceThanCap = Mockito.mock(Transaction.class);
        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        when(txLessGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP - 1));
        when(block.getTransactionsList()).thenReturn(ImmutableList.of(txLessGasPriceThanCap));

        Assert.assertTrue(validator.isValid(block)); // valid up until here, no tx surpassing cap

        Transaction txMoreGasPriceThanCap = Mockito.mock(Transaction.class);
        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        when(txMoreGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP + 1_000_000_000_000L));
        when(block.getTransactionsList()).thenReturn(ImmutableList.of(txLessGasPriceThanCap, txMoreGasPriceThanCap));

        Assert.assertFalse(validator.isValid(block)); // now invalid up due to txMoreGasPriceThanCap
    }
}
