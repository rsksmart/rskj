package co.rsk.storagerent;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rent computation util according to the RSKIP240
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 */
public class StorageRentComputation {
    public static final long READ_THRESHOLD = 2500;
    public static final long READ_THRESHOLD_CONTRACT_CODE = 15000;
    public static final long WRITE_THRESHOLD = 1000;
    public static final long RENT_CAP = 5000;
    public static final long RENT_CAP_CODE = 30000;

    private static final double RENTAL_RATE = (1 / Math.pow(2, 21));
    private static final long STORAGE_OVERHEAD = 128;

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageRentComputation.class);

    /**
     * Computes the amount of rent to be paid for trie reads/writes.
     *
     * @param rentDue       a rent due
     * @param rentCap       a maximum gas amount to be pay
     * @param rentThreshold a gas threshold
     * @return an amount of gas to be paid
     */
    public static long computeRent(long rentDue, long rentCap, long rentThreshold) {
        validateArgumentsComputeRent(rentCap, rentThreshold);

        long computedRent = Math.min(rentCap, rentDue);
        long result = computedRent > rentThreshold ? computedRent : 0;

        return result;
    }

    /**
     * Calculates the total rent due.
     * This is not the actual rent to be paid within a transaction because it's then limited by the rent cap.
     *
     * @param nodeSize a node size (expressed in bytes)
     * @param duration a duration from the last paid time (expressed in seconds)
     * @return total rent due (gas amount)
     */
    public static long rentDue(long nodeSize, long duration) {
        validateArgumentsRentDue(nodeSize, duration);
        long nodeSizeWithOverhead = nodeSize + STORAGE_OVERHEAD;

        Double result = Math.floor(Double.valueOf(nodeSizeWithOverhead) * Double.valueOf(duration) * RENTAL_RATE);

        return result.longValue();
    }

    /**
     * Computes the new timestamp after paying the rent or not.
     * If the rent exceeds the cap, it partially advances the last paid time.
     *
     * @param nodeSize a node size (expressed in bytes)
     * @param rentDue rent due (gas)
     * @param lastPaidTimestamp a timestamp corresponding to the last payment (expressed in seconds)
     * @param currentBlockTimestamp the current block timestamp
     * @param rentCap a gas cap (useful to partially advance the last payment)
     * @param rentThreshold a gas threshold
     *
     * @return the next timestamp (it can be the same if it doesn't reach the threshold)
     * */
    public static long computeNewTimestamp(long nodeSize, long rentDue, long lastPaidTimestamp, long currentBlockTimestamp,
                                        long rentCap, long rentThreshold) {
        // todo(fedejinnich) add validations
        if (rentDue <= rentThreshold) {
            return lastPaidTimestamp;
        } else if (rentThreshold < rentDue && rentDue <= rentCap) {
            return currentBlockTimestamp;
        }

        double rentCapDouble = Long.valueOf(rentCap).doubleValue();
        double nodeSizeDouble = Long.valueOf(nodeSize).doubleValue();

        // if rent due exceeds cap, it partially advances the last paid timestamp
        Double timePaid = Math.floor(rentCapDouble / (nodeSizeDouble * RENTAL_RATE));
        return lastPaidTimestamp + timePaid.longValue();
    }

    private static void validateArgumentsRentDue(long nodeSize, long duration) {
        validPositiveValue(nodeSize, "node size must be positive");
        validPositiveValue(duration, "duration must be positive");
    }

    private static void validateArgumentsComputeRent(long cap, long threshold) {
        validPositiveValue(cap, "cap must be positive");
        validPositiveValue(threshold, "threshold must be positive");
    }

    private static void validPositiveValue(long value, String s) {
        if (value < 0) {
            throw new IllegalArgumentException(s);
        }
    }
}
