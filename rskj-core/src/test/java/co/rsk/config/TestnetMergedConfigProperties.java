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
 * {@link TestSystemProperties} overlaying {@code config/testnet.conf} consensus rules on the test base config.
 */
public class TestnetMergedConfigProperties extends TestSystemProperties {

    public TestnetMergedConfigProperties() {
        this(MergedNetworkConfig.TESTNET_VETIVER_HEIGHT);
    }

    public TestnetMergedConfigProperties(long vetiverHeight) {
        super(base -> overlayTestnetActivation(base, vetiverHeight));
    }

    private static Config overlayTestnetActivation(Config base, long vetiverHeight) {
        Config blockchain = MergedNetworkConfig.mergedBlockchainConfig(MergedNetworkConfig.TESTNET_RESOURCE);
        Config activationOverlay = ConfigFactory.empty()
                .withValue(
                        "blockchain.config.hardforkActivationHeights.vetiver900",
                        ConfigValueFactory.fromAnyRef(vetiverHeight))
                .withValue(
                        "blockchain.config.consensusRules.rskip535",
                        blockchain.getValue("consensusRules.rskip535"))
                .withValue(
                        "blockchain.config.consensusRules.rskip555",
                        blockchain.getValue("consensusRules.rskip555"));
        return activationOverlay.withFallback(base)
                .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true));
    }
}
