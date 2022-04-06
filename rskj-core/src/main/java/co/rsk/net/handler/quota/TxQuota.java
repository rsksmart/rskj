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
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the Quota information for a given address
 */
public class TxQuota {

    private static final Logger logger = LoggerFactory.getLogger(TxQuota.class);

    private final TimeProvider timeProvider;
    private long timestamp;
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
     * @return True if there was enough accumulated gas
     */
    public synchronized boolean acceptVirtualGasConsumption(double virtualGasToConsume, Transaction tx) {
        if (this.availableVirtualGas < virtualGasToConsume) {
            logger.warn("NOT enough virtualGas for tx [{}]: availableVirtualGas=[{}], virtualGasToConsume=[{}]", tx, this.availableVirtualGas, virtualGasToConsume);
            return false;
        }

        logger.trace("Enough virtualGas for tx [{}]: availableVirtualGas=[{}], virtualGasToConsume=[{}]", tx, this.availableVirtualGas, virtualGasToConsume);
        this.availableVirtualGas -= virtualGasToConsume;
        return true;
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

        logger.trace("Quota refreshed for account [{}], new value [{}] (addToQuota [{}])", address.toHexString(), this.availableVirtualGas, addToQuota);

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
