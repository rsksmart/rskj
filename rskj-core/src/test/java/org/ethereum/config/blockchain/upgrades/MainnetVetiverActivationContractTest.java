/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.ethereum.config.blockchain.upgrades;

import co.rsk.config.MergedNetworkConfig;
import org.junit.jupiter.api.Test;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP351;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP535;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP555;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Activation boundary checks for merged production network configs. */
class MainnetVetiverActivationContractTest {

    @Test
    void mainnet_beforeVetiver_rskip555Inactive_headerV0() {
        ActivationConfig config = MergedNetworkConfig.activationConfig(MergedNetworkConfig.MAINNET_RESOURCE);
        long before = MergedNetworkConfig.MAINNET_BEFORE_VETIVER_HEIGHT;

        assertFalse(config.isActive(RSKIP555, before));
        assertFalse(config.isActive(RSKIP351, before));
        assertFalse(config.isActive(RSKIP535, before));
        assertEquals((byte) 0x00, config.getHeaderVersion(before));
    }

    @Test
    void mainnet_atVetiver_rskip351And555Active_headerV3() {
        ActivationConfig config = MergedNetworkConfig.activationConfig(MergedNetworkConfig.MAINNET_RESOURCE);
        long at = MergedNetworkConfig.MAINNET_VETIVER_HEIGHT;

        assertTrue(config.isActive(RSKIP351, at));
        assertTrue(config.isActive(RSKIP555, at));
        assertEquals((byte) 0x03, config.getHeaderVersion(at));
    }

    @Test
    void testnet_beforeVetiver_rskip555Inactive_headerV1() {
        ActivationConfig config = MergedNetworkConfig.activationConfig(MergedNetworkConfig.TESTNET_RESOURCE);
        long before = MergedNetworkConfig.TESTNET_BEFORE_VETIVER_HEIGHT;

        assertFalse(config.isActive(RSKIP555, before));
        assertTrue(config.isActive(RSKIP351, before), "testnet RSKIP351 follows reed810");
        assertFalse(config.isActive(RSKIP535, before));
        assertEquals((byte) 0x01, config.getHeaderVersion(before));
    }

    @Test
    void testnet_atVetiver_rskip555Active_headerV3() {
        ActivationConfig config = MergedNetworkConfig.activationConfig(MergedNetworkConfig.TESTNET_RESOURCE);
        long at = MergedNetworkConfig.TESTNET_VETIVER_HEIGHT;

        assertTrue(config.isActive(RSKIP555, at));
        assertEquals((byte) 0x03, config.getHeaderVersion(at));
    }

    @Test
    void testnet2_earlyRskip555_requiresRskip351ForHeaderV3() {
        ActivationConfig config = MergedNetworkConfig.activationConfig(MergedNetworkConfig.TESTNET2_RESOURCE);

        assertTrue(config.isActive(RSKIP351, 1L));
        assertTrue(config.isActive(RSKIP555, 1L));
        assertEquals((byte) 0x03, config.getHeaderVersion(1L));
    }
}
