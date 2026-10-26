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
import co.rsk.cli.exceptions.PicocliBadResultException;
import picocli.CommandLine;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.concurrent.Callable;

public abstract class PicoCliToolRskContextAware extends CliToolRskContextAware implements Callable<Integer> {

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) {
        this.ctx = ctx;

        CommandLine commandLine = new CommandLine(this).setUnmatchedArgumentsAllowed(true);
        rejectArgumentsUnknownToToolAndNode(commandLine.parseArgs(args).unmatched());

        int result = commandLine.execute(args);

        if (result != 0) {
            throw new PicocliBadResultException(result);
        }
    }

    /** Arguments the tool does not declare must be node arguments (network flags, -X overrides, ...). */
    private static void rejectArgumentsUnknownToToolAndNode(List<String> unmatched) {
        if (!unmatched.isEmpty()) {
            new CommandLine(new RskCli()).parseArgs(unmatched.toArray(new String[0]));
        }
    }
}
