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

import org.ethereum.db.OperationType;
import org.ethereum.vm.GasCost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;

/**
 * Storage Rent Util
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

    public static long feeByRent(long computedRent) {
        return BigDecimal.valueOf(computedRent)
                .divide(BigDecimal.valueOf(4), RoundingMode.FLOOR)
                .longValue();
    }

    public static long rentThreshold(OperationType operationType) {
        switch (operationType) {
            case WRITE_OPERATION:
            case DELETE_OPERATION:
                return WRITE_THRESHOLD;
            case READ_OPERATION:
                return READ_THRESHOLD;
            default:
                throw new RuntimeException("this shouldn't happen");
        }
    }

    private static void validPositiveValue(long value, String s) {
        if (value < 0) {
            throw new IllegalArgumentException(s);
        }
    }

    /**
     * Maps a collection an calculates rent
     *
     * @param rentedNodes a RentedNode collection
     * @param rentFunction a map function to calculate rent
     * @return a rent amountx
     * */
    public static long rentBy(Collection<RentedNode> rentedNodes, Function<RentedNode, Long> rentFunction) {
        Optional<Long> rent = rentedNodes.stream()
                .map(r -> rentFunction.apply(r))
                .reduce(GasCost::add);

        return rentedNodes.isEmpty() || !rent.isPresent() ? 0 : rent.get();
    }

    /***
     * Calculates the rent amount for reading/writing non-existent trie nodes
     *
     * @param mismatchesCount
     * @return a rent amount
     */
    public static long mismatchesRent(long mismatchesCount) {
        return MISMATCH_PENALTY * mismatchesCount;
    }

    // todo(fedejinich) add unit test
    public static StorageRentResult calculateRent(long mismatchesCount,
                                                  Set<RentedNode> rentedNodes,
                                                  Set<RentedNode> rollbackNodes,
                                                  long gasRemaining, long executionBlockTimestamp) {
        long payableRent = rentBy(rentedNodes, rentedNode -> rentedNode.payableRent(executionBlockTimestamp));
        long rollbacksRent = rentBy(rollbackNodes, rentedNode -> rentedNode.rollbackFee(executionBlockTimestamp,
                rentedNodes));

        long rentToPay = payableRent + rollbacksRent + StorageRentUtil.mismatchesRent(mismatchesCount);

        // not enough gas to pay rent
        if(gasRemaining < rentToPay) {
            return StorageRentResult.outOfGas(rentedNodes, rollbackNodes, mismatchesCount, executionBlockTimestamp,
                    rentToPay);
        }

        long gasAfterPayingRent = GasCost.subtract(gasRemaining, rentToPay);

        return StorageRentResult.ok(rentedNodes, rollbackNodes,
                gasAfterPayingRent, mismatchesCount, executionBlockTimestamp, rentToPay);
    }
}
