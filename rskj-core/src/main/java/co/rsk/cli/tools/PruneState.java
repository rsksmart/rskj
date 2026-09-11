/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
import co.rsk.util.BlockStateHandler;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.util.FileUtil;
import picocli.CommandLine;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@CommandLine.Command(name = "prune-state", mixinStandardHelpOptions = true, version = "prune-state 1.0",
        description = "The entry point for pruning blockchain state")
public class PruneState extends PicoCliToolRskContextAware {

    @CommandLine.Option(names = {"-b", "--numOfBlocks"}, description = "Number of top blocks to preserve from pruning (128 by default)")
    private Long numOfBlocks;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        if (numOfBlocks == null) {
            numOfBlocks = 128L; // by default
        } else if (numOfBlocks <= 0) {
            throw new IllegalArgumentException("'numOfBlocks' cannot be less than or equal to zero");
        }

        RskSystemProperties systemProperties = ctx.getRskSystemProperties();
        Path databasePath = Paths.get(systemProperties.databaseDir());
        Path trieStorePath = databasePath.resolve("unitrie");
        if (!trieStorePath.toFile().exists()) {
            throw new IllegalStateException("'unitrie' db folder not found");
        }

        Path tmpTrieStorePath = databasePath.resolve("tmp_unitrie");
        BlockStateHandler stateHandler = new BlockStateHandler(ctx.getBlockchain(), ctx.getTrieStore(), ctx.getStateRootHandler());

        try {
            KeyValueDataSource destDataSource = KeyValueDataSourceUtils.makeDataSource(tmpTrieStorePath, systemProperties.databaseKind());
            try {
                stateHandler.copyState(numOfBlocks, destDataSource);
            } finally {
                destDataSource.close();
            }
        } catch (Exception e) {
            printError("State copying failed", e);

            FileUtil.recursiveDelete(tmpTrieStorePath.toString());

            throw e;
        } finally {
            ctx.close();
        }

        FileUtil.recursiveDelete(trieStorePath.toString());
        Files.move(tmpTrieStorePath, trieStorePath);

        return 0;
    }
}
