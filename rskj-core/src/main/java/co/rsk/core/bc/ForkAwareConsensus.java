package co.rsk.core.bc;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.metrics.jmx.JmxMetric;
import co.rsk.metrics.jmx.MetricAggregate;
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.peg.BtcBlockStoreWithCache;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public class ForkAwareConsensus implements ForkAwareConsensusMBean {

    private static final Logger logger = LoggerFactory.getLogger("forkawareconsensus");

    /**
     * Maximum wall-clock span (in minutes) for BTC best-chain entries kept in btcBestChainList.
     * Oldest entries are dropped from the tail until newest−oldest ≤ this duration.
     */
    public static final int BTC_EPOCH_DURATION_MINUTES = 160;
    /**
     * Maximum wall-clock span (in minutes) for entries in rskRecentBtcHeaders.
     * Same trimming rule as btcBestChainList, but typically a wider window.
     */
    public static final int BTC_FROM_RSK_CACHE = 2 * BTC_EPOCH_DURATION_MINUTES;
    /** Fixed bar for safe mode until enough BTC heads have been observed for adaptive tuning. */
    private static final double DEFAULT_SAFE_MODE_THRESHOLD = 0.5;
    /**
     * When adaptive safe mode is active, recent-window MM proportion must be at least
     * SAFE_MODE_PARAMETER × longTermMMProportion (fraction of the historical average).
     */
    private static final double SAFE_MODE_PARAMETER = 0.65;
    /** Minimum BTC heads processed before switching from DEFAULT_SAFE_MODE_THRESHOLD to adaptive threshold. */
    private static final long MIN_BTC_BLOCKS_TO_ADAPTIVE = 50L;

    private final NetworkParameters btcNetworkParameters;
    /** Newest-first sliding window aligned with the bridge BTC best chain. */
    private final Deque<BtcChainEntry> btcBestChainList = new LinkedList<>();
    /** Newest-first BTC headers referenced by RSK blocks; trimmed by header time span. */
    private final Deque<RskBtcHeaderEntry> rskRecentBtcHeaders = new LinkedList<>();

    private Block LastSafeRskBlock;
    private StoredBlock LastSafeBtcBlock;
    private double mergedMinedProportion;

    private long rskBlocksProcessed;
    private long mmCount16;
    private long mmCount20;
    private long mmCount32;
    private long mmCount64;
    private long mmCount100;

    /**
     * Running proportion of merged-mined BTC heads over numberBtcBlocksProcessed (incremental mean; reset on reorg replay).
     */
    private double longTermMMProportion;
    /** New bridge chain heads observed since the last reorg reset (not evicted from the sliding window). */
    private long numberBtcBlocksProcessed;
    /**
     * Long-term BTC block arrival rate: numberBtcBlocksProcessed / (tNew − firstBtcTimestamp) in blocks per second,
     * once at least two distinct timestamps exist; 0 otherwise.
     */
    private double longTermBtcRate;
    /** Header time (seconds) of the first BTC head we processed for long-term stats. */
    private long firstBtcTimestamp;

    /**
     * True while recent-window merged-mined proportion is below the required threshold (safe mode).
     * Last-safe pointers are updated when leaving safe mode, or once on bootstrap while healthy if still unset.
     */
    private boolean mmSafeModeActive;

    /**
     * Incremented on each merged-mined match; registered as a JMX attribute (often scraped by Prometheus jmx_exporter).
     */
    private final JmxMetric totalMmMatchesMetric = new JmxMetric(MetricKind.BLOCK_CONNECTION, MetricAggregate.SUM);

    public ForkAwareConsensus(NetworkParameters btcNetworkParameters) {
        this.btcNetworkParameters = Objects.requireNonNull(btcNetworkParameters);
        logger.info("ForkAwareConsensus PoC Initialized");
        registerJmx();
    }

    /**
     * Entry point used by the blockchain once a block has been fully validated and executed on the canonical best chain.
     */
    public void updateMetricsState(Block rskBlock, BtcBlockStoreWithCache btcBlockStore) {
        updateMetricsState(rskBlock, btcBlockStore, true);
    }

    /**
     * After a canonical re-branch (BlockStore.reBranch), resets fork-aware state and reapplies metrics for
     * each block on the new canonical segment (exclusive common ancestor, inclusive new tip), in ascending height order.
     *
     * @param commonAncestor            fork point shared by old and new best chains
     * @param newCanonicalSegmentOldestFirst blocks on the new chain after the ancestor, oldest first
     * @param btcBlockStoreForExecutedBlock  bridge BTC store as of each block's parent state (same as execution)
     */
    public void onReorganization(
            Block commonAncestor,
            List<Block> newCanonicalSegmentOldestFirst,
            Function<Block, BtcBlockStoreWithCache> btcBlockStoreForExecutedBlock) {
        Objects.requireNonNull(commonAncestor, "commonAncestor");
        Objects.requireNonNull(newCanonicalSegmentOldestFirst, "newCanonicalSegmentOldestFirst");
        Objects.requireNonNull(btcBlockStoreForExecutedBlock, "btcBlockStoreForExecutedBlock");

        resetStateForReorganization(commonAncestor);
        for (Block b : newCanonicalSegmentOldestFirst) {
            updateMetricsState(b, btcBlockStoreForExecutedBlock.apply(b), false);
        }
        trimDequesToConfiguredSpans();
    }

    private void resetStateForReorganization(Block commonAncestor) {
        rskRecentBtcHeaders.clear();
        btcBestChainList.clear();
        rskBlocksProcessed = commonAncestor.getNumber();
        numberBtcBlocksProcessed = 0;
        longTermMMProportion = 0.0;
        longTermBtcRate = 0.0;
        firstBtcTimestamp = 0;
        mmCount16 = 0;
        mmCount20 = 0;
        mmCount32 = 0;
        mmCount64 = 0;
        mmCount100 = 0;
        mergedMinedProportion = 0.0;
        LastSafeRskBlock = null;
        LastSafeBtcBlock = null;
        mmSafeModeActive = false;
    }

    /**
     * Trims both deques to their configured wall-clock spans (after bulk updates such as reorg replay).
     */
    public void trimDequesToConfiguredSpans() {
        trimDequeByNewestOldestTimeSpan(
                btcBestChainList,
                e -> btcHeaderTimeSeconds(e.block),
                minutesToSeconds(BTC_EPOCH_DURATION_MINUTES));
        trimDequeByNewestOldestTimeSpan(
                rskRecentBtcHeaders,
                e -> e.btcHeaderTimeSeconds,
                minutesToSeconds(BTC_FROM_RSK_CACHE));
    }

    private void updateMetricsState(Block rskBlock, BtcBlockStoreWithCache btcBlockStore, boolean recordMmMatchMetric) {
        if (rskBlock == null || btcBlockStore == null) {
            return;
        }

        rskBlocksProcessed++;

        byte[] btcHeaderBytes = rskBlock.getBitcoinMergedMiningHeader();
        if (btcHeaderBytes != null && btcHeaderBytes.length > 0) {
            try {
                BtcBlock rskBtcHeader = btcNetworkParameters.getDefaultSerializer().makeBlock(btcHeaderBytes);
                recordRskBtcHeader(rskBlock, rskBtcHeader);
            } catch (Exception e) {
                logger.warn("Failed to parse BTC header in RSK block {}", rskBlock.getNumber());
            }
        }

        BtcChainEntry newBtcHead = syncWithBtcMainChain(btcBlockStore);

        markMergedMinedFromRskHeaders(rskBlock, recordMmMatchMetric);

        if (newBtcHead != null) {
            updateLongTermBtcMetrics(newBtcHead);
        }

        performRetrospectiveMatching(rskBlock);
        updateWindowedCounts();
    }

    private void recordRskBtcHeader(Block rskBlock, BtcBlock rskBtcHeader) {
        if (rskBtcHeader == null) {
            return;
        }

        Sha256Hash rskBtcHash = rskBtcHeader.getHash();
        long btcTime = rskBtcHeader.getTimeSeconds();
        rskRecentBtcHeaders.addFirst(new RskBtcHeaderEntry(rskBtcHash, rskBlock.getNumber(), btcTime));
        logger.debug("[PoC] RSK block {} referenced BTC header {} (btc time {})", rskBlock.getNumber(), rskBtcHash, btcTime);

        trimDequeByNewestOldestTimeSpan(
                rskRecentBtcHeaders,
                e -> e.btcHeaderTimeSeconds,
                minutesToSeconds(BTC_FROM_RSK_CACHE));
    }

    private void markMergedMinedFromRskHeaders(Block rskBlock, boolean recordMmMatchMetric) {
        for (BtcChainEntry entry : btcBestChainList) {
            if (entry.isMergedMined) {
                continue;
            }

            for (RskBtcHeaderEntry rskHeaderEntry : rskRecentBtcHeaders) {
                if (!rskHeaderEntry.hash.equals(entry.hash)) {
                    continue;
                }

                entry.isMergedMined = true;
                if (recordMmMatchMetric) {
                    totalMmMatchesMetric.updateDuration(1);
                }

                logger.info("[PoC] MATCH FOUND! BTC Block {} at height {} tagged as Merged-Mined by RSK Block {}",
                        entry.hash, entry.block.getHeight(), rskHeaderEntry.rskBlockNumber);
                break;
            }
        }
    }

    /**
     * Returns the new BtcChainEntry if a chain head was appended; null if unchanged or duplicate.
     */
    @Nullable
    private BtcChainEntry syncWithBtcMainChain(BtcBlockStoreWithCache btcBlockStore) {
        try {
            StoredBlock btcHead = btcBlockStore.getChainHead();
            if (btcHead == null) {
                return null;
            }

            BtcChainEntry newlyAdded = null;
            if (btcBestChainList.isEmpty() || !btcBestChainList.peekFirst().block.getHeader().getHash().equals(btcHead.getHeader().getHash())) {
                if (!containsHash(btcHead.getHeader().getHash())) {
                    newlyAdded = new BtcChainEntry(btcHead);
                    btcBestChainList.addFirst(newlyAdded);
                    logger.debug("[PoC] BTC Chain advanced to height {}", btcHead.getHeight());
                }
            }

            trimDequeByNewestOldestTimeSpan(
                    btcBestChainList,
                    e -> btcHeaderTimeSeconds(e.block),
                    minutesToSeconds(BTC_EPOCH_DURATION_MINUTES));
            return newlyAdded;
        } catch (BlockStoreException e) {
            logger.error("Error syncing with BTC Bridge", e);
            return null;
        }
    }

    /**
     * Drops entries from the tail of a newest-first deque while the absolute time span between
     * the newest (peekFirst) and oldest (peekLast) item exceeds thresholdSeconds.
     */
    private static <T> void trimDequeByNewestOldestTimeSpan(
            Deque<T> deque,
            ToLongFunction<T> timeSeconds,
            long thresholdSeconds) {
        while (deque.size() > 1) {
            long newestTime = timeSeconds.applyAsLong(Objects.requireNonNull(deque.peekFirst()));
            long oldestTime = timeSeconds.applyAsLong(Objects.requireNonNull(deque.peekLast()));
            long spanSeconds = Math.abs(newestTime - oldestTime);
            if (spanSeconds <= thresholdSeconds) {
                break;
            }
            deque.removeLast();
        }
    }

    private static long minutesToSeconds(int durationMinutes) {
        return durationMinutes * 60L;
    }

    private static long btcHeaderTimeSeconds(StoredBlock storedBlock) {
        return storedBlock.getHeader().getTimeSeconds();
    }

    /**
     * Updates cumulative merged-mined proportion and BTC arrival rate when a new bridge chain head is observed.
     * Must run after markMergedMinedFromRskHeaders so BtcChainEntry.isMergedMined is final for this head.
     */
    private void updateLongTermBtcMetrics(BtcChainEntry newHead) {
        long tNew = btcHeaderTimeSeconds(newHead.block);
        numberBtcBlocksProcessed++;

        if (numberBtcBlocksProcessed == 1) {
            firstBtcTimestamp = tNew;
            longTermMMProportion = newHead.isMergedMined ? 1.0 : 0.0;
            longTermBtcRate = 0.0;
            return;
        }

        longTermMMProportion =
                (longTermMMProportion * (numberBtcBlocksProcessed - 1) + (newHead.isMergedMined ? 1.0 : 0.0))
                        / numberBtcBlocksProcessed;

        long deltaSeconds = tNew - firstBtcTimestamp;
        if (deltaSeconds > 0) {
            longTermBtcRate = (double) numberBtcBlocksProcessed / deltaSeconds;
        }
    }

    private void performRetrospectiveMatching(Block rskBlock) {
        long mmCount = btcBestChainList.stream().filter(e -> e.isMergedMined).count();
        mergedMinedProportion = btcBestChainList.isEmpty() ? 0 : (double) mmCount / btcBestChainList.size();

        if (btcBestChainList.isEmpty()) {
            return;
        }

        double requiredProportion = safeModeRequiredProportion();
        boolean belowThreshold = mergedMinedProportion < requiredProportion;
        boolean atOrAboveThreshold = mergedMinedProportion >= requiredProportion;

        if (belowThreshold) {
            if (!mmSafeModeActive) {
                logger.warn("[PoC Safe Mode] Entering safe mode: recentMM={} required={} (adaptive={})",
                        mergedMinedProportion, requiredProportion, numberBtcBlocksProcessed >= MIN_BTC_BLOCKS_TO_ADAPTIVE);
            }
            mmSafeModeActive = true;
            return;
        }

        if (atOrAboveThreshold) {
            if (mmSafeModeActive) {
                mmSafeModeActive = false;
                assignLastSafePointers(rskBlock);
                boolean adaptive = numberBtcBlocksProcessed >= MIN_BTC_BLOCKS_TO_ADAPTIVE;
                logger.info("[PoC Safe Mode] Leaving safe mode; updated last-safe pointers. required={} (adaptive={}, longTermMM={}) recentMM={} Last Safe BTC: {}, Last Safe RSK: {}",
                        requiredProportion, adaptive, longTermMMProportion, mergedMinedProportion,
                        LastSafeBtcBlock.getHeader().getHash(), LastSafeRskBlock.getNumber());
            } else if (LastSafeRskBlock == null) {
                assignLastSafePointers(rskBlock);
                boolean adaptive = numberBtcBlocksProcessed >= MIN_BTC_BLOCKS_TO_ADAPTIVE;
                logger.info("[PoC Safe Mode] Initial last-safe established. required={} (adaptive={}, longTermMM={}) recentMM={} Last Safe BTC: {}, Last Safe RSK: {}",
                        requiredProportion, adaptive, longTermMMProportion, mergedMinedProportion,
                        LastSafeBtcBlock.getHeader().getHash(), LastSafeRskBlock.getNumber());
            }
        }
    }

    private void assignLastSafePointers(Block rskBlock) {
        LastSafeBtcBlock = Objects.requireNonNull(btcBestChainList.peekFirst()).block;
        LastSafeRskBlock = rskBlock;
    }

    /**
     * Required recent-window MM proportion for declaring safe mode.
     * Before MIN_BTC_BLOCKS_TO_ADAPTIVE BTC heads: fixed DEFAULT_SAFE_MODE_THRESHOLD.
     * After: SAFE_MODE_PARAMETER × longTermMMProportion (adaptive vs historical average).
     */
    private double safeModeRequiredProportion() {
        if (numberBtcBlocksProcessed < MIN_BTC_BLOCKS_TO_ADAPTIVE) {
            return DEFAULT_SAFE_MODE_THRESHOLD;
        }
        return SAFE_MODE_PARAMETER * longTermMMProportion;
    }

    private void updateWindowedCounts() {
        mmCount16 = countMergedMinedInWindow(16);
        mmCount20 = countMergedMinedInWindow(20);
        mmCount32 = countMergedMinedInWindow(32);
        mmCount64 = countMergedMinedInWindow(64);
        mmCount100 = countMergedMinedInWindow(100);
    }

    private long countMergedMinedInWindow(int windowSize) {
        if (btcBestChainList.isEmpty() || windowSize <= 0) {
            return 0;
        }

        int count = 0;
        int processed = 0;
        for (BtcChainEntry entry : btcBestChainList) {
            if (entry.isMergedMined) {
                count++;
            }
            processed++;
            if (processed >= windowSize) {
                break;
            }
        }
        return count;
    }

    private boolean containsHash(Sha256Hash hash) {
        return btcBestChainList.stream().anyMatch(e -> e.block.getHeader().getHash().equals(hash));
    }

    private static class BtcChainEntry {
        final StoredBlock block;
        final Sha256Hash hash;
        boolean isMergedMined;

        BtcChainEntry(StoredBlock block) {
            this.block = block;
            this.hash = block.getHeader().getHash();
            this.isMergedMined = false;
        }
    }

    private static class RskBtcHeaderEntry {
        final Sha256Hash hash;
        final long rskBlockNumber;
        /** BTC block header time (seconds), used for time-span eviction aligned with bridge list logic. */
        final long btcHeaderTimeSeconds;

        RskBtcHeaderEntry(Sha256Hash hash, long rskBlockNumber, long btcHeaderTimeSeconds) {
            this.hash = hash;
            this.rskBlockNumber = rskBlockNumber;
            this.btcHeaderTimeSeconds = btcHeaderTimeSeconds;
        }
    }

    // --- JMX / MBean (operator visibility; Prometheus typically via jmx_exporter)

    private void registerJmx() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("co.rsk.metrics:type=ForkAware,name=Statistics");
            if (mbs.isRegistered(name)) {
                mbs.unregisterMBean(name);
            }
            mbs.registerMBean(this, name);
            logger.info("Successfully registered ForkAware MBean: {}", name);
        } catch (Exception e) {
            logger.error("JMX Registration CRITICAL FAILURE", e);
        }
    }

    @Override
    public long getCurrentEpochMmMatches() {
        return btcBestChainList.stream().filter(e -> e.isMergedMined).count();
    }

    @Override
    public long getRskBlocksProcessed() {
        return rskBlocksProcessed;
    }

    @Override
    public long getMmCount_16() {
        return mmCount16;
    }

    @Override
    public long getMmCount_20() {
        return mmCount20;
    }

    @Override
    public long getMmCount_32() {
        return mmCount32;
    }

    @Override
    public long getMmCount_64() {
        return mmCount64;
    }

    @Override
    public long getMmCount_100() {
        return mmCount100;
    }

    @Override
    public double getLongTermMMProportion() {
        return longTermMMProportion;
    }

    @Override
    public long getNumberBtcBlocksProcessed() {
        return numberBtcBlocksProcessed;
    }

    @Override
    public double getLongTermBtcRate() {
        return longTermBtcRate;
    }

    @Override
    public long getFirstBtcTimestamp() {
        return firstBtcTimestamp;
    }

    @Override
    public long getBtcBestChainListSize() {
        return btcBestChainList.size();
    }

    @Override
    public double getMergedMinedProportion() {
        return mergedMinedProportion;
    }

    @Override
    public String getLastSafeRskBlock() {
        return LastSafeRskBlock != null ? LastSafeRskBlock.toString() : "N/A";
    }

    @Override
    public String getLastSafeBtcBlock() {
        return LastSafeBtcBlock != null ? LastSafeBtcBlock.toString() : "N/A";
    }

    @Nullable
    public Block getLastSafeRskBlockObj() {
        return LastSafeRskBlock;
    }
}
