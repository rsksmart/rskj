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

import co.rsk.config.ConfigUtils;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.config.MainnetMergedConfigProperties;
import co.rsk.config.MergedNetworkConfig;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.facade.Ethereum;
import org.ethereum.util.BuildInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class MinerServerBtcRpcRequirementTest {

    @Test
    void regtestParams_doesNotRequireBtcRpcForV3() {
        MinerServerImpl server = minerServerWithSyntheticActivation(
                Constants.regtest().getBridgeConstants().getBtcParams(), 1L);
        Assertions.assertDoesNotThrow(server::requireBitcoinRpcForV3MiningIfApplicable);
    }

    @Test
    void mainnetMergedConfig_requiresBtcRpcWhenNextHeightIsProductionVetiver() {
        MinerServerImpl server = minerServerWithMergedMainnetConfig(
                MergedNetworkConfig.MAINNET_BEFORE_VETIVER_HEIGHT, "");
        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class,
                server::requireBitcoinRpcForV3MiningIfApplicable);
        Assertions.assertTrue(ex.getMessage().contains("miner.forkBalance.btcRpc.url"));
        Assertions.assertTrue(ex.getMessage().contains("bitcoind"));
        Assertions.assertTrue(ex.getMessage().contains(String.valueOf(MergedNetworkConfig.MAINNET_VETIVER_HEIGHT)));
    }

    @Test
    void mainnetMergedConfig_doesNotRequireBtcRpcBeforeProductionVetiver() {
        MinerServerImpl server = minerServerWithMergedMainnetConfig(
                MergedNetworkConfig.MAINNET_BEFORE_VETIVER_HEIGHT - 1, "");
        Assertions.assertDoesNotThrow(server::requireBitcoinRpcForV3MiningIfApplicable);
    }

    @Test
    void mainnetMergedConfig_allowsStartWhenBtcRpcConfiguredAtProductionVetiver() {
        MainnetMergedConfigProperties config = spy(new MainnetMergedConfigProperties());
        when(config.forkBalanceBtcCacheConfig()).thenReturn(
                new ForkBalanceBtcCacheConfig(12, "http://127.0.0.1:8332", 0, 5_000));

        MinerServerImpl server = minerServerWithMergedMainnetConfig(
                config,
                MergedNetworkConfig.MAINNET_BEFORE_VETIVER_HEIGHT,
                "http://127.0.0.1:8332");
        Assertions.assertDoesNotThrow(server::requireBitcoinRpcForV3MiningIfApplicable);
    }

    @Test
    void mainnetMergedConfig_shortChain_requiresBtcRpcAtShiftedVetiver() {
        MainnetMergedConfigProperties config = spy(
                new MainnetMergedConfigProperties(MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT));
        when(config.forkBalanceBtcCacheConfig()).thenReturn(new ForkBalanceBtcCacheConfig(12, "", 0, 5_000));

        MiningMainchainView mainchainView = mock(MiningMainchainView.class);
        BlockHeader parent = new BlockHeaderBuilder(config.getActivationConfig())
                .setNumber(MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT - 1)
                .build();
        when(mainchainView.get()).thenReturn(List.of(parent));

        MinerServerImpl server = new MinerServerImpl(
                config,
                mock(Ethereum.class),
                mainchainView,
                null,
                new ProofOfWorkRule(config),
                null,
                new MinerClock(true, java.time.Clock.systemUTC()),
                null,
                new BuildInfo("test", "test"),
                ConfigUtils.getDefaultMiningConfig(),
                SubmissionRateLimitHandler.ofMiningConfig(ConfigUtils.getDefaultMiningConfig()),
                null,
                null,
                Constants.mainnet().getBridgeConstants().getBtcParams());

        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class,
                server::requireBitcoinRpcForV3MiningIfApplicable);
        Assertions.assertTrue(ex.getMessage().contains("miner.forkBalance.btcRpc.url"));
    }

    @Test
    void syntheticActivation_requiresBtcRpcWhenNextBlockIsV3() {
        MinerServerImpl server = minerServerWithSyntheticActivation(
                Constants.mainnet().getBridgeConstants().getBtcParams(), 1L);
        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class,
                server::requireBitcoinRpcForV3MiningIfApplicable);
        Assertions.assertTrue(ex.getMessage().contains("miner.forkBalance.btcRpc.url"));
    }

    private static MinerServerImpl minerServerWithMergedMainnetConfig(long parentNumber, String btcRpcUrl) {
        return minerServerWithMergedMainnetConfig(
                spy(new MainnetMergedConfigProperties()), parentNumber, btcRpcUrl);
    }

    private static MinerServerImpl minerServerWithMergedMainnetConfig(
            MainnetMergedConfigProperties config,
            long parentNumber,
            String btcRpcUrl) {
        when(config.forkBalanceBtcCacheConfig()).thenReturn(
                new ForkBalanceBtcCacheConfig(12, btcRpcUrl, 0, 5_000));

        MiningMainchainView mainchainView = mock(MiningMainchainView.class);
        BlockHeader parent = new BlockHeaderBuilder(config.getActivationConfig())
                .setNumber(parentNumber)
                .build();
        when(mainchainView.get()).thenReturn(List.of(parent));

        return new MinerServerImpl(
                config,
                mock(Ethereum.class),
                mainchainView,
                null,
                new ProofOfWorkRule(config),
                null,
                new MinerClock(true, java.time.Clock.systemUTC()),
                null,
                new BuildInfo("test", "test"),
                ConfigUtils.getDefaultMiningConfig(),
                SubmissionRateLimitHandler.ofMiningConfig(ConfigUtils.getDefaultMiningConfig()),
                null,
                null,
                Constants.mainnet().getBridgeConstants().getBtcParams());
    }

    private static MinerServerImpl minerServerWithSyntheticActivation(
            co.rsk.bitcoinj.core.NetworkParameters btcParams,
            long parentNumber) {
        SyntheticV3Properties config = spy(new SyntheticV3Properties());
        when(config.forkBalanceBtcCacheConfig()).thenReturn(new ForkBalanceBtcCacheConfig(12, "", 0, 5_000));

        MiningMainchainView mainchainView = mock(MiningMainchainView.class);
        BlockHeader parent = new BlockHeaderBuilder(ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144))
                .setNumber(parentNumber)
                .build();
        when(mainchainView.get()).thenReturn(Collections.singletonList(parent));

        return new MinerServerImpl(
                config,
                mock(Ethereum.class),
                mainchainView,
                null,
                new ProofOfWorkRule(config),
                null,
                new MinerClock(true, java.time.Clock.systemUTC()),
                null,
                new BuildInfo("test", "test"),
                ConfigUtils.getDefaultMiningConfig(),
                SubmissionRateLimitHandler.ofMiningConfig(ConfigUtils.getDefaultMiningConfig()),
                null,
                null,
                btcParams);
    }

    private static final class SyntheticV3Properties extends MainnetMergedConfigProperties {
        @Override
        public ActivationConfig getActivationConfig() {
            return ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);
        }
    }
}
