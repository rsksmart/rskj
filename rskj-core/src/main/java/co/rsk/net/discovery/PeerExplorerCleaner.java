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

package co.rsk.net.discovery;

import co.rsk.util.ExecState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by mario on 22/02/17.
 */
class PeerExplorerCleaner {

    private static final Logger logger = LoggerFactory.getLogger(PeerExplorerCleaner.class);

    private final long updatePeriod;
    private final long cleanPeriod;

    private final PeerExplorer peerExplorer;
    private final ScheduledExecutorService executor;

    private ExecState state = ExecState.CREATED;

    PeerExplorerCleaner(long updatePeriod, long cleanPeriod, PeerExplorer peerExplorer) {
        this(updatePeriod, cleanPeriod, peerExplorer, makeDefaultScheduledExecutorService());
    }

    PeerExplorerCleaner(long updatePeriod, long cleanPeriod, PeerExplorer peerExplorer, ScheduledExecutorService executor) {
        this.updatePeriod = updatePeriod;
        this.cleanPeriod = cleanPeriod;
        this.peerExplorer = Objects.requireNonNull(peerExplorer);
        this.executor = Objects.requireNonNull(executor);
    }

    synchronized void start() {
        if (state != ExecState.CREATED) {
            logger.warn("Cannot start peer explorer cleaner as current state is {}", state);
            return;
        }
        state = ExecState.RUNNING;

        startCleanAndUpdateTasks();
    }

    synchronized void dispose() {
        if (state == ExecState.FINISHED) {
            logger.warn("Cannot dispose peer explorer cleaner as current state is {}", state);
            return;
        }
        state = ExecState.FINISHED;

        executor.shutdown();
    }

    private void startCleanAndUpdateTasks() {
        executor.scheduleAtFixedRate(peerExplorer::clean, cleanPeriod, cleanPeriod, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(peerExplorer::update, updatePeriod, updatePeriod, TimeUnit.MILLISECONDS);
    }

    private static ScheduledExecutorService makeDefaultScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PeerExplorerCleaner"));
    }
}
