/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR ANY PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.mine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimedMinerClientTest {

    private TimedMinerClient timedMinerClient;
    private MinerServer minerServer;

    @BeforeEach
    void setUp() {
        minerServer = mock(MinerServer.class);
        timedMinerClient = new TimedMinerClient(minerServer, Duration.ofSeconds(10), false);
    }

    @Test
    void byDefaultIsDisabled() {
        assertThat(timedMinerClient.isMining(), is(false));
    }

    @Test
    void startsMining() {
        timedMinerClient.start();
        assertThat(timedMinerClient.isMining(), is(true));
        timedMinerClient.stop();
    }

    @Test
    void stopsMining() {
        timedMinerClient.start();
        timedMinerClient.stop();
        assertThat(timedMinerClient.isMining(), is(false));
    }

    @Test
    void canStartAndStopMultipleTimes() {
        timedMinerClient.start();
        assertThat(timedMinerClient.isMining(), is(true));
        
        timedMinerClient.stop();
        assertThat(timedMinerClient.isMining(), is(false));
        
        timedMinerClient.start();
        assertThat(timedMinerClient.isMining(), is(true));
        
        timedMinerClient.stop();
        assertThat(timedMinerClient.isMining(), is(false));
    }

    @Test
    void constructorWithCustomMedianTime() {
        Duration customMedian = Duration.ofMinutes(5);
        TimedMinerClient customClient = new TimedMinerClient(minerServer, customMedian, false);
        
        assertThat(customClient.isMining(), is(false));
        customClient.start();
        assertThat(customClient.isMining(), is(true));
        customClient.stop();
    }
}
