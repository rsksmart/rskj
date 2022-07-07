package co.rsk.net.handler.txvalidator;

import co.rsk.core.Coin;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TxValidatorMaximumGasPriceValidatorTest {

    @Mock
    private ActivationConfig activationConfig;

    @Test
    public void whenRskip252DisabledThenTxValidRegardlessGasPrice() {
        long bestBlockNumber = 100L;

        long minGasPriceRef = 2L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);
        long capGasPrice = minGasPriceRef * TxValidatorMaximumGasPriceValidator.GAS_PRICE_CAP_MULTIPLIER.longValue();

        when(activationConfig.isActive(ConsensusRule.RSKIP252, bestBlockNumber)).thenReturn(false);

        Transaction tx1 = Mockito.mock(Transaction.class);
        // lenient to avoid "unnecessary Mockito stubbing" error while ensuring 'tx.getGasPrice()' is greater than cap if ever called
        lenient().when(tx1.getGasPrice()).thenReturn(Coin.valueOf(capGasPrice + 1_000_000));

        TxValidatorMaximumGasPriceValidator validator = new TxValidatorMaximumGasPriceValidator(activationConfig);

        Assert.assertTrue(validator.validate(tx1, null, null, minGasPrice, bestBlockNumber, false).transactionIsValid());
    }

    @Test
    public void whenRskip252EnabledAndGaspriceCapNotSurpassedThenTxValid() {
        long bestBlockNumber = 100L;

        long minGasPriceRef = 2L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);
        long capGasPrice = minGasPriceRef * TxValidatorMaximumGasPriceValidator.GAS_PRICE_CAP_MULTIPLIER.longValue();

        when(activationConfig.isActive(ConsensusRule.RSKIP252, bestBlockNumber)).thenReturn(true);

        Transaction txLessGasPriceThanCap = Mockito.mock(Transaction.class);
        when(txLessGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(capGasPrice - 1));

        Transaction txSameGasPriceAsCap = Mockito.mock(Transaction.class);
        when(txSameGasPriceAsCap.getGasPrice()).thenReturn(Coin.valueOf(capGasPrice));

        TxValidatorMaximumGasPriceValidator validator = new TxValidatorMaximumGasPriceValidator(activationConfig);

        Assert.assertTrue(validator.validate(txLessGasPriceThanCap, null, null, minGasPrice, bestBlockNumber, false).transactionIsValid());
        Assert.assertTrue(validator.validate(txSameGasPriceAsCap, null, null, minGasPrice, bestBlockNumber, false).transactionIsValid());
    }

    @Test
    public void whenRskip252EnabledAndGaspriceCapSurpassedThenTxInvalid() {
        long bestBlockNumber = 100L;

        long minGasPriceRef = 2L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);
        long capGasPrice = minGasPriceRef * TxValidatorMaximumGasPriceValidator.GAS_PRICE_CAP_MULTIPLIER.longValue();

        when(activationConfig.isActive(ConsensusRule.RSKIP252, bestBlockNumber)).thenReturn(true);

        Transaction txSameGasPriceThanCap = Mockito.mock(Transaction.class);
        when(txSameGasPriceThanCap.getGasPrice()).thenReturn(Coin.valueOf(capGasPrice));

        Transaction txMoreGasPriceAsCap = Mockito.mock(Transaction.class);
        when(txMoreGasPriceAsCap.getGasPrice()).thenReturn(Coin.valueOf(capGasPrice + 1));

        TxValidatorMaximumGasPriceValidator validator = new TxValidatorMaximumGasPriceValidator(activationConfig);

        Assert.assertTrue(validator.validate(txSameGasPriceThanCap, null, null, minGasPrice, bestBlockNumber, false).transactionIsValid());
        Assert.assertFalse(validator.validate(txMoreGasPriceAsCap, null, null, minGasPrice, bestBlockNumber, false).transactionIsValid());
    }

}
