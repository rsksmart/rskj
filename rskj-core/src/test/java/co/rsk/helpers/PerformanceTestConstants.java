package co.rsk.helpers;

/**
 * Created by Sergio Demian Lerner on 12/10/2018.
 */
public class PerformanceTestConstants {
    // We aim for blocks with 7M gas units top (without parallel processing)
    final public static int blockGasLimit = 7 * 1000 * 1000;
    final public static int maxBlockProcessingTimeMillis = 550;
    final public static int maxMegabytesConsumedPerBlock = 100;
}
