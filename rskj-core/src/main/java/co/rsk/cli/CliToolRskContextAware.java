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
 *
 * Also {@link CliToolRskContextAware} provides a logger instance for derived classes.
 */
public abstract class CliToolRskContextAware {

    private static final Logger logger = LoggerFactory.getLogger("clitool");

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
        execute(args, () -> new RskContext(args), System::exit);
    }

    public void execute(@Nonnull String[] args, @Nonnull Factory<RskContext> contextFactory, @Nonnull NodeStopper stopper) {
        Objects.requireNonNull(args, "args should not be null");
        Objects.requireNonNull(contextFactory, "contextFactory should not be null");

        String cliToolName = getClass().getSimpleName();
        RskContext ctx = contextFactory.create(); // TODO: close context when such capability is ready
        try {
            printInfo("{} started", cliToolName);

            onExecute(args, ctx);

            printInfo("{} finished", cliToolName);
            stopper.stop(0);
        } catch (Exception e) {
            printError("{} failed", cliToolName, e);
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
}
