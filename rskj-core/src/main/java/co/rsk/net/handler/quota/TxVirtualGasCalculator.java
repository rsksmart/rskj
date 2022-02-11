package co.rsk.net.handler.quota;

import co.rsk.core.Coin;
import co.rsk.core.bc.PendingState;
import org.ethereum.core.Block;
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

    private final PendingState state;

    private final Block bestBlock;

    TxVirtualGasCalculator(PendingState state, Block bestBlock) {
        this.state = state;
        this.bestBlock = bestBlock;
    }

    public double calculate(Transaction newTx, Optional<Transaction> replacedTx) {
        long accountNonce = state.getNonce(newTx.getSender()).longValue();
        long txGasLimit = newTx.getGasLimitAsInteger().longValue();

        long newTxNonce = newTx.getNonceAsInteger().longValue();
        long futureNonceFactor = newTxNonce == accountNonce ? 1 : 2;

        double lowGasPriceFactor = calculateLowGasPriceFactor(newTx.getGasPrice(), bestBlock);

        double nonceFactor = 1 + NONCE_WEIGHT / (accountNonce + 1);

        double sizeFactor = 1 + newTx.getSize() / SIZE_FACTOR_DIVISOR;

        double replacementFactor = calculateReplacementFactor(newTx, replacedTx);

        double gasLimitFactor = calculateGasLimitFactor(bestBlock, txGasLimit);

        double compositeFactor = futureNonceFactor * lowGasPriceFactor * nonceFactor * sizeFactor * replacementFactor * gasLimitFactor;
        logger.debug("virtualGasConsumed calculation: txGasLimit {}, compositeFactor {} (futureNonceFactor {}, lowGasPriceFactor {}, nonceFactor {}, sizeFactor {}, replacementFactor {}, gasLimitFactor {})", txGasLimit, compositeFactor, futureNonceFactor, lowGasPriceFactor, nonceFactor, sizeFactor, replacementFactor, gasLimitFactor);
        return txGasLimit * compositeFactor;
    }

    private double calculateLowGasPriceFactor(Coin gasPrice, Block block) {
        long txGasPrice = gasPrice.asBigInteger().longValue();
        long averageGasPrice = block.getAverageGasPrice().asBigInteger().longValue();

        if (txGasPrice < averageGasPrice) {
            double minimumGasPrice = block.getMinimumGasPrice().asBigInteger().doubleValue();
            double factor = (averageGasPrice - txGasPrice) / (averageGasPrice - minimumGasPrice);
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

    private double calculateGasLimitFactor(Block block, long txGasLimit) {
        long blockGasLimit = block.getGasLimitAsInteger().longValue();
        return 1 + GAS_LIMIT_WEIGHT * txGasLimit / blockGasLimit;
    }

}
