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
package co.rsk.cli;

import co.rsk.RskContext;
import co.rsk.util.Factory;
import co.rsk.util.NodeStopper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * An abstract class for cli tools that need {@link RskContext}. Lifecycle of the  {@link RskContext} instance
 * is being managed by {@link CliToolRskContextAware}.
 * <p>
 * Also {@link CliToolRskContextAware} provides a logger instance for derived classes.
 */
public abstract class CliToolRskContextAware {

    private static final Logger logger = LoggerFactory.getLogger("clitool");
    protected RskContext ctx;
    protected NodeStopper stopper;

    protected static CliToolRskContextAware create(@Nonnull Class<?> cliToolClass) {
        Objects.requireNonNull(cliToolClass, "cliToolClass should not be null");

        if (!CliToolRskContextAware.class.isAssignableFrom(cliToolClass)) {
            throw new IllegalArgumentException("cliToolClass");
        }

        try {
            return (CliToolRskContextAware) cliToolClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            printError("Cannot create an instance of {} cli tool", cliToolClass.getSimpleName(), e);
            throw new IllegalArgumentException("Cannot create instance of " + cliToolClass.getName(), e);
        }
    }

    public void execute(@Nonnull String[] args) {
        execute(args, System::exit);
    }

    public void execute(@Nonnull String[] args, @Nonnull NodeStopper nodeStopper) {
        execute(args, () -> new RskContext(args, true), nodeStopper);
    }

    public void execute(@Nonnull String[] args, @Nonnull Factory<RskContext> contextFactory, @Nonnull NodeStopper nodeStopper) {
        Objects.requireNonNull(args, "args should not be null");
        Objects.requireNonNull(contextFactory, "contextFactory should not be null");
        Objects.requireNonNull(nodeStopper, "stopper should not be null");

        String cliToolName = getClass().getSimpleName();

        // Ignore contextFactory if ctx was set previously
        // in order to allow compatibility between picocli
        // and old cli tool version
        if (this.ctx == null) {
            this.ctx = contextFactory.create();
        }
        // Ignore nodeStopper if stopper was set previously
        // in order to allow compatibility between picocli
        // and old cli tool version
        if (this.stopper == null) {
            this.stopper = nodeStopper;
        }

        try {
            printInfo("{} started", cliToolName);

            onExecute(args, ctx);

            printInfo("{} finished", cliToolName);

            ctx.close();

            stopper.stop(0);
        } catch (Exception e) {
            printError("{} failed", cliToolName, e);

            if (ctx != null) {
                ctx.close();
            }

            stopper.stop(1);
        }
    }

    protected static void printInfo(String format, Object... arguments) {
        logger.info(format, arguments);
    }

    protected static void printError(String format, Object... arguments) {
        logger.error(format, arguments);
    }

    /**
     * Actual execution should be implemented in this method in derived classes.
     */
    protected abstract void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception;

    /**
     * Handy interface for printing strings.
     */
    @FunctionalInterface
    public interface Printer {
        void println(String x);
    }
}