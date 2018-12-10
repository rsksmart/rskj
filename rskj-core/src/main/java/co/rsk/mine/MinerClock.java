/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.mine;

import org.ethereum.core.Block;

import java.time.Clock;

public class MinerClock {
    private final boolean isRegtest;
    private final Clock clock;

    private long timeAdjustment;

    public MinerClock(boolean isRegtest, Clock clock) {
        this.isRegtest = isRegtest;
        this.clock = clock;
    }

    public long calculateTimestampForChild(Block parent) {
        long previousTimestamp = parent.getTimestamp();
        if (isRegtest) {
            return previousTimestamp + timeAdjustment;
        }

        long ret = clock.instant().plusSeconds(timeAdjustment).getEpochSecond();
        return Long.max(ret, previousTimestamp + 1);
    }

    public long increaseTime(long seconds) {
        if (seconds <= 0) {
            return timeAdjustment;
        }

        timeAdjustment += seconds;
        return timeAdjustment;
    }

    public void clearIncreaseTime() {
        timeAdjustment = 0;
    }
}
