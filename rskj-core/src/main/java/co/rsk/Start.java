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
package co.rsk;

import co.rsk.util.PreflightChecksUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * The entrypoint for the RSK full node
 */
public class Start {

    private static final Logger logger = LoggerFactory.getLogger("start");

    public static void main(String[] args) {
        setUpThread(Thread.currentThread());

        RskContext ctx = null;
        try {
            ctx = new RskContext(args);
            runNode(Runtime.getRuntime(), new PreflightChecksUtils(ctx), ctx);
        } catch (Exception e) {
            logger.error("The RSK node main thread failed, closing program", e);

            if (ctx != null) {
                ctx.close();
            }

            System.exit(1);
        }
    }

    static void runNode(@Nonnull Runtime runtime, @Nonnull PreflightChecksUtils preflightChecks, @Nonnull RskContext ctx) throws Exception {
        preflightChecks.runChecks();

        NodeRunner runner = ctx.getNodeRunner();

        runtime.addShutdownHook(new Thread(ctx::close, "stopper"));

        runner.run();
    }

    static void setUpThread(@Nonnull Thread thread) {
        thread.setName("main");
    }
}
