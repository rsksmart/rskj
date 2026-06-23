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
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

/**
 * Loads merged network + {@code reference.conf} overlays used in production.
 */
public final class MergedNetworkConfig {

    public static final String MAINNET_RESOURCE = "config/main.conf";
    public static final String TESTNET_RESOURCE = "config/testnet.conf";
    public static final String TESTNET2_RESOURCE = "config/testnet2.conf";

    public static final long MAINNET_VETIVER_HEIGHT = 8_804_200L;
    public static final long MAINNET_BEFORE_VETIVER_HEIGHT = MAINNET_VETIVER_HEIGHT - 1L;

    public static final long TESTNET_VETIVER_HEIGHT = 7_604_200L;
    public static final long TESTNET_BEFORE_VETIVER_HEIGHT = TESTNET_VETIVER_HEIGHT - 1L;

    /** Vetiver height shifted for short-chain behavioral tests (parent at {@code height - 1}). */
    public static final long SHORT_CHAIN_VETIVER_HEIGHT = 2L;

    private MergedNetworkConfig() {
    }

    public static Config unifiedNetworkConfig(String networkResourcePath) {
        Config reference = ConfigFactory.parseResources("reference.conf");
        Config network = ConfigFactory.parseResources(networkResourcePath);
        ConfigResolveOptions resolveOptions = ConfigResolveOptions.defaults().setAllowUnresolved(true);
        return network.withFallback(reference).resolve(resolveOptions);
    }

    public static Config mergedBlockchainConfig(String networkResourcePath) {
        return unifiedNetworkConfig(networkResourcePath).getConfig("blockchain.config");
    }

    public static ActivationConfig activationConfig(String networkResourcePath) {
        return ActivationConfig.read(mergedBlockchainConfig(networkResourcePath));
    }

    public static ActivationConfig activationConfigWithVetiverHeight(
            String networkResourcePath,
            long vetiverHeight) {
        Config blockchain = mergedBlockchainConfig(networkResourcePath)
                .withValue(
                        "hardforkActivationHeights.vetiver900",
                        ConfigValueFactory.fromAnyRef(vetiverHeight));
        return ActivationConfig.read(blockchain);
    }
}
