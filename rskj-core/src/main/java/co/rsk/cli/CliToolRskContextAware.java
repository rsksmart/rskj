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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * An abstract class for cli tools that need {@link RskContext}. Lifecycle of the  {@link RskContext} instance
 * is being managed by {@link CliToolRskContextAware}.
 *
 * Also {@link CliToolRskContextAware} provides a logger instance for derived classes.
 */
public abstract class CliToolRskContextAware {

    protected static final Logger logger = LoggerFactory.getLogger("clitool");

    protected static void execute(@Nonnull String[] args, @Nonnull Class<?> cliToolClass) {
        if (!CliToolRskContextAware.class.isAssignableFrom(cliToolClass)) {
            throw new IllegalArgumentException("cliToolClass");
        }

        RskContext ctx = new RskContext(args); // TODO: close context when such capability is ready
        try {
            CliToolRskContextAware cliTool = (CliToolRskContextAware) cliToolClass.newInstance();
            String cliToolName = cliToolClass.getSimpleName();

            logger.info("{} started", cliToolName);

            cliTool.onExecute(args, ctx);

            logger.info("{} finished", cliToolName);
            System.exit(0);
        } catch (InstantiationException | IllegalAccessException e) {
            logger.error("Cannot create an instance of {} cli tool", cliToolClass.getSimpleName(), e);
            System.exit(2);
        } catch (Exception e) {
            logger.error("{} failed", cliToolClass.getSimpleName(), e);
            System.exit(1);
        }
    }

    /**
     * Actual execution should be implemented in this method in derived classes.
     */
    protected abstract void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception;
}
