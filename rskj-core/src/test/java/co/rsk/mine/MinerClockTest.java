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

import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MinerClockTest {

    private Clock clock;

    @BeforeEach
    public void setUp() {
        clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    }

    @Test
    public void timestampForChildIsParentTimestampIfRegtest() {
        MinerClock minerClock = new MinerClock(true, clock);

        BlockHeader parent = mockBlockHeaderWithTimestamp(54L);

        assertEquals(
                54L,
                minerClock.calculateTimestampForChild(parent)
        );
    }

    @Test
    public void timestampForChildIsClockTimeIfNotRegtest() {
        MinerClock minerClock = new MinerClock(false, clock);

        BlockHeader parent = mockBlockHeaderWithTimestamp(54L);

        assertEquals(
                clock.instant().getEpochSecond(),
                minerClock.calculateTimestampForChild(parent)
        );
    }

    @Test
    public void timestampForChildIsTimestampPlusOneIfNotRegtest() {
        MinerClock minerClock = new MinerClock(false, clock);

        BlockHeader parent = mockBlockHeaderWithTimestamp(clock.instant().getEpochSecond());

        assertEquals(
                clock.instant().getEpochSecond() + 1,
                minerClock.calculateTimestampForChild(parent)
        );
    }

    @Test
    public void adjustTimeIfRegtest() {
        MinerClock minerClock = new MinerClock(true, clock);

        BlockHeader parent = mockBlockHeaderWithTimestamp(33L);

        minerClock.increaseTime(5392L);

        assertEquals(
                33L + 5392L,
                minerClock.calculateTimestampForChild(parent)
        );
    }

    @Test
    public void adjustTimeIfNotRegtest() {
        MinerClock minerClock = new MinerClock(false, clock);

        BlockHeader parent = mockBlockHeaderWithTimestamp(33L);

        minerClock.increaseTime(5392L);

        assertEquals(
                clock.instant().getEpochSecond() + 5392L,
                minerClock.calculateTimestampForChild(parent)
        );
    }

    @Test
    public void clearTimeIncrease() {
        MinerClock minerClock = new MinerClock(true, clock);

        BlockHeader parent = mockBlockHeaderWithTimestamp(33L);

        minerClock.increaseTime(5392L);
        minerClock.clearIncreaseTime();

        assertEquals(
                33L,
                minerClock.calculateTimestampForChild(parent)
        );
    }

    BlockHeader mockBlockHeaderWithTimestamp(long timestamp) {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTimestamp()).thenReturn(timestamp);

        return header;
    }
}
