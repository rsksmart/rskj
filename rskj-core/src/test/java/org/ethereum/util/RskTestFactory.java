package org.ethereum.util;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Genesis;
import org.ethereum.core.genesis.GenesisLoader;

/**
 * @deprecated use {@link RskTestContext} instead which builds the same graph of dependencies than the productive code
 */
@Deprecated
public class RskTestFactory extends RskTestContext {
    private final TestSystemProperties config;

    public RskTestFactory() {
        this(new TestSystemProperties());
    }

    public RskTestFactory(TestSystemProperties config) {
        super(new String[0]);
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
    public Genesis buildGenesis() {
        return new BlockGenerator().getGenesisBlock();
    }

    @Override
    public SyncConfiguration buildSyncConfiguration() {
        return SyncConfiguration.IMMEDIATE_FOR_TESTING;
    }

    public static Genesis getGenesisInstance(RskSystemProperties config) {
        boolean useRskip92Encoding = config.getBlockchainConfig().getConfigForBlock(0).isRskip92();
        return GenesisLoader.loadGenesis(config.genesisInfo(), config.getNetworkConstants().getInitialNonce(), false, useRskip92Encoding);
    }
}
