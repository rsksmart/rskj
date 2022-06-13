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

package co.rsk.net.handler.quota;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.PendingState;
import co.rsk.db.RepositorySnapshot;
import co.rsk.util.MaxSizeHashMap;
import co.rsk.util.TimeProvider;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.listener.GasPriceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

/**
 * This class hosts a map with the available virtualGas for a set of addresses and validates or rejects a transaction accordingly.
 * The underlying map has auto-clean capabilities so, when filled, the item with an older access date is removed to make room for a new one.
 */
public class TxQuotaChecker {

    public static final int UNKNOWN_LAST_BLOCK_GAS_LIMIT = -1;

    private static final int MAX_QUOTAS_SIZE = 400_000;
    private static final int MAX_QUOTA_GAS_MULTIPLIER = 2000;
    private static final double MAX_GAS_PER_SECOND_PERCENT = 0.9;

    private static final Logger logger = LoggerFactory.getLogger(TxQuotaChecker.class);

    private long lastBlockGasLimit;

    private final MaxSizeHashMap<RskAddress, TxQuota> accountQuotas;

    private final TimeProvider timeProvider;

    public TxQuotaChecker(TimeProvider timeProvider) {
        this.accountQuotas = new MaxSizeHashMap<>(MAX_QUOTAS_SIZE, true);
        this.timeProvider = timeProvider;
        this.lastBlockGasLimit = UNKNOWN_LAST_BLOCK_GAS_LIMIT;
    }

    /**
     * Tries to accept a transaction under a certain context
     *
     * @param newTx          The tx to accept
     * @param replacedTx     The tx being replaced by <code>newTx</code>, if any
     * @param currentContext Some contextual information of the time <code>newTx</code> is being processed
     * @return true if the <code>newTx</code> was accepted, false otherwise
     */
    public synchronized boolean acceptTx(Transaction newTx, @Nullable Transaction replacedTx, CurrentContext currentContext) {
        // keep track of lastBlockGasLimit on each processed transaction, so we can use it from cleanMaxQuotas were we lack this context
        this.lastBlockGasLimit = currentContext.bestBlock.getGasLimitAsInteger().longValue();

        // doing this before creating quota
        boolean isFirstTxFromSender = isFirstTxFromSender(newTx, currentContext);

        TxQuota senderQuota = updateSenderQuota(newTx, currentContext);

        updateReceiverQuotaIfRequired(newTx, currentContext);

        double consumedVirtualGas = calculateConsumedVirtualGas(newTx, replacedTx, currentContext);

        // "isFirstTxFromSender" check is used to prevent the account first tx from taking hours to be propagated due to the minimum gas that is granted the first time account is added to node's quota map
        // let's imagine a scenario with a tx [tx1] from sender [s1], nodes [n1, n2] and different time moments [t1, t2, t3]:
        //      t1: n1 receives tx1 => s1 is a new account for n1 (not in the map) and gets granted min virtual gas that is not enough for tx1 => n1 rejects tx1, and it is not propagated
        //      t2: n1 receives again tx1 => as for n1, s1 accumulated enough gas for tx1 (already in the map since t1) => n1 accepts and propagates tx1
        //      t3: n2 receives tx1 => n2 behaves in the exact same way as n1 did in t1 => n2 rejects tx1, and it is not propagated
        //      ...
        //      tn: nn ...
        // by accepting the very first transaction regardless gas consumption we avoid this problem that won't occur with greater nonce
        // this won't be a problem since this first tx has a cost and min gas will be granted for next tx from same account
        if (isFirstTxFromSender) {
            logger.debug("Allowing account first tx [{}] regardless virtual gas consumption that was [{}]", newTx, consumedVirtualGas);
            return true;
        }

        return senderQuota.acceptVirtualGasConsumption(consumedVirtualGas, newTx, currentContext.bestBlock.getNumber());
    }

    private TxQuota updateSenderQuota(Transaction newTx, CurrentContext currentContext) {
        return updateQuota(newTx, true, currentContext);
    }

    private void updateReceiverQuotaIfRequired(Transaction newTx, CurrentContext currentContext) {
        // updating receiver quota for it to start accumulating virtual gas as soon as we now of its existence
        // with the following check we can face two scenarios if account is already in the map; we know that it is either:
        // 1) an EOA => we want to now its existence the sooner, the better, so it starts accumulating virtual gas
        // 2) a counterfactual contract (CF), not yet created (code not yet assigned) which received RBTCs =>
        //      in this case we don't care about quotas, a contract will never be a sender, but still we will be storing some contracts in the map
        //      we have decided to accept this caveat in our own benefit, since the map works as a cache that helps to avoid repository calls
        //      furthermore, quota map is cleaned up periodically and, later, when the CF exists on-chain, it won't be added ever again
        boolean receiverIsEOAorCF = accountQuotas.get(newTx.getReceiveAddress()) != null || isEOA(newTx.getReceiveAddress(), currentContext.repository);
        if (receiverIsEOAorCF || newAccountInRepository(newTx.getReceiveAddress(), currentContext.repository)) {
            updateQuota(newTx, false, currentContext);
        }
    }

    private boolean isFirstTxFromSender(Transaction newTx, CurrentContext currentContext) {
        RskAddress senderAddress = newTx.getSender();

        TxQuota quotaForSender = this.accountQuotas.get(senderAddress);
        long accountNonce = currentContext.state.getNonce(senderAddress).longValue();
        long txNonce = newTx.getNonceAsInteger().longValue();

        // need to check that account it's not in the map to ensure it is not a resend or a gasPrice bump
        return quotaForSender == null && accountNonce == 0 && txNonce == 0;
    }

    private boolean newAccountInRepository(RskAddress receiverAddress, RepositorySnapshot repository) {
        return !repository.isExist(receiverAddress);
    }

    private boolean isEOA(RskAddress receiverAddress, RepositorySnapshot repository) {
        return !repository.isContract(receiverAddress);
    }

    /**
     * Cleans from the underlying map those entries that have <code>maxQuota</code> after being refreshed
     * This method is intended to be called periodically with a rate similar to the time needed for an account to get <code>maxQuota</code>
     */
    public synchronized void cleanMaxQuotas() {
        if (lastBlockGasLimit == UNKNOWN_LAST_BLOCK_GAS_LIMIT) {
            // no transactions yet processed
            return;
        }

        long maxGasPerSecond = getMaxGasPerSecond(lastBlockGasLimit);
        long maxQuota = getMaxQuota(maxGasPerSecond);

        logger.debug("Clearing quota map, size before {}", this.accountQuotas.size());

        Iterator<Map.Entry<RskAddress, TxQuota>> quotaIterator = accountQuotas.entrySet().iterator();
        while (quotaIterator.hasNext()) {
            Map.Entry<RskAddress, TxQuota> quotaEntry = quotaIterator.next();
            RskAddress address = quotaEntry.getKey();
            TxQuota quota = quotaEntry.getValue();
            double accumulatedVirtualGas = quota.refresh(address, maxGasPerSecond, maxQuota);
            boolean maxQuotaGranted = BigDecimal.valueOf(maxQuota).compareTo(BigDecimal.valueOf(accumulatedVirtualGas)) == 0;
            if (maxQuotaGranted) {
                logger.trace("Clearing {}, it has maxQuota", quota);
                quotaIterator.remove();
            }
        }

        logger.debug("Clearing quota map, size after {}", this.accountQuotas.size());
    }

    public TxQuota getTxQuota(RskAddress address) {
        return this.accountQuotas.get(address);
    }

    private TxQuota updateQuota(Transaction newTx, boolean isTxSource, CurrentContext currentContext) {
        BigInteger blockGasLimit = currentContext.bestBlock.getGasLimitAsInteger();
        long maxGasPerSecond = getMaxGasPerSecond(blockGasLimit.longValue());
        long maxQuota = getMaxQuota(maxGasPerSecond);

        RskAddress address = isTxSource ? newTx.getSender() : newTx.getReceiveAddress();

        TxQuota quotaForAddress = this.accountQuotas.get(address);
        if (quotaForAddress == null) {
            long accountNonce = currentContext.state.getNonce(address).longValue();
            long initialQuota = calculateNewItemQuota(accountNonce, isTxSource, maxGasPerSecond, maxQuota);
            quotaForAddress = TxQuota.createNew(address, newTx.getHash(), initialQuota, timeProvider);
            this.accountQuotas.put(address, quotaForAddress);
        } else {
            quotaForAddress.refresh(address, maxGasPerSecond, maxQuota);
        }

        return quotaForAddress;
    }

    private long calculateNewItemQuota(long accountNonce, boolean isTxSource, long maxGasPerSecond, long maxQuota) {
        boolean isNewAccount = accountNonce == 0;
        boolean grantMaxQuota = isTxSource && !isNewAccount;
        return grantMaxQuota ? maxQuota : maxGasPerSecond;
    }

    private long getMaxGasPerSecond(long blockGasLimit) {
        return Math.round(blockGasLimit * MAX_GAS_PER_SECOND_PERCENT);
    }

    private long getMaxQuota(long maxGasPerSecond) {
        return maxGasPerSecond * TxQuotaChecker.MAX_QUOTA_GAS_MULTIPLIER;
    }

    private double calculateConsumedVirtualGas(Transaction newTx, @Nullable Transaction replacedTx, CurrentContext currentContext) {
        long accountNonce = currentContext.state.getNonce(newTx.getSender()).longValue();
        long blockGasLimit = currentContext.bestBlock.getGasLimitAsInteger().longValue();
        long blockMinGasPrice = currentContext.bestBlock.getMinimumGasPrice().asBigInteger().longValue();

        TxVirtualGasCalculator calculator;
        if (currentContext.gasPriceTracker.isFeeMarketWorking()) {
            long avgGasPrice = currentContext.gasPriceTracker.getGasPrice().asBigInteger().longValue();
            calculator = TxVirtualGasCalculator.createWithAllFactors(accountNonce, blockGasLimit, blockMinGasPrice, avgGasPrice);
        } else {
            calculator = TxVirtualGasCalculator.createSkippingGasPriceFactor(accountNonce, blockGasLimit, blockMinGasPrice);
        }

        return calculator.calculate(newTx, replacedTx);
    }

    /**
     * Helper class holding contextual information of the time the tx is being processed (bestBlock, state, repository, etc)
     */
    public static class CurrentContext {
        private final Block bestBlock;
        private final PendingState state;
        private final RepositorySnapshot repository;
        private final GasPriceTracker gasPriceTracker;

        public CurrentContext(Block bestBlock, PendingState state, RepositorySnapshot repository, GasPriceTracker gasPriceTracker) {
            this.bestBlock = bestBlock;
            this.state = state;
            this.repository = repository;
            this.gasPriceTracker = gasPriceTracker;
        }
    }

}
