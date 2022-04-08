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
import co.rsk.util.HexUtils;
import co.rsk.util.MaxSizeHashMap;
import co.rsk.util.TimeProvider;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.Web3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * This class hosts a map with the available virtualGas for a set of addresses and validates or rejects a transaction accordingly.
 * The underlying map has auto-clean capabilities so, when filled, the item with an older access date is removed to make room for a new one.
 */
public class TxQuotaChecker {

    private static final int MAX_QUOTAS_SIZE = 400_000;
    private static final int MAX_QUOTA_GAS_MULTIPLIER = 2000;
    private static final double MAX_GAS_PER_SECOND_PERCENT = 0.9;

    private static final Logger logger = LoggerFactory.getLogger(TxQuotaChecker.class);

    private static long lastBlockGasLimit = -1;

    private final MaxSizeHashMap<RskAddress, TxQuota> accountQuotas;

    private final TimeProvider timeProvider;

    public TxQuotaChecker(TimeProvider timeProvider) {
        this.accountQuotas = new MaxSizeHashMap<>(MAX_QUOTAS_SIZE, true);
        this.timeProvider = timeProvider;
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
        updateLastBlockGasLimit(currentContext.bestBlock.getGasLimitAsInteger());

        TxQuota senderQuota = updateQuota(newTx, true, currentContext);

        boolean existsDestination = currentContext.repository.getAccountState(newTx.getReceiveAddress()) != null;
        boolean isEOA = !currentContext.repository.isContract(newTx.getReceiveAddress());
        if (!existsDestination || isEOA) {
            updateQuota(newTx, false, currentContext);
        }

        double consumedVirtualGas = calculateConsumedVirtualGas(newTx, replacedTx, currentContext);

        return senderQuota.acceptVirtualGasConsumption(consumedVirtualGas, newTx);
    }

    /**
     * Cleans from the underlying map those entries that have <code>maxQuota</code> after being refreshed
     * This method is intended to be called periodically with a rate similar to the time needed for an account to get <code>maxQuota</code>
     */
    public synchronized void cleanMaxQuotas() {
        if (lastBlockGasLimit == -1) {
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

    /**
     * We need this static since, when {@link #cleanMaxQuotas() cleanMaxQuotas} is called, we have no context
     * to get <code>blockGasLimit</code> value from, so we should keep track of it during tx processing
     */
    private static void updateLastBlockGasLimit(BigInteger blockGasLimit) {
        lastBlockGasLimit = blockGasLimit.longValue();
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
        long avgGasPrice = HexUtils.stringHexToBigInteger(currentContext.web3.eth_gasPrice()).longValue();

        TxVirtualGasCalculator calculator = new TxVirtualGasCalculator(accountNonce, blockGasLimit, blockMinGasPrice, avgGasPrice);
        return calculator.calculate(newTx, replacedTx);
    }

    /**
     * Helper class holding contextual information of the time the tx is being processed (bestBlock, state, repository, etc)
     */
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
