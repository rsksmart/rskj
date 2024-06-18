/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.util.cli;

import java.io.IOException;
import java.nio.file.Path;

public class ConnectBlocksCommandLine extends RskjCommandLineBase {

    public ConnectBlocksCommandLine(String filePath) {
        this(filePath, null);
    }

    public ConnectBlocksCommandLine(String filePath, Path dbDir) {
        super("co.rsk.cli.tools.ConnectBlocks",
                new String[]{ "-Dlogging.dir=./build/tmp" },
                makeArgs(filePath, dbDir));
    }

    @Override
    public Process executeCommand() throws IOException, InterruptedException {
        return super.executeCommand(10);
    }

    private static String[] makeArgs(String filePath, Path dbDir) {
        if (dbDir == null) {
            return new String[]{"-f", filePath, "--regtest"};
        }
        return new String[]{"-f", filePath, "-Xdatabase.dir=" + dbDir, "--regtest"};
    }
}
