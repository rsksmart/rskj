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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by mario on 22/02/17.
 */
public class PeerExplorerCleaner {

    private PeerExplorer peerExplorer;
    private ScheduledExecutorService updateTask;
    private long updatePeriod;
    private long cleanPeriod;
    private boolean running = false;

    public PeerExplorerCleaner(PeerExplorer peerExplorer, long updatePeriod, long cleanPeriod) {
        this.peerExplorer = peerExplorer;
        this.updatePeriod = updatePeriod;
        this.cleanPeriod = cleanPeriod;
        // it should stay on a single thread since there are two tasks that could interfere with each other running here
        this.updateTask = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PeerExplorerCleaner"));
    }

    public void run() {
        if (!running) {
            this.startUpdateTask();
            running = true;
        }
    }

    private void startUpdateTask() {
        updateTask.scheduleAtFixedRate(() -> peerExplorer.clean(), cleanPeriod, cleanPeriod, TimeUnit.MILLISECONDS);
        updateTask.scheduleAtFixedRate(() -> peerExplorer.update(), updatePeriod, updatePeriod, TimeUnit.MILLISECONDS);
    }


}
