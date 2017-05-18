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

package org.ethereum.sync;

/**
 * Manages sync measurements
 *
 * @author Mikhail Kalinin
 * @since 20.08.2015
 */
public class SyncStatistics {
    private long updatedAt;
    private long blocksCount;
    private long headersCount;
    private int headerBunchesCount;

    // RSK new fields
    private long statusCount;
    private long getBlocksCount;

    public SyncStatistics() {
        reset();
    }

    public void reset() {
        updatedAt = System.currentTimeMillis();
        blocksCount = 0;
        headersCount = 0;
        headerBunchesCount = 0;
        statusCount = 0;
        getBlocksCount = 0;
    }

    // RSK new method
    public void getBlock() {
        getBlocksCount++;
        fixCommon(1);
    }

    // RSK new method
    public void addStatus() {
        statusCount++;
        fixCommon(1);
    }

    public void addBlocks(long cnt) {
        blocksCount += cnt;
        fixCommon(cnt);
    }

    public void addHeaders(long cnt) {
        headerBunchesCount++;
        headersCount += cnt;
        fixCommon(cnt);
    }

    private void fixCommon(long cnt) {
        updatedAt = System.currentTimeMillis();
    }

    public long getBlocksCount() {
        return blocksCount;
    }

    public long getHeadersCount() {
        return headersCount;
    }

    public long secondsSinceLastUpdate() {
        return (System.currentTimeMillis() - updatedAt) / 1000;
    }

    public int getHeaderBunchesCount() {
        return headerBunchesCount;
    }
}
