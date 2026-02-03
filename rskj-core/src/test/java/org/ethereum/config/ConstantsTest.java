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
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationTestNetConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstantsTest {

    private static final List<BtcECKey> GENESIS_FEDERATION_PUBLIC_KEYS_TESTNET = Stream.of(
        "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9",
        "02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da",
        "0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09",
        "034844a99cd7028aa319476674cc381df006628be71bc5593b8b5fdb32bb42ef85",
        "032de868a99cf955cc1f9bfe9a869a611ec64707e8ea10709dee52dbfc688626eb" // public key derived from "random" word seed
    ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private final ActivationConfig activationConfig = mock(ActivationConfig.class);
    private final ActivationConfig.ForBlock preRskip297Config = mock(ActivationConfig.ForBlock.class);
    private final ActivationConfig.ForBlock postRskip297Config = mock(ActivationConfig.ForBlock.class);
    private final FederationConstants federationConstants = FederationTestNetConstants.getInstance();

    @BeforeEach
    void setUp() {
        when(preRskip297Config.isActive(ConsensusRule.RSKIP297)).thenReturn(false);
        when(postRskip297Config.isActive(ConsensusRule.RSKIP297)).thenReturn(true);
    }

    @Test
    void devnetWithFederationTest() {
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        assertThat(genesisFederation.hasBtcPublicKey(GENESIS_FEDERATION_PUBLIC_KEYS_TESTNET.get(0)), is(true));
        assertThat(genesisFederation.hasBtcPublicKey(GENESIS_FEDERATION_PUBLIC_KEYS_TESTNET.get(1)), is(true));
        assertThat(genesisFederation.hasBtcPublicKey(GENESIS_FEDERATION_PUBLIC_KEYS_TESTNET.get(2)), is(true));
        assertThat(genesisFederation.hasBtcPublicKey(GENESIS_FEDERATION_PUBLIC_KEYS_TESTNET.get(3)), is(true));
        assertThat(genesisFederation.hasBtcPublicKey(GENESIS_FEDERATION_PUBLIC_KEYS_TESTNET.get(4)), is(false));
    }

    @Test
    void rskip297ActivationTest() {
        assertEquals(300, Constants.regtest().getMaxTimestampsDiffInSecs(preRskip297Config));
        assertEquals(300, Constants.regtest().getMaxTimestampsDiffInSecs(postRskip297Config));

        assertEquals(300, Constants.mainnet().getMaxTimestampsDiffInSecs(preRskip297Config));
        assertEquals(300, Constants.mainnet().getMaxTimestampsDiffInSecs(postRskip297Config));

        assertEquals(300, Constants.testnet(activationConfig).getMaxTimestampsDiffInSecs(preRskip297Config));
        assertEquals(7200, Constants.testnet(activationConfig).getMaxTimestampsDiffInSecs(postRskip297Config));
    }

    @Test
    void maxInitcodeSizeTest() {
        //given
        long maxInitCodeSizeExpected = 49152L;
        //when
        long maxInitCodeSize = Constants.getMaxInitCodeSize();
        //then
        assertEquals(maxInitCodeSizeExpected, maxInitCodeSize);
    }

    @Test
    void minSequentialSetGasLimitTest() {
        // Assert the values of MIN_SEQUENTIAL_SET_GAS_LIMIT
        assertEquals(7_500_000L, Constants.mainnet().getMinSequentialSetGasLimit());
        assertEquals(7_500_000L, Constants.testnet(mock(ActivationConfig.class)).getMinSequentialSetGasLimit());
        assertEquals(7_500_000L, Constants.testnet2(mock(ActivationConfig.class)).getMinSequentialSetGasLimit());
        assertEquals(7_500_000L, Constants.devnetWithFederation().getMinSequentialSetGasLimit());
        assertEquals(6_800_000L, Constants.regtest().getMinSequentialSetGasLimit());
    }
}
