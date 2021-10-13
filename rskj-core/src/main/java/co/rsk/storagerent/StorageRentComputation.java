package co.rsk.storagerent;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rent computation util according to the RSKIP240
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentComputation {
    public static final long READ_THRESHOLD = 2500;
    public static final long READ_THRESHOLD_CONTRACT_CODE = 15000;
    public static final long WRITE_THRESHOLD = 1000;
    public static final long RENT_CAP = 5000;
    public static final long RENT_CAP_CODE = 30000;

    private static final double RENTAL_RATE = (1/Math.pow(2, 21));
    private static final long STORAGE_OVERHEAD = 128;

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageRentComputation.class);

    /**
     * Computes the amount of rent to be paid for trie reads/writes
     * @param nodeSize a node size (expressed in bytes)
     * @param duration a duration from the last paid time (expressed in seconds)
     * @param cap a maximum gas amount to be pay
     * @param threshold a gas threshold to compute rent, below the threshold will return 0
     * @return a gas amount of rent*/
    public static double computeRent(long nodeSize, double duration, long cap, long threshold) {
        validateArguments(nodeSize, duration, cap, threshold);

        long nodeSizeWithOverhead = nodeSize + STORAGE_OVERHEAD;
        double computedRent = Math.min(cap, nodeSizeWithOverhead * duration * RENTAL_RATE);
        double result = computedRent > threshold ? computedRent : 0;

        LOGGER.error("computed rent before threshold={}", computedRent);
        LOGGER.error("computed rent {} gas. nodeSize={}, duration={}, cap={}, threshold={}", result, nodeSizeWithOverhead, duration, cap, threshold);

        return result; // todo(fedejinich) should I up-round the result?
    }

    private static void validateArguments(long nodeSize, double duration, long cap, long threshold) {
        if(nodeSize < 0) {
            throw new IllegalArgumentException("node size must be positive");
        }

        if(duration < 0) {
            throw new IllegalArgumentException("duration must be positive");
        }

        if(cap < 0) {
            throw new IllegalArgumentException("cap must be positive");
        }

        if(threshold < 0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
    }
}
