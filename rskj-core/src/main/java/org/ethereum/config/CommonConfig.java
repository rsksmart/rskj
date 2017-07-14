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
import org.ethereum.datasource.mapdb.MapDBFactory;
import org.ethereum.validator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;

import java.util.*;

import static java.util.Arrays.asList;

@Configuration
@ComponentScan(
        basePackages = { "org.ethereum", "co.rsk" },
        excludeFilters = @ComponentScan.Filter(NoAutoscan.class))
public class CommonConfig {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Autowired
    private MapDBFactory mapDBFactory;

    @Autowired
    SystemProperties config = SystemProperties.CONFIG;

    @Bean
    public Repository repository() {
        KeyValueDataSource ds = makeDataSource("state");
        KeyValueDataSource detailsDS = makeDataSource("details");

        return new RepositoryImpl(new TrieStoreImpl(ds), detailsDS);
    }

    private KeyValueDataSource makeDataSource(String name) {
        KeyValueDataSource ds = keyValueDataSource();
        ds.setName(name);
        ds.init();

        return ds;
    }

    @Bean
    @Scope("prototype")
    public KeyValueDataSource keyValueDataSource() {
        String dataSource = config.getKeyValueDataSource();
        try {
            dataSource = "leveldb";
            return new LevelDbDataSource();
        } finally {
            logger.info(dataSource + " key-value data source created.");
        }
    }

    @Bean
    public Set<PendingTransaction> wireTransactions() {
        String storage = "LevelDB";
        try {
            storage = "In memory";
            return Collections.synchronizedSet(new HashSet<PendingTransaction>());
        } finally {
            logger.info(storage + " 'wireTransactions' storage created.");
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
