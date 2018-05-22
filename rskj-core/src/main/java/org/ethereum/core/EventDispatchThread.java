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

package org.ethereum.core;

import co.rsk.panic.PanicProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The class intended to serve as an 'Event Bus' where all EthereumJ events are
 * dispatched asynchronously from component to component or from components to
 * the user event handlers.
 *
 * This made for decoupling different components which are intended to work
 * asynchronously and to avoid complex synchronisation and deadlocks between them
 *
 * Created by Anton Nashatyrev on 29.12.2015.
 */
public class EventDispatchThread {
    private static final Logger logger = LoggerFactory.getLogger("blockchain");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EventDispatchThread() {
        // utility class can't be instantiated
    }

    public static void invokeLater(final Runnable r) {
        executor.submit(() -> {
            try {
                r.run();
            } catch (Exception e) {
                logger.error("EDT task exception", e);
                panicProcessor.panic("thread", String.format("EDT task exception %s", e.getMessage()));
            }
        });
    }
}
