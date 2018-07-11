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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        // The code used to be associated to an account and it belonged to the account state
        // We need to move it into its own database
        // For this we copy all the code that appears on every account state into the right entry on the code database
        // It is mandatory for a "state" database to exist and not a "code" one
        // The db abstraction will need to check if this migration is actually needed, we just assumed that it is here
        boolean shouldMigrateCode = new File(databaseDir + "/state").exists() && !new File(databaseDir + "/code").exists();

        KeyValueDataSource ds = makeDataSource(config, "state");
        KeyValueDataSource detailsDS = makeDataSource(config, "details");
        KeyValueDataSource codeDS = makeDataSource(config, "code");

        final RepositoryImpl repository = new RepositoryImpl(config, new TrieStoreImpl(ds), detailsDS, codeDS);

        if (shouldMigrateCode) {
            repository.migrateCode();
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
