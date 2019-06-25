package co.rsk.helpers;

/** Created by Sergio Demian Lerner on 12/10/2018. */
public class PerformanceTestConstants {
    // We aim for blocks with 7M gas units top (without parallel processing)
    public static final int blockGasLimit = 7 * 1000 * 1000;
    public static final int maxBlockProcessingTimeMillis = 550;
    public static final int maxMegabytesConsumedPerBlock = 100;
}
