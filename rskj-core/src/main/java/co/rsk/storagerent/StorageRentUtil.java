/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.storagerent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;

/**
 * Rent computation util according to the RSKIP240
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 */
public class StorageRentUtil {
    public static final long READ_THRESHOLD = 2500;
    public static final long WRITE_THRESHOLD = 1000;
    public static final long RENT_CAP = 5000;
    public static final long MISMATCH_PENALTY = 2500;
    private static final BigDecimal RENTAL_RATE = BigDecimal.ONE.divide(BigDecimal.valueOf(2).pow(21));
    private static final long STORAGE_OVERHEAD = 128;

    private StorageRentUtil() {}

    /**
     * Computes the amount of rent to be paid for trie reads/writes.
     * The final amount it's determined by the rent cap and rent threshold
     *
     * @param rentDue       a rent due
     * @param rentCap       a maximum gas amount to be pay
     * @param rentThreshold a gas threshold
     * @return an amount of gas to be paid
     */
    public static long computeRent(long rentDue, long rentCap, long rentThreshold) {
        validPositiveValue(rentCap, "cap must be positive");
        validPositiveValue(rentThreshold, "threshold must be positive");

        long computedRent = Math.min(rentCap, rentDue);
        
        return computedRent > rentThreshold ? computedRent : 0;
    }

    /**
     * Calculates the total rent due.
     * This is not the actual rent to be paid within a transaction because it's then limited by the rent cap.
     *
     * @param nodeSize a node value length (expressed in bytes)
     * @param duration a duration from the last paid time (expressed in milliseconds)
     * @return total rent due (gas amount)
     */
    // todo(fedejinich) this should be a private method, it's only used in computeRent & computeNewTimestamp
    public static long rentDue(long nodeSize, long duration) {
        validPositiveValue(nodeSize, "node size must be positive");
        validPositiveValue(duration, "duration must be positive");

        BigDecimal nodeSizeWithOverhead = BigDecimal.valueOf(nodeSize + STORAGE_OVERHEAD);
        BigDecimal durationSeconds = BigDecimal.valueOf(TimeUnit.MILLISECONDS.toSeconds(duration));

        long rentDue = nodeSizeWithOverhead
                .multiply(durationSeconds)
                .multiply(RENTAL_RATE).longValue();

        return rentDue;
    }

    /**
     * Computes the new timestamp after paying the rent or not.
     * If the rent exceeds the cap, it partially advances the last paid time.
     *
     * @param nodeSize a node size (expressed in bytes)
     * @param rentDue rent due (gas)
     * @param lastPaidTimestamp a timestamp corresponding to the last rent payment (expressed in seconds)
     * @param currentBlockTimestamp the current block timestamp
     * @param rentCap a gas cap (useful to partially advance the timestamp)
     * @param rentThreshold a gas threshold
     *
     * @return the new timestamp (it can be the same if it doesn't reach the threshold)
     * */
    public static long computeNewTimestamp(long nodeSize, long rentDue, long lastPaidTimestamp,
                                           long currentBlockTimestamp, long rentCap, long rentThreshold) {
        validPositiveValue(nodeSize, "nodeSize must be positive");
        validPositiveValue(rentDue, "rentDue must be positive");
        validPositiveValue(currentBlockTimestamp, "currentBlockTimestamp must be positive");
        validPositiveValue(rentCap, "rentCap must be positive");
        validPositiveValue(rentThreshold, "rentThreshold must be positive");

        if(lastPaidTimestamp == NO_RENT_TIMESTAMP) {
            return currentBlockTimestamp;
        }

        if (rentDue <= rentThreshold) {
            return lastPaidTimestamp;
        }

        if (rentDue <= rentCap) {
            return currentBlockTimestamp;
        }

        // partially advances the timestamp if rent due exceeds cap
        BigDecimal timePaid = BigDecimal.valueOf(rentCap)
                .divide(BigDecimal.valueOf(nodeSize).multiply(RENTAL_RATE), RoundingMode.FLOOR);

        return lastPaidTimestamp + timePaid.longValue();
    }

    private static void validPositiveValue(long value, String s) {
        if (value < 0) {
            throw new IllegalArgumentException(s);
        }
    }
}
