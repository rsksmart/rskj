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

package org.ethereum.config;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.util.FileUtil;
import org.ethereum.validator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.System.getProperty;
import static java.util.Arrays.asList;

@Configuration
@ComponentScan(
        basePackages = { "org.ethereum", "co.rsk" },
        excludeFilters = @ComponentScan.Filter(NoAutoscan.class))
public class CommonConfig {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Bean
    public Repository repository(RskSystemProperties config) throws IOException {
        String databaseDir = config.databaseDir();
        if (config.databaseReset()){
            FileUtil.recursiveDelete(databaseDir);
            logger.info("Database reset done");
        }

        KeyValueDataSource ds = makeDataSource(config, "state");
        KeyValueDataSource detailsDS = makeDataSource(config, "details");
        KeyValueDataSource codeDS = makeDataSource(config, "code");
        KeyValueDataSource version = makeDataSource(config, "version");

        RepositoryImpl repository = new RepositoryImpl(config, new TrieStoreImpl(ds), detailsDS, codeDS, version);


        // The code used to be associated to an account and it belonged to the account state
        // We need to move it into its own database
        // For this we copy all the code that appears on every account state into the right entry on the code database
        // The db abstraction will need to check if this migration is actually needed
        //TODO(donequis): should move to a nice place where healthy checks take place
        if (repository.shouldMigrateDb()) {
            logger.info("Code db is out of date. Migrating [...]");
            LevelDbDataSource temp = null;
            boolean success = false;
            try {
                temp = (LevelDbDataSource) makeDataSource(config, "temp");
                success = repository.migrateCode(temp);
            } finally {
                if (temp != null) temp.close();
            }

            if (success) {
                detailsDS.close();
                Path src;

                //TODO: repeated code in leveldb datasource
                if (Paths.get(config.databaseDir()).isAbsolute()) {
                    src = Paths.get(config.databaseDir(), "temp");
                } else {
                    src = Paths.get(getProperty("user.dir"), config.databaseDir(), "temp");
                }

                FileUtil.recursiveDelete(src.resolveSibling("details").toAbsolutePath().toString());
                Files.move(src, src.resolveSibling("details"), StandardCopyOption.REPLACE_EXISTING);
                repository.setVersion();

                detailsDS = makeDataSource(config, "details");
                repository = new RepositoryImpl(config, new TrieStoreImpl(ds), detailsDS, codeDS, version);
            }
        } else {
            logger.debug("No need to migrate code db");
        }



        return repository;
    }

    private KeyValueDataSource makeDataSource(RskSystemProperties config, String name) {
        KeyValueDataSource ds = new LevelDbDataSource(config, name);
        ds.init();
        return ds;
    }

    @Bean
    public List<Transaction> transactionPoolTransactions() {
        return Collections.synchronizedList(new ArrayList<Transaction>());
    }

    @Bean
    public ParentBlockHeaderValidator parentHeaderValidator(RskSystemProperties config, DifficultyCalculator difficultyCalculator) {

        List<DependentBlockHeaderRule> rules = new ArrayList<>(asList(
                new ParentNumberRule(),
                new DifficultyRule(difficultyCalculator),
                new ParentGasLimitRule(config.getBlockchainConfig().
                        getCommonConstants().getGasLimitBoundDivisor())));

        return new ParentBlockHeaderValidator(rules);
    }

}
