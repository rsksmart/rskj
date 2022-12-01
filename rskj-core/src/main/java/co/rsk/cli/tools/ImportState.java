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
package co.rsk.cli.tools;

import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;

/**
 * The entry point for import state CLI tool
 * This is an experimental/unsupported tool
 * <p>
 * Required cli args:
 * - args[0] - file path
 */
@CommandLine.Command(name = "import-state", mixinStandardHelpOptions = true, version = "import-state 1.0",
        description = "Imports state from a file")
public class ImportState extends PicoCliToolRskContextAware {
    @CommandLine.Option(names = {"-f", "--file"}, description = "Path to a file to import state from", required = true)
    private String filePath;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        RskSystemProperties rskSystemProperties = ctx.getRskSystemProperties();
        String databaseDir = rskSystemProperties.databaseDir();

        DbKind defaultDbKind = rskSystemProperties.databaseKind();
        KeyValueDataSource trieDB = KeyValueDataSource.makeDataSource(Paths.get(databaseDir, "unitrie"), rskSystemProperties.getStatesDataSource(), defaultDbKind);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            importState(reader, trieDB);
        }

        trieDB.flush();
        trieDB.close();

        return 0;
    }

    private void importState(BufferedReader reader, KeyValueDataSource trieDB) throws IOException {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            line = line.trim();
            byte[] value = Hex.decode(line);
            byte[] key = new Keccak256(Keccak256Helper.keccak256(value)).getBytes();

            trieDB.put(key, value);
        }
    }
}
