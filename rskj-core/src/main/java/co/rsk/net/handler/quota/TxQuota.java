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
import co.rsk.crypto.Keccak256;
import co.rsk.util.TimeProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the Quota information for a given address
 */
public class TxQuota {

    private static final Logger logger = LoggerFactory.getLogger(TxQuota.class);

    private final TimeProvider timeProvider;

    @JsonProperty
    private long timestamp;

    @JsonProperty
    private double availableVirtualGas;

    private TxQuota(String addressHex, String txHash, long availableVirtualGas, TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.timestamp = this.timeProvider.currentTimeMillis();
        this.availableVirtualGas = availableVirtualGas;

        logger.debug("Quota created for account [{}] after tx [{}] with value [{}]", addressHex, txHash, this.availableVirtualGas);
    }

    /**
     * Creates a new instance of TxQuota
     *
     * @param address      Address sourcing the tx, for logging purposes
     * @param txHash       Hash of the tx after which the quota is being created, for logging purposes
     * @param initialQuota The initialQuota to provide
     * @param timeProvider The time provider to calculate inactivity periods, etc.
     * @return a new instance of TxQuota
     */
    public static TxQuota createNew(RskAddress address, Keccak256 txHash, long initialQuota, TimeProvider timeProvider) {
        return new TxQuota(address.toHexString(), txHash.toHexString(), initialQuota, timeProvider);
    }

    /**
     * Checks whether the accumulated virtual gas is greater than the received gas to consume. If enough, received gas is discounted from accumulated.
     *
     * @param virtualGasToConsume Gas to be consumed
     * @param tx                  Tx being executed, used for logging purposes only
     * @param blockNumber         Block number for logging purposes
     * @return True if there was enough accumulated gas
     */
    public synchronized boolean acceptVirtualGasConsumption(double virtualGasToConsume, Transaction tx, long blockNumber) {
        return acceptVirtualGasConsumption(virtualGasToConsume, tx, blockNumber, false);
    }

    private synchronized boolean acceptVirtualGasConsumption(double virtualGasToConsume, Transaction tx, long blockNumber, boolean forcingAcceptance) {
        // we know getSender() was called previously in the flow, so sender field was already computed and is available
        // we are not adding extra cost here, and it is useful for debugging purposes of transactions that don't get to a block, but we want to analyse
        RskAddress sender = tx.getSender();

        if (this.availableVirtualGas < virtualGasToConsume) {
            String acceptanceNote = forcingAcceptance ? "Forcing tx acceptance" : "NOT enough virtualGas";
            if (logger.isWarnEnabled()) {
                logger.warn("{} for blockNumber [{}], sender [{}] and tx [{}]: availableVirtualGas=[{}], virtualGasToConsume=[{}]", acceptanceNote, blockNumber, sender, tx.getHash(), this.availableVirtualGas, virtualGasToConsume);
            }
            return false;
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Enough virtualGas for blockNumber [{}], sender [{}] and tx [{}]: availableVirtualGas=[{}], virtualGasToConsume=[{}]", blockNumber, sender, tx.getHash(), this.availableVirtualGas, virtualGasToConsume);
        }
        this.availableVirtualGas -= virtualGasToConsume;
        return true;
    }

    /**
     * Checks whether the accumulated virtual gas is greater than the received gas to consume.
     * If enough, received gas is discounted from accumulated. If not enough, all available gas is discounted.
     * This method is for special cases when we want to force the gas subtraction regardless available one is enough
     *
     * @param virtualGasToConsume Gas to be consumed
     * @param tx                  Tx being executed, used for logging purposes only
     * @param blockNumber         Block number for logging purposes
     *
     * @return True if there was enough accumulated gas, false otherwise
     */
    public synchronized boolean forceVirtualGasSubtraction(double virtualGasToConsume, Transaction tx, long blockNumber) {
        boolean wasAccepted = acceptVirtualGasConsumption(virtualGasToConsume, tx, blockNumber, true);
        if (wasAccepted) {
            return true;
        }

        this.availableVirtualGas = 0;
        return false;
    }

    /**
     * Refreshes availableVirtualGas according to inactivity time
     *
     * @param address         Address sourcing the tx, for logging purposes
     * @param maxGasPerSecond Gas to accumulate for each second of inactivity
     * @param maxQuota        Max virtual gas to provide even if accumulated gas is greater
     * @return The new accumulated virtual gas
     */
    public synchronized double refresh(RskAddress address, long maxGasPerSecond, long maxQuota) {
        long currentTimestamp = this.timeProvider.currentTimeMillis();

        double timeDiffSeconds = (currentTimestamp - this.timestamp) / 1000d;
        double addToQuota = timeDiffSeconds * maxGasPerSecond;
        this.timestamp = currentTimestamp;
        this.availableVirtualGas = Math.min(maxQuota, this.availableVirtualGas + addToQuota);

        String addressHex = address.toHexString();
        logger.trace("Quota refreshed for account [{}], new value [{}] (addToQuota [{}])", addressHex, this.availableVirtualGas, addToQuota);

        return this.availableVirtualGas;
    }

    @Override
    public String toString() {
        return "TxQuota{" +
                "timestamp=" + timestamp +
                ", availableVirtualGas=" + availableVirtualGas +
                '}';
    }
}
