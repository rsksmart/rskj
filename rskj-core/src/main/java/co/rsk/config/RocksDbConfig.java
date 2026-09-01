/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
package co.rsk.config;

/**
 * RocksDB tuning, resolved from {@code database.rocksdb.*} in the node configuration.
 *
 * <p>These are ordinary configuration properties like any other: they live in {@code rsk.conf},
 * and because the config loader layers {@link com.typesafe.config.ConfigFactory#systemProperties()}
 * over the files, any of them can still be overridden on the command line with
 * {@code -Ddatabase.rocksdb.<key>=<value>} without a separate mechanism.
 */
public class RocksDbConfig {

    private final long writeBufferSizeBytes;
    private final int maxWriteBufferNumber;
    private final int minWriteBufferNumberToMerge;
    private final long blockCacheSizeBytes;
    private final int maxBackgroundJobs;
    private final int bloomBitsPerKey;
    private final int blockSizeBytes;
    private final boolean paranoidChecks;
    private final boolean disableWal;
    private final boolean verifyChecksums;
    private final String compression;

    @SuppressWarnings("java:S107") // one parameter per tunable; a builder would add noise, not clarity
    public RocksDbConfig(long writeBufferSizeMb, int maxWriteBufferNumber, int minWriteBufferNumberToMerge,
                         long blockCacheSizeMb, int maxBackgroundJobs, int bloomBitsPerKey, int blockSizeKb,
                         boolean paranoidChecks, boolean disableWal, boolean verifyChecksums, String compression) {
        this.writeBufferSizeBytes = writeBufferSizeMb * 1024L * 1024L;
        this.maxWriteBufferNumber = maxWriteBufferNumber;
        this.minWriteBufferNumberToMerge = minWriteBufferNumberToMerge;
        this.blockCacheSizeBytes = blockCacheSizeMb * 1024L * 1024L;
        this.maxBackgroundJobs = maxBackgroundJobs;
        this.bloomBitsPerKey = bloomBitsPerKey;
        this.blockSizeBytes = blockSizeKb * 1024;
        this.paranoidChecks = paranoidChecks;
        this.disableWal = disableWal;
        this.verifyChecksums = verifyChecksums;
        this.compression = compression;
    }

    /**
     * The values a node gets when nothing is configured. These mirror the defaults in
     * {@code reference.conf}, and exist for the code paths that build a datasource without a full
     * node configuration - CLI tools and tests.
     */
    public static RocksDbConfig defaults() {
        return new RocksDbConfig(128L, 4, 2, 1024L, 8, 10, 16, false, false, true, "none");
    }

    public long getWriteBufferSizeBytes() { return writeBufferSizeBytes; }
    public int getMaxWriteBufferNumber() { return maxWriteBufferNumber; }
    public int getMinWriteBufferNumberToMerge() { return minWriteBufferNumberToMerge; }
    public long getBlockCacheSizeBytes() { return blockCacheSizeBytes; }
    public int getMaxBackgroundJobs() { return maxBackgroundJobs; }
    public int getBloomBitsPerKey() { return bloomBitsPerKey; }
    public int getBlockSizeBytes() { return blockSizeBytes; }
    public boolean isParanoidChecks() { return paranoidChecks; }
    public boolean isDisableWal() { return disableWal; }
    public boolean isVerifyChecksums() { return verifyChecksums; }
    public String getCompression() { return compression; }
}
