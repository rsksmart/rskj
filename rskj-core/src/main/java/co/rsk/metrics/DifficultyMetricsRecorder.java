
package co.rsk.metrics;

import co.rsk.core.BlockDifficulty;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records metrics for the difficulty calculation process.
 *
 * <p><b>Note:</b> This class writes to {@code /var/log/rsk/difficulty_calculation_metrics.csv}.
 * The user running the node must have write permissions to {@code /var/log/rsk/} directory.
 * On Unix-like systems, this typically requires:
 * <ul>
 *   <li>Creating the directory: {@code sudo mkdir -p /var/log/rsk}</li>
 *   <li>Setting permissions: {@code sudo chown $USER:$USER /var/log/rsk}</li>
 * </ul>
 */
public class DifficultyMetricsRecorder {
    private static final Logger logger = LoggerFactory.getLogger(DifficultyMetricsRecorder.class);

    // File path requires write permissions - see class-level documentation
    private static final String CSV_FILE_PATH = "/var/log/rsk/difficulty_calculation_metrics.csv";

    // The expected size of the block window
    private final int blockWindowSize = 30;
    // A list of previous log entries used to calculate the metrics
    private final List<LogEntry> logEntries;
    // The last block number processed (to avoid processing the same block twice)
    private long lastBlockNumber = 0;

    public DifficultyMetricsRecorder() {
        this.logEntries = new ArrayList<>();
        logger.info("Dumping difficulty calculation metrics to file: {}", CSV_FILE_PATH);
        // Check if the file exists
        File file = new File(CSV_FILE_PATH);
        if (!file.exists()) {
            try {
                // Ensure parent directories exist
                String parentPath = file.getParent();
                if (parentPath != null) {
                    Files.createDirectories(Paths.get(parentPath));
                    logger.debug("Parent directories created for CSV file: {}", parentPath);
                }
                // Create the file
                boolean fileCreated = file.createNewFile();
                if (fileCreated) {
                    logger.info("CSV file created: {}", CSV_FILE_PATH);
                } else {
                    logger.warn("CSV file already exists or could not be created: {}", CSV_FILE_PATH);
                }
                // Write the header to the file
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(LogEntry.getCsvHeader());
                    writer.write("\n");
                    writer.flush();
                }
            } catch (IOException e) {
                logger.error("Error creating the CSV file: {}", e.getMessage());
            }
        }
    }

    /**
     * Adds a new block to the log entries.
     *
     * @param blockHeader
     *            The header of the new block.
     * @param blockDifficulty
     *            The difficulty of the new block.
     * @param parentDifficulty
     *            The difficulty of the parent block.
     */
    public void addBlock(BlockHeader blockHeader, BlockDifficulty blockDifficulty, BlockDifficulty parentDifficulty) {
        try {
            if (blockHeader.getNumber() <= lastBlockNumber) {
                // Skip the block, since it was already processed
                return;
            }
            LogEntry newEntry = new LogEntry(blockHeader.getNumber(), blockHeader.getTimestamp(),
                    blockDifficulty, blockHeader.getUncleCount(), calcBlockTime(blockHeader),
                    parentDifficulty, calcWindowUncleCount(blockHeader),
                    calcUncleRate(blockHeader), calcBlockTimeAverage(blockHeader));
            logEntries.add(newEntry);
            // Ensure the window size is not exceeded
            if (logEntries.size() > blockWindowSize) {
                logEntries.remove(0);
            }
            // Only start logging when the window is full
            if (logEntries.size() == blockWindowSize) {
                logMetrics();
            } else {
                logger.info("Window is not full yet. Current window size: {}", logEntries.size());
            }
            // Update the last block number processed
            lastBlockNumber = blockHeader.getNumber();
        } catch (Exception e) {
            logger.error("Error updating metrics: {}", e.getMessage());
        }
    }

    private long calcBlockTime(BlockHeader blockHeader) {
        if (logEntries.isEmpty()) {
            // This is the first block of the window, we can't calculate the block time
            return 0;
        }
        LogEntry lastEntry = logEntries.get(logEntries.size() - 1);
        long lastTimestamp = lastEntry.getTimestamp();
        long currentTimestamp = blockHeader.getTimestamp();
        if (currentTimestamp < lastTimestamp) {
            throw new IllegalArgumentException("current timestamp is less than last timestamp");
        }
        return blockHeader.getTimestamp() - lastEntry.getTimestamp();
    }

    private int calcWindowUncleCount(BlockHeader newBlock) {
        if (logEntries.isEmpty()) {
            // This is the first block of the window, we don't have historical data yet
            return newBlock.getUncleCount();
        }

        // The window uncle count is the sum of the uncle counts of the blocks in the window plus
        // the new block
        int windowUncleCount = 0;
        for (LogEntry entry : logEntries) {
            windowUncleCount += entry.getUncleCount();
        }
        return windowUncleCount + newBlock.getUncleCount();
    }

    private double calcUncleRate(BlockHeader newBlock) {
        if (logEntries.isEmpty()) {
            // This is the first block of the window, we don't have historical data yet
            return (double) newBlock.getUncleCount();
        }

        int numSamples = logEntries.size() + 1;
        return (double) calcWindowUncleCount(newBlock) / numSamples;
    }

    private double calcBlockTimeAverage(BlockHeader newBlock) {
        if (logEntries.size() < 2) {
            // We need at least 2 previous blocks to calculate the block time average
            return calcBlockTime(newBlock);
        }

        long firstBlockTimestamp = logEntries.get(0).getTimestamp();
        long lastBlockTimestamp = newBlock.getTimestamp();
        int numSamples = logEntries.size() + 1;
        return (lastBlockTimestamp - firstBlockTimestamp) / (numSamples - 1);
    }

    private void logMetrics() {
        if (logEntries.size() != blockWindowSize) {
            throw new IllegalArgumentException(
                    "log entries size is not equal to the block window size");
        }

        LogEntry lastEntry = logEntries.get(logEntries.size() - 1);
        logger.debug("New difficulty calculation metrics: {}", lastEntry.toString());
        // Write the metrics to the file
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH, true)) {
            writer.write(lastEntry.toCsvRow());
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            logger.error("Error writing metrics to file: {}", e.getMessage());
        }
    }

    private class LogEntry {
        private final long blockNumber;
        private final long timestamp;
        private final long blockTime;
        private final BlockDifficulty difficulty;
        private final BlockDifficulty parentDifficulty;
        private final int uncleCount;
        private final int windowUncleCount;
        private final double windowUncleRate;
        private final double windowAvgBlockTime;

        public LogEntry(long blockNumber, long timestamp, BlockDifficulty difficulty,
                int uncleCount, long blockTime, BlockDifficulty parentDifficulty,
                int windowUncleCount, double windowUncleRate, double windowAvgBlockTime) {
            this.blockNumber = blockNumber;
            this.timestamp = timestamp;
            this.difficulty = difficulty;
            this.uncleCount = uncleCount;
            this.blockTime = blockTime;
            this.parentDifficulty = parentDifficulty;
            this.windowUncleCount = windowUncleCount;
            this.windowUncleRate = windowUncleRate;
            this.windowAvgBlockTime = windowAvgBlockTime;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getUncleCount() {
            return uncleCount;
        }

        @Override
        public String toString() {
            return "LogEntry{"
                + "blockNumber=" + blockNumber
                + ", timestamp=" + timestamp
                + ", blockTime=" + blockTime
                + ", difficulty=" + difficulty
                + ", parentDifficulty=" + parentDifficulty
                + ", uncleCount=" + uncleCount
                + ", windowUncleCount=" + windowUncleCount
                + ", windowUncleRate=" + windowUncleRate
                + ", windowAvgBlockTime=" + windowAvgBlockTime
                + '}';
        }

        public static String getCsvHeader() {
            return "block_number," + "timestamp," + "block_time," + "difficulty,"
                    + "parent_difficulty," + "uncle_count," + "window_uncle_count,"
                    + "window_uncle_rate," + "window_avg_block_time";
        }

        public String toCsvRow() {
            return String.format("%d,%d,%d,%s,%s,%d,%d,%f,%f",
                                 blockNumber,
                                 timestamp,
                                 blockTime,
                                 difficulty.toString(),
                                 parentDifficulty.toString(),
                                 uncleCount,
                                 windowUncleCount,
                                 windowUncleRate,
                                 windowAvgBlockTime);
        }
    }
}
