package co.rsk.net.handler.quota;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.PendingState;
import co.rsk.db.RepositorySnapshot;
import co.rsk.util.HexUtils;
import co.rsk.util.MaxSizeHashMap;
import co.rsk.util.TimeProvider;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.Web3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Optional;

public class TxQuotaChecker {

    private static final int MAX_QUOTAS_SIZE = 10000;
    private static final int MAX_QUOTA_GAS_MULTIPLIER = 2000;
    private static final double MAX_GAS_PER_SECOND_PERCENT = 0.9;

    private static final Logger logger = LoggerFactory.getLogger(TxQuotaChecker.class);

    private static long lastBlockGasLimit;

    private final MaxSizeHashMap<RskAddress, TxQuota> accountQuotas;

    private final TimeProvider timeProvider;

    public TxQuotaChecker(TimeProvider timeProvider) {
        this.accountQuotas = new MaxSizeHashMap<>(MAX_QUOTAS_SIZE, true);
        this.timeProvider = timeProvider;
    }

    public synchronized boolean acceptTx(Transaction newTx, Optional<Transaction> replacedTx, CurrentContext currentContext) {
        updateLastBlockGasLimit(currentContext.bestBlock.getGasLimitAsInteger());

        TxQuota senderQuota = updateQuota(newTx, true, currentContext);

        boolean existsDestination = currentContext.repository.getAccountState(newTx.getReceiveAddress()) != null;
        boolean isEOA = !currentContext.repository.isContract(newTx.getReceiveAddress());
        if (!existsDestination || isEOA) {
            updateQuota(newTx, false, currentContext);
        }

        double consumedVirtualGas = calculateConsumedVirtualGas(newTx, replacedTx, currentContext);

        boolean wasAccepted = senderQuota.acceptVirtualGasConsumption(consumedVirtualGas);

        logger.trace("tx [{}] was [{}] after quota check", newTx.getHash(), wasAccepted ? "accepted" : "rejected");

        return wasAccepted;
    }

    public synchronized void cleanMaxQuotas() {
        long maxGasPerSecond = getMaxGasPerSecond(lastBlockGasLimit);
        long maxQuota = getMaxGasPerSecond(maxGasPerSecond);

        Iterator<TxQuota> quotaIterator = accountQuotas.values().iterator();
        while (quotaIterator.hasNext()) {
            TxQuota quota = quotaIterator.next();
            double accumulatedVirtualGas = quota.refresh(maxGasPerSecond, maxQuota);
            boolean maxQuotaGranted = BigDecimal.valueOf(maxQuota).compareTo(BigDecimal.valueOf(accumulatedVirtualGas)) == 0;
            if (maxQuotaGranted) {
                quotaIterator.remove();
            }
        }
    }

    @VisibleForTesting
    TxQuota getTxQuota(RskAddress address) {
        return this.accountQuotas.get(address);
    }

    private TxQuota updateQuota(Transaction newTx, boolean isTxSource, CurrentContext currentContext) {
        BigInteger blockGasLimit = currentContext.bestBlock.getGasLimitAsInteger();
        long maxGasPerSecond = getMaxGasPerSecond(blockGasLimit);
        long maxQuota = getMaxGasPerSecond(maxGasPerSecond);

        RskAddress address = isTxSource ? newTx.getSender() : newTx.getReceiveAddress();

        TxQuota quotaForAddress = this.accountQuotas.get(address);
        if (quotaForAddress == null) {
            long accountNonce = currentContext.state.getNonce(address).longValue();
            long initialQuota = calculateNewItemQuota(accountNonce, isTxSource, maxGasPerSecond, maxQuota);
            quotaForAddress = TxQuota.createNew(newTx.getHash().toHexString(), initialQuota, timeProvider);
            this.accountQuotas.put(address, quotaForAddress);
        } else {
            quotaForAddress.refresh(maxGasPerSecond, maxQuota);
        }

        return quotaForAddress;
    }

    private static void updateLastBlockGasLimit(BigInteger blockGasLimit) {
        lastBlockGasLimit = blockGasLimit.longValue();
    }

    private long calculateNewItemQuota(long accountNonce, boolean isTxSource, long maxGasPerSecond, long maxQuota) {
        boolean isNewAccount = accountNonce == 0;
        boolean grantMaxQuota = isTxSource && !isNewAccount;
        return grantMaxQuota ? maxQuota : maxGasPerSecond;
    }

    private long getMaxGasPerSecond(BigInteger blockGasLimit) {
        return Math.round(blockGasLimit.longValue() * MAX_GAS_PER_SECOND_PERCENT);
    }

    private long getMaxGasPerSecond(long maxGasPerSecond) {
        return maxGasPerSecond * TxQuotaChecker.MAX_QUOTA_GAS_MULTIPLIER;
    }

    private double calculateConsumedVirtualGas(Transaction newTx, Optional<Transaction> replacedTx, CurrentContext currentContext) {
        long accountNonce = currentContext.state.getNonce(newTx.getSender()).longValue();
        long blockGasLimit = currentContext.bestBlock.getGasLimitAsInteger().longValue();
        long blockMinGasPrice = currentContext.bestBlock.getMinimumGasPrice().asBigInteger().longValue();
        long avgGasPrice = HexUtils.stringHexToBigInteger(currentContext.web3.eth_gasPrice()).longValue();

        TxVirtualGasCalculator calculator = new TxVirtualGasCalculator(accountNonce, blockGasLimit, blockMinGasPrice, avgGasPrice);
        return calculator.calculate(newTx, replacedTx);
    }

    public static class CurrentContext {
        private final Block bestBlock;
        private final PendingState state;
        private final RepositorySnapshot repository;
        private final Web3 web3;

        public CurrentContext(Block bestBlock, PendingState state, RepositorySnapshot repository, Web3 web3) {
            this.bestBlock = bestBlock;
            this.state = state;
            this.repository = repository;
            this.web3 = web3;
        }
    }

}
