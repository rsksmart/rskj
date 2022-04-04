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

import co.rsk.util.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the Quota information for a given address
 */
public class TxQuota {

    private static final Logger logger = LoggerFactory.getLogger(TxQuota.class);

    private final String addressHex;
    private final TimeProvider timeProvider;
    private long timestamp;
    private double availableVirtualGas;

    private TxQuota(String addressHex, String txHash, long availableVirtualGas, TimeProvider timeProvider) {
        this.addressHex = addressHex;
        this.timeProvider = timeProvider;
        this.timestamp = this.timeProvider.currentTimeMillis();
        this.availableVirtualGas = availableVirtualGas;

        logger.debug("Quota created for account [{}] after tx [{}] with value [{}]", addressHex, txHash, this.availableVirtualGas);
    }

    /**
     * Creates a new instance of TxQuota
     * @param addressHex Hex of the address sourcing the tx
     * @param txHash Hash of the tx after which the quota is being created, for logging purposes
     * @param initialQuota The initialQuota to provide
     * @param timeProvider The time provider to calculate inactivity periods, etc.
     * @return a new instance of TxQuota
     */
    public static TxQuota createNew(String addressHex, String txHash, long initialQuota, TimeProvider timeProvider) {
        return new TxQuota(addressHex, txHash, initialQuota, timeProvider);
    }

    /**
     * Checks whether the accumulated virtual gas is greater than the received gas to consume. If enough, received gas is discounted from accumulated.
     *
     * @param virtualGasToConsume Gas to be consumed
     * @param txHash Hash of the tx for logging purposes
     * @return True if there was enough accumulated gas
     */
    public synchronized boolean acceptVirtualGasConsumption(double virtualGasToConsume, String txHash) {
        if (this.availableVirtualGas < virtualGasToConsume) {
            logger.warn("NOT enough virtualGas for address [{}] and tx [{}]: availableVirtualGas=[{}], virtualGasToConsume=[{}]", this.addressHex, this.availableVirtualGas, txHash, virtualGasToConsume);
            return false;
        }

        logger.trace("Enough virtualGas for address [{}]: availableVirtualGas=[{}], virtualGasToConsume=[{}]", this.addressHex, this.availableVirtualGas, virtualGasToConsume);
        this.availableVirtualGas -= virtualGasToConsume;
        return true;
    }

    /**
     * Refreshes availableVirtualGas according to inactivity time
     *
     * @param maxGasPerSecond Gas to accumulate for each second of inactivity
     * @param maxQuota        Max virtual gas to provide even if accumulated gas is greater
     * @return The new accumulated virtual gas
     */
    public synchronized double refresh(long maxGasPerSecond, long maxQuota) {
        long currentTimestamp = this.timeProvider.currentTimeMillis();

        double timeDiffSeconds = (currentTimestamp - this.timestamp) / 1000d;
        double addToQuota = timeDiffSeconds * maxGasPerSecond;
        this.timestamp = currentTimestamp;
        this.availableVirtualGas = Math.min(maxQuota, this.availableVirtualGas + addToQuota);

        logger.debug("Quota refreshed for account [{}], new value [{}] (addToQuota [{}])", this.addressHex, this.availableVirtualGas, addToQuota);

        return this.availableVirtualGas;
    }

    @Override
    public String toString() {
        return "TxQuota{" +
                "addressHex='" + addressHex + '\'' +
                ", timestamp=" + timestamp +
                ", availableVirtualGas=" + availableVirtualGas +
                '}';
    }
}
