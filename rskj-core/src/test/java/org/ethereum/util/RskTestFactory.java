package org.ethereum.util;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Genesis;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.datasource.HashMapDB;

import java.nio.file.Path;

/**
 * @deprecated use {@link RskTestContext} instead which builds the same graph of dependencies than the productive code
 */
@Deprecated
public class RskTestFactory extends RskTestContext {
    private final TestSystemProperties config;

    public RskTestFactory(Path dbPath) {
        this(dbPath, new TestSystemProperties());
    }

    public RskTestFactory(Path dbPath, TestSystemProperties config) {
        super(dbPath);
        this.config = config;
    }

    @Override
    public RskSystemProperties buildRskSystemProperties() {
        return config;
    }

    @Override
    public BlockValidator buildBlockValidator() {
        return new DummyBlockValidator();
    }

    @Override
    protected GenesisLoader buildGenesisLoader() {
        return () -> {
            Genesis genesis = new BlockGenerator().getGenesisBlock();
            getStateRootHandler().register(genesis.getHeader(), new Trie());
            return genesis;
        };
    }

    @Override
    public SyncConfiguration buildSyncConfiguration() {
        return SyncConfiguration.IMMEDIATE_FOR_TESTING;
    }

    public static Genesis getGenesisInstance(RskSystemProperties config) {
        boolean useRskip92Encoding = config.getActivationConfig().isActive(ConsensusRule.RSKIP92, 0L);
        boolean isRskip126Enabled = config.getActivationConfig().isActive(ConsensusRule.RSKIP126, 0L);
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new TestGenesisLoader(trieStore, config.genesisInfo(), config.getNetworkConstants().getInitialNonce(), false, useRskip92Encoding, isRskip126Enabled).load();
    }
}
