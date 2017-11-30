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
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.PendingTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.validator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.*;

import static java.util.Arrays.asList;

@Configuration
@ComponentScan(
        basePackages = { "org.ethereum", "co.rsk" },
        excludeFilters = @ComponentScan.Filter(NoAutoscan.class))
public class CommonConfig {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Autowired
    SystemProperties config = RskSystemProperties.CONFIG;

    @Bean
    public Repository repository() {
        KeyValueDataSource ds = makeDataSource("state");
        KeyValueDataSource detailsDS = makeDataSource("details");

        return new RepositoryImpl(new TrieStoreImpl(ds), detailsDS);
    }

    private KeyValueDataSource makeDataSource(String name) {
        KeyValueDataSource ds = new LevelDbDataSource(name);
        ds.init();
        return ds;
    }

    @Bean
    public Set<PendingTransaction> wireTransactions() {
        String storage = "LevelDB";
        try {
            storage = "In memory";
            return Collections.synchronizedSet(new HashSet<PendingTransaction>());
        } finally {
            logger.info("{} 'wireTransactions' storage created.", storage);
        }
    }

    @Bean
    public List<Transaction> pendingStateTransactions() {
        return Collections.synchronizedList(new ArrayList<Transaction>());
    }

    @Bean
    public ParentBlockHeaderValidator parentHeaderValidator() {

        List<DependentBlockHeaderRule> rules = new ArrayList<>(asList(
                new ParentNumberRule(),
                new DifficultyRule(),
                new ParentGasLimitRule(RskSystemProperties.CONFIG.getBlockchainConfig().
                        getCommonConstants().getGasLimitBoundDivisor())));

        return new ParentBlockHeaderValidator(rules);
    }

}
