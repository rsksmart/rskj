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

package co.rsk.net.handler.txvalidator;

import co.rsk.core.Coin;
import co.rsk.validators.TxGasPriceCap;
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
public class TxValidatorMaximumGasPriceValidatorTest {

    private static final long BEST_BLOCK_NUMBER = 100L;

    private static final Coin MIN_GAS_PRICE = Coin.valueOf(2L);

    private static final long GAS_PRICE_CAP = MIN_GAS_PRICE.asBigInteger().multiply(TxGasPriceCap.FOR_TRANSACTION.timesMinGasPrice).longValue();

    @Mock
    private ActivationConfig activationConfig;

    @Mock
    private Block block;

    private TxValidatorMaximumGasPriceValidator validator;

    @Before
    public void setUp() {
        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        lenient().when(block.getMinimumGasPrice()).thenReturn(MIN_GAS_PRICE);

        validator = new TxValidatorMaximumGasPriceValidator(activationConfig);
    }

    @Test
    public void validate_whenRskip252DisabledThenTxValidRegardlessGasPrice() {
        when(activationConfig.isActive(ConsensusRule.RSKIP252, BEST_BLOCK_NUMBER)).thenReturn(false);

        Transaction txLessGasPriceThanCap = Mockito.mock(Transaction.class);
        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        lenient().when(txLessGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP - 1));
        Assert.assertTrue(validator.validate(txLessGasPriceThanCap, null, null, MIN_GAS_PRICE, BEST_BLOCK_NUMBER, false).transactionIsValid());

        Transaction txMoreGasPriceThanCap = Mockito.mock(Transaction.class);
        // lenient to avoid "unnecessary Mockito stubbing", if ever called
        lenient().when(txMoreGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP + 1_000_000_000_000L));
        Assert.assertTrue(validator.validate(txMoreGasPriceThanCap, null, null, MIN_GAS_PRICE, BEST_BLOCK_NUMBER, false).transactionIsValid());
    }

    @Test
    public void validate_whenRskip252EnabledThenTxWithMoreGasPriceThanCapInvalid() {
        when(activationConfig.isActive(ConsensusRule.RSKIP252, BEST_BLOCK_NUMBER)).thenReturn(true);

        Transaction txLessGasPriceThanCap = Mockito.mock(Transaction.class);
        when(txLessGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP - 1));
        Assert.assertTrue(validator.validate(txLessGasPriceThanCap, null, null, MIN_GAS_PRICE, BEST_BLOCK_NUMBER, false).transactionIsValid());

        Transaction txMoreGasPriceThanCap = Mockito.mock(Transaction.class);
        when(txMoreGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(GAS_PRICE_CAP + 1));
        Assert.assertFalse(validator.validate(txMoreGasPriceThanCap, null, null, MIN_GAS_PRICE, BEST_BLOCK_NUMBER, false).transactionIsValid());
    }

}
