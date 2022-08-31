/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.bitcoinj.core.BtcECKey;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ConstantsTest {

    private static final List<BtcECKey> TEST_FED_KEYS = Arrays.asList(
            BtcECKey.fromPublicOnly(BtcECKey.fromPrivate(HashUtil.keccak256("federator1".getBytes(StandardCharsets.UTF_8))).getPubKey()),
            BtcECKey.fromPublicOnly(BtcECKey.fromPrivate(HashUtil.keccak256("federator2".getBytes(StandardCharsets.UTF_8))).getPubKey()),
            BtcECKey.fromPublicOnly(BtcECKey.fromPrivate(HashUtil.keccak256("federator3".getBytes(StandardCharsets.UTF_8))).getPubKey()),
            BtcECKey.fromPublicOnly(BtcECKey.fromPrivate(HashUtil.keccak256("federator4".getBytes(StandardCharsets.UTF_8))).getPubKey())
    );

    private final ActivationConfig activationConfig = mock(ActivationConfig.class);
    private final ActivationConfig.ForBlock preRskip297Config = mock(ActivationConfig.ForBlock.class);
    private final ActivationConfig.ForBlock postRskip297Config = mock(ActivationConfig.ForBlock.class);

    @Before
    public void setUp() {
        when(preRskip297Config.isActive(eq(ConsensusRule.RSKIP297))).thenReturn(false);
        when(postRskip297Config.isActive(eq(ConsensusRule.RSKIP297))).thenReturn(true);
    }

    @Test
    public void devnetWithFederationTest() {
        Constants constants = Constants.devnetWithFederation(TEST_FED_KEYS.subList(0, 3));
        assertThat(constants.getBridgeConstants().getGenesisFederation().hasBtcPublicKey(TEST_FED_KEYS.get(0)), is(true));
        assertThat(constants.getBridgeConstants().getGenesisFederation().hasBtcPublicKey(TEST_FED_KEYS.get(1)), is(true));
        assertThat(constants.getBridgeConstants().getGenesisFederation().hasBtcPublicKey(TEST_FED_KEYS.get(2)), is(true));
        assertThat(constants.getBridgeConstants().getGenesisFederation().hasBtcPublicKey(TEST_FED_KEYS.get(3)), is(false));
    }

    @Test
    public void rskip297ActivationTest() {
        assertEquals(300, Constants.regtest().getMaxTimestampsDiffInSecs(preRskip297Config));
        assertEquals(300, Constants.regtest().getMaxTimestampsDiffInSecs(postRskip297Config));

        assertEquals(300, Constants.mainnet().getMaxTimestampsDiffInSecs(preRskip297Config));
        assertEquals(300, Constants.mainnet().getMaxTimestampsDiffInSecs(postRskip297Config));

        assertEquals(300, Constants.testnet(activationConfig).getMaxTimestampsDiffInSecs(preRskip297Config));
        assertEquals(7200, Constants.testnet(activationConfig).getMaxTimestampsDiffInSecs(postRskip297Config));
    }
}
