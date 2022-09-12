/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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

package co.rsk.net.discovery;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

class PeerExplorerCleanerTest {

    private final PeerExplorer peerExplorer = mock(PeerExplorer.class);
    private final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);

    private final PeerExplorerCleaner peerExplorerCleaner = new PeerExplorerCleaner(10L, 20L, peerExplorer, executorService);

    @Test
    void cleanerNotRunning_WhenRun_ThenShouldScheduleUpdateAndCleaning() {
        peerExplorerCleaner.start();

        verify(executorService, times(1)).scheduleAtFixedRate(any(), eq(10L), eq(10L), eq(TimeUnit.MILLISECONDS));
        verify(executorService, times(1)).scheduleAtFixedRate(any(), eq(20L), eq(20L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void cleanerRunning_WhenRun_ThenShouldIgnore() {
        peerExplorerCleaner.start();

        verify(executorService, times(1)).scheduleAtFixedRate(any(), eq(10L), eq(10L), eq(TimeUnit.MILLISECONDS));
        verify(executorService, times(1)).scheduleAtFixedRate(any(), eq(20L), eq(20L), eq(TimeUnit.MILLISECONDS));

        peerExplorerCleaner.start();

        verify(executorService, times(2)).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }

    @Test
    void cleanerRunning_WhenDispose_ThenShouldShutdown() {
        peerExplorerCleaner.start();
        peerExplorerCleaner.dispose();

        verify(executorService, times(1)).shutdown();
    }

    @Test
    void cleanerNotRunning_WhenDispose_ThenShouldBeDisposed() {
        peerExplorerCleaner.dispose();

        verify(executorService, times(1)).shutdown();
    }

    @Test
    void cleanerAlreadyDisposed_WhenDispose_ThenShouldIgnoreSecondCall() {
        peerExplorerCleaner.start();
        peerExplorerCleaner.dispose();

        peerExplorerCleaner.dispose();

        verify(executorService, times(1)).shutdown();
    }
}
