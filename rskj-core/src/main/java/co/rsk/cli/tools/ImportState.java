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

import co.rsk.RskContext;
import co.rsk.cli.CliToolRskContextAware;
import co.rsk.crypto.Keccak256;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;

/**
 * The entry point for import state CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - file path
 */
public class ImportState extends CliToolRskContextAware {

    public static void main(String[] args) {
        execute(args, MethodHandles.lookup().lookupClass());
    }

    public static void importState(BufferedReader reader, KeyValueDataSource trieDB) throws IOException {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            line = line.trim();
            byte[] value = Hex.decode(line);
            byte[] key = new Keccak256(Keccak256Helper.keccak256(value)).getBytes();

            trieDB.put(key, value);
        }

        trieDB.flush();
        trieDB.close();
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        String databaseDir = ctx.getRskSystemProperties().databaseDir();
        KeyValueDataSource trieDB = LevelDbDataSource.makeDataSource(Paths.get(databaseDir, "unitrie"));

        String filename = args[0];

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            importState(reader, trieDB);
        }
    }
}
