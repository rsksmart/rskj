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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TimedMinerClientSimpleTest {

    @Mock
    private MinerServer minerServer;

    @Test
    void testConstructor() {
        Duration medianTime = Duration.ofSeconds(10);
        TimedMinerClient client = new TimedMinerClient(minerServer, medianTime, false);
        
        assertNotNull(client);
        assertFalse(client.isMining());
    }

    @Test
    void testStartAndStop() {
        Duration medianTime = Duration.ofSeconds(1);
        TimedMinerClient client = new TimedMinerClient(minerServer, medianTime, false);
        
        // Initially not mining
        assertFalse(client.isMining());
        
        // Start mining
        client.start();
        assertTrue(client.isMining());
        
        // Stop mining
        client.stop();
        assertFalse(client.isMining());
    }

    @Test
    void testMultipleStartStopCycles() {
        Duration medianTime = Duration.ofSeconds(1);
        TimedMinerClient client = new TimedMinerClient(minerServer, medianTime, false);
        
        // First cycle
        client.start();
        assertTrue(client.isMining());
        client.stop();
        assertFalse(client.isMining());
        
        // Second cycle
        client.start();
        assertTrue(client.isMining());
        client.stop();
        assertFalse(client.isMining());
    }

    @Test
    void testConstructorWithDifferentDurations() {
        Duration shortDuration = Duration.ofMillis(100);
        Duration longDuration = Duration.ofMinutes(5);
        
        TimedMinerClient shortClient = new TimedMinerClient(minerServer, shortDuration, false);
        TimedMinerClient longClient = new TimedMinerClient(minerServer, longDuration, false);
        
        assertNotNull(shortClient);
        assertNotNull(longClient);
        
        shortClient.start();
        longClient.start();
        
        assertTrue(shortClient.isMining());
        assertTrue(longClient.isMining());
        
        shortClient.stop();
        longClient.stop();
    }
}
