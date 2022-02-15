package co.rsk.net.handler.quota;

import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

class TxVirtualGasCalculator {

    private static final double GAS_LIMIT_WEIGHT = 4;
    private static final double NONCE_WEIGHT = 4;
    private static final double LOW_GAS_PRICE_WEIGH = 3;
    private static final double SIZE_FACTOR_DIVISOR = 25000d;

    private static final Logger logger = LoggerFactory.getLogger(TxVirtualGasCalculator.class);

    private final long accountNonce;

    private final long blockGasLimit;

    private final long blockMinGasPrice;

    private final long avgGasPrice;

    TxVirtualGasCalculator(long accountNonce, long blockGasLimit, long blockMinGasPrice, long avgGasPrice) {
        this.accountNonce = accountNonce;
        this.blockGasLimit = blockGasLimit;
        this.blockMinGasPrice = blockMinGasPrice;
        this.avgGasPrice = avgGasPrice;
    }

    public double calculate(Transaction newTx, Optional<Transaction> replacedTx) {
        long txGasLimit = newTx.getGasLimitAsInteger().longValue();

        long newTxNonce = newTx.getNonceAsInteger().longValue();
        long futureNonceFactor = newTxNonce == accountNonce ? 1 : 2;

        double lowGasPriceFactor = calculateLowGasPriceFactor(newTx);

        double nonceFactor = 1 + NONCE_WEIGHT / (accountNonce + 1);

        double sizeFactor = 1 + newTx.getSize() / SIZE_FACTOR_DIVISOR;

        double replacementFactor = calculateReplacementFactor(newTx, replacedTx);

        double gasLimitFactor = calculateGasLimitFactor(txGasLimit);

        double compositeFactor = futureNonceFactor * lowGasPriceFactor * nonceFactor * sizeFactor * replacementFactor * gasLimitFactor;
        logger.debug("virtualGasConsumed calculation: txGasLimit {}, compositeFactor {} (futureNonceFactor {}, lowGasPriceFactor {}, nonceFactor {}, sizeFactor {}, replacementFactor {}, gasLimitFactor {})", txGasLimit, compositeFactor, futureNonceFactor, lowGasPriceFactor, nonceFactor, sizeFactor, replacementFactor, gasLimitFactor);
        return txGasLimit * compositeFactor;
    }

    private double calculateLowGasPriceFactor(Transaction newTx) {
        long txGasPrice = newTx.getGasPrice().asBigInteger().longValue();

        if (txGasPrice < this.avgGasPrice) {
            double factor = (this.avgGasPrice - txGasPrice) / (this.avgGasPrice - (double) this.blockMinGasPrice);
            return 1 + LOW_GAS_PRICE_WEIGH * factor;
        } else {
            return 1;
        }
    }

    private double calculateReplacementFactor(Transaction newTx, Optional<Transaction> replacedTx) {
        double newTxGasPrice = newTx.getGasPrice().asBigInteger().doubleValue();
        double replacementRatio = replacedTx
                .map(Transaction::getGasPrice)
                .map(rtxGasPrice -> rtxGasPrice.asBigInteger().doubleValue())
                .map(rtxGasPrice -> newTxGasPrice / rtxGasPrice)
                .orElse(0.0);
        return replacementRatio > 0 ? (1 + 1 / replacementRatio) : 1;
    }

    private double calculateGasLimitFactor(long txGasLimit) {
        return 1 + GAS_LIMIT_WEIGHT * txGasLimit / this.blockGasLimit;
    }

}
