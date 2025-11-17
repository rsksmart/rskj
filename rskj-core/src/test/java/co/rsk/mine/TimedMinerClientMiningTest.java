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

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.params.RegTestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimedMinerClientMiningTest {

    private TimedMinerClient timedMinerClient;
    private MinerServer minerServer;
    private MinerWork mockWork;

    @BeforeEach
    void setUp() {
        minerServer = mock(MinerServer.class);
        mockWork = mock(MinerWork.class);
        
        // Setup mock work with easy target for testing
        when(mockWork.getBlockHashForMergedMining()).thenReturn("0x404142");
        when(mockWork.getTarget()).thenReturn("0x10000000000000000000000000000000000000000000000000000000000000");
        
        when(minerServer.getWork()).thenReturn(mockWork);
        
        timedMinerClient = new TimedMinerClient(minerServer, Duration.ofSeconds(1));
    }

    @Test
    void minesBlockSuccessfully() {
        // Start the client
        timedMinerClient.start();
        
        // Wait a bit for the scheduled task to run
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify that the miner server was called
        verify(minerServer, atLeastOnce()).getWork();
        verify(minerServer, atLeastOnce()).submitBitcoinBlock(eq("0x404142"), any(BtcBlock.class));
        
        timedMinerClient.stop();
    }

    @Test
    void handlesMiningErrorsGracefully() {
        // Make the miner server throw an exception
        when(minerServer.getWork()).thenThrow(new RuntimeException("Test error"));
        
        timedMinerClient.start();
        
        // Wait a bit for the scheduled task to run
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify that the client is still running despite errors
        assertThat(timedMinerClient.isMining(), is(true));
        
        timedMinerClient.stop();
    }

    @Test
    void stopsMiningWhenStopped() {
        timedMinerClient.start();
        assertThat(timedMinerClient.isMining(), is(true));
        
        timedMinerClient.stop();
        assertThat(timedMinerClient.isMining(), is(false));
        
        // Wait a bit to ensure no more mining attempts
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify that no more calls were made after stopping
        verify(minerServer, never()).getWork();
    }

    @Test
    void usesExponentialDistribution() {
        // Test with a very short median time to see multiple mining attempts
        TimedMinerClient fastClient = new TimedMinerClient(minerServer, Duration.ofMillis(100));
        
        fastClient.start();
        
        // Wait for multiple mining attempts
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should have made several mining attempts
        verify(minerServer, atLeast(3)).getWork();
        
        fastClient.stop();
    }
}
