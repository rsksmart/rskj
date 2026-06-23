/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValueFactory;

/**
 * {@link TestSystemProperties} overlaying {@code config/main.conf} consensus rules on the test base config.
 */
public class MainnetMergedConfigProperties extends TestSystemProperties {

    public MainnetMergedConfigProperties() {
        this(MergedNetworkConfig.MAINNET_VETIVER_HEIGHT);
    }

    /**
     * @param vetiverHeight {@code vetiver900} activation height; use
     *                      {@link MergedNetworkConfig#SHORT_CHAIN_VETIVER_HEIGHT} for short-chain tests.
     */
    public MainnetMergedConfigProperties(long vetiverHeight) {
        super(base -> overlayMainnetActivation(base, vetiverHeight));
    }

    private static Config overlayMainnetActivation(Config base, long vetiverHeight) {
        Config blockchain = MergedNetworkConfig.mergedBlockchainConfig(MergedNetworkConfig.MAINNET_RESOURCE);
        Config activationOverlay = ConfigFactory.empty()
                .withValue(
                        "blockchain.config.hardforkActivationHeights.vetiver900",
                        ConfigValueFactory.fromAnyRef(vetiverHeight))
                .withValue(
                        "blockchain.config.consensusRules.rskip351",
                        blockchain.getValue("consensusRules.rskip351"))
                .withValue(
                        "blockchain.config.consensusRules.rskip536",
                        blockchain.getValue("consensusRules.rskip536"))
                .withValue(
                        "blockchain.config.consensusRules.rskip555",
                        blockchain.getValue("consensusRules.rskip555"));
        return activationOverlay.withFallback(base)
                .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true));
    }
}
