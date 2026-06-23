/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.config.MainnetMergedConfigProperties;
import co.rsk.config.MergedNetworkConfig;
import co.rsk.core.bc.BtcBlockFacCache;
import co.rsk.core.bc.FacBlockHashesCache;
import co.rsk.test.mine.ForkBalanceMainnetMiningTestSupport;
import co.rsk.test.mine.ForkBalanceMainnetMiningTestSupport.ChainFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static co.rsk.test.mine.ForkBalanceMainnetMiningTestSupport.MAINNET_BTC;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Verifies header v3 work-build precomputes {@code forkBalanceProof} under mainnet activation rules.
 */
class ForkBalanceMainnetWorkBuildTest {

    @Test
    void buildBlockToMine_mainnetMergedConfig_precomputesForkBalanceProofFromCachedBtcTip() {
        MainnetMergedConfigProperties props = spy(new MainnetMergedConfigProperties(
                MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT));
        when(props.forkBalanceBtcCacheConfig()).thenReturn(
                new ForkBalanceBtcCacheConfig(12, "", 0, 5_000));

        FacBlockHashesCache facBlockHashesCache = new FacBlockHashesCache();
        BtcBlockFacCache btcBlockFacCache = new BtcBlockFacCache(
                props.forkBalanceBtcCacheConfig(),
                MAINNET_BTC,
                null,
                props.getActivationConfig());
        ForkBalanceMainnetMiningTestSupport.seedBtcMiningTip(btcBlockFacCache, props.getActivationConfig());

        ChainFixture chainFixture = ForkBalanceMainnetMiningTestSupport.buildChainFixture(
                props, facBlockHashesCache, btcBlockFacCache, false);
        MinerServerImpl minerServer = ForkBalanceMainnetMiningTestSupport.createMinerServer(
                props, chainFixture.chainBuilder, chainFixture.chain, MAINNET_BTC,
                facBlockHashesCache, btcBlockFacCache);

        try {
            minerServer.buildBlockToMine(false);
            var built = minerServer.getLatestBuiltBlockForTesting();
            Assertions.assertNotNull(built);
            Assertions.assertEquals(MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT, built.getNumber());
            Assertions.assertEquals((byte) 0x03, built.getHeader().getVersion());

            byte[] proof = built.getHeader().getForkBalanceProof();
            Assertions.assertNotNull(proof);
            Assertions.assertFalse(ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(proof));

            MinerWork work = minerServer.getWork();
            BtcBlock childTemplate = minerServer.buildBitcoinMergedMiningBlock(MAINNET_BTC, work);
            Assertions.assertFalse(childTemplate.getPrevBlockHash().equals(co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH));
            Assertions.assertEquals(
                    childTemplate.getPrevBlockHash().toString(),
                    work.getBtcParentBlockHash());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void buildBlockToMine_mainnetMergedConfig_withoutBtcParent_skipsWorkUpdate() {
        MainnetMergedConfigProperties props = spy(new MainnetMergedConfigProperties(
                MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT));
        when(props.forkBalanceBtcCacheConfig()).thenReturn(
                new ForkBalanceBtcCacheConfig(12, "", 0, 5_000));

        BtcBlockFacCache emptyBtcCache = new BtcBlockFacCache(
                props.forkBalanceBtcCacheConfig(),
                MAINNET_BTC,
                null,
                props.getActivationConfig());
        FacBlockHashesCache facBlockHashesCache = new FacBlockHashesCache();
        ChainFixture chainFixture = ForkBalanceMainnetMiningTestSupport.buildChainFixture(
                props, facBlockHashesCache, emptyBtcCache, false);

        MinerServerImpl minerServer = ForkBalanceMainnetMiningTestSupport.createMinerServer(
                props, chainFixture.chainBuilder, chainFixture.chain, MAINNET_BTC,
                facBlockHashesCache, emptyBtcCache);

        try {
            Assertions.assertNull(minerServer.getLatestBuiltBlockForTesting());
            minerServer.buildBlockToMine(false);
            Assertions.assertNull(minerServer.getLatestBuiltBlockForTesting());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    void buildBlockToMine_mainnetMergedConfig_withoutBtcBlockFacCache_skipsWorkUpdate() {
        MainnetMergedConfigProperties props = spy(new MainnetMergedConfigProperties(
                MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT));
        when(props.forkBalanceBtcCacheConfig()).thenReturn(
                new ForkBalanceBtcCacheConfig(12, "", 0, 5_000));

        FacBlockHashesCache facBlockHashesCache = new FacBlockHashesCache();
        ChainFixture chainFixture = ForkBalanceMainnetMiningTestSupport.buildChainFixture(
                props, facBlockHashesCache, null, false);

        MinerServerImpl minerServer = ForkBalanceMainnetMiningTestSupport.createMinerServer(
                props, chainFixture.chainBuilder, chainFixture.chain, MAINNET_BTC,
                facBlockHashesCache, null);

        try {
            minerServer.buildBlockToMine(false);
            Assertions.assertNull(minerServer.getLatestBuiltBlockForTesting());
        } finally {
            minerServer.stop();
        }
    }
}
