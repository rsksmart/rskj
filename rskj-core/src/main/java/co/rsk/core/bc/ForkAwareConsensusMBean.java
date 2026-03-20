package co.rsk.core.bc;

public interface ForkAwareConsensusMBean {
    long getCurrentEpochMmMatches();
    long getRskBlocksProcessed();
    long getMmCount_16();
    long getMmCount_20();
    long getMmCount_32();
    long getMmCount_64();
    long getMmCount_100();

    double getLongTermMMProportion();

    long getNumberBtcBlocksProcessed();

    double getLongTermBtcRate();

    long getFirstBtcTimestamp();

    long getBtcBestChainListSize();

    double getMergedMinedProportion();

    String getLastSafeRskBlock();

    String getLastSafeBtcBlock();
}