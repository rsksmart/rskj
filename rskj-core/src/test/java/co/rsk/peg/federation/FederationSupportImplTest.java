/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.peg.federation;

import static co.rsk.bitcoinj.script.ScriptBuilder.createRedeemScript;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.FederationMember.KeyType;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.federation.constants.FederationTestNetConstants;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.FederationSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationSupportImplTest {

    private FederationSupport federationSupport;
    private FederationConstants federationMainnetConstants;
    private NetworkParameters networkParameters;
    private FederationStorageProvider storageProvider;
    private FederationSupportBuilder federationSupportBuilder;

    @BeforeEach
    void setUp() {
        StorageAccessor storageAccessor = new InMemoryStorage();
        storageProvider = new FederationStorageProviderImpl(storageAccessor);
        federationMainnetConstants = FederationMainNetConstants.getInstance();
        networkParameters = federationMainnetConstants.getBtcParams();
        federationSupportBuilder = new FederationSupportBuilder();

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .build();
    }

    // getActiveFederation

    @Test
    void getActiveFederation_withNullFederations_returnsGenesisFederation() {
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationMainnetConstants);
        Federation activeFederation = federationSupport.getActiveFederation();

        assertThat(activeFederation, is(genesisFederation));
    }

    @Test
    void getActiveFederation_withOldFederationAndNullNewFederation_returnsGenesisFederation() {
        Federation oldFederation = FederationTestUtils.getErpFederation(networkParameters);
        storageProvider.setOldFederation(oldFederation);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationMainnetConstants);
        Federation activeFederation = federationSupport.getActiveFederation();

        assertThat(activeFederation, is(genesisFederation));
    }

    @Test
    void getActiveFederation_withNewFederationAndNullOldFederation_returnsNewFederation() {
        Federation newFederation = FederationTestUtils.getErpFederation(networkParameters);
        storageProvider.setNewFederation(newFederation);

        Federation activeFederation = federationSupport.getActiveFederation();

        assertThat(activeFederation, is(newFederation));
    }

    private static Stream<Arguments> fedActivationAgeTestArgs() {
        FederationConstants federationMainNetConstants = FederationMainNetConstants.getInstance();
        FederationConstants federationTestNetConstants = FederationTestNetConstants.getInstance();
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        return Stream.of(
            Arguments.of(federationTestNetConstants, hopActivations, false),
            Arguments.of(federationTestNetConstants, hopActivations, true),
            Arguments.of(federationTestNetConstants, fingerrootActivations, false),
            Arguments.of(federationTestNetConstants, fingerrootActivations, true),
            Arguments.of(federationMainNetConstants, hopActivations, false),
            Arguments.of(federationMainNetConstants, hopActivations, true),
            Arguments.of(federationMainNetConstants, fingerrootActivations, false),
            Arguments.of(federationMainNetConstants, fingerrootActivations, true)
        );
    }

    @ParameterizedTest
    @MethodSource("fedActivationAgeTestArgs")
    void getActiveFederation_withOldAndNewFederations_returnsActiveFederationByActivationAge(
        FederationConstants federationConstants,
        ActivationConfig.ForBlock activations,
        boolean newFedExpectedToBeActive
    ) {
        int newFedCreationBlockNumber = 65;
        int oldFedCreationBlockNumber = 0;
        long currentBlockNumber = newFedCreationBlockNumber + federationConstants.getFederationActivationAge(activations);

        if (newFedExpectedToBeActive) {
            currentBlockNumber++;
        } else {
            currentBlockNumber--;
        }

        // Arrange
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(currentBlockNumber);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(storageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        networkParameters = federationConstants.getBtcParams();

        List<BtcECKey> newFederationPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
        );
        Federation newFederation = FederationTestUtils.getErpFederation(
            networkParameters,
            newFederationPublicKeys,
            newFedCreationBlockNumber
        );

        List<BtcECKey> oldFederationPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa10", "fa12", "fa13", "fa14", "fa15", "fa16", "fa17"}, true
        );
        Federation oldFederation = FederationTestUtils.getErpFederation(
            networkParameters,
            oldFederationPublicKeys,
            oldFedCreationBlockNumber
        );

        storageProvider.setNewFederation(newFederation);
        storageProvider.setOldFederation(oldFederation);

        // Act
        Federation activeFederation = federationSupport.getActiveFederation();

        // Assert
        if (newFedExpectedToBeActive){
            assertThat(activeFederation, is(newFederation));
        } else {
            assertThat(activeFederation, is(oldFederation));
        }
    }

    // getActiveFederationRedeemScript

    @Test
    void getActiveFederationRedeemScript_withNullFederations_beforeRSKIP293_returnsEmpty() {
        assertFalse(federationSupport.getActiveFederationRedeemScript().isPresent());
    }

    @Test
    void getActiveFederationRedeemScript_withNullFederations_afterRSKIP293_returnsGenesisFederationRedeemScript() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Script genesisFederationRedeemScript = createRedeemScript(8, federationMainnetConstants.getGenesisFederationPublicKeys());

        Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();

        assertTrue(activeFederationRedeemScript.isPresent());
        assertThat(activeFederationRedeemScript.get(), is(genesisFederationRedeemScript));
    }

    @Test
    void getActiveFederationRedeemScript_withOldFederationAndNullNewFederation_afterRSKIP293_returnsGenesisFederationRedeemScript() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        List<BtcECKey> oldFederationPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
        );
        storageProvider.setOldFederation(FederationTestUtils.getErpFederation(networkParameters, oldFederationPublicKeys));

        Script genesisFederationRedeemScript = createRedeemScript(8, federationMainnetConstants.getGenesisFederationPublicKeys());
        Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();

        assertTrue(activeFederationRedeemScript.isPresent());
        assertThat(activeFederationRedeemScript.get(), is(genesisFederationRedeemScript));
    }

    @Test
    void getActiveFederationRedeemScript_withNewFederationAndNullOldFederation_afterRSKIP293_returnsNewFederationRedeemScript() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        List<BtcECKey> newFederationPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
        );
        storageProvider.setNewFederation(FederationTestUtils.getErpFederation(networkParameters, newFederationPublicKeys));

        Script newFederationRedeemScript = FederationTestUtils.createP2shErpRedeemScriptFromKeys(federationMainnetConstants, newFederationPublicKeys, 5);
        Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();

        assertTrue(activeFederationRedeemScript.isPresent());
        assertThat(activeFederationRedeemScript.get(), is(newFederationRedeemScript));
    }

    @Test
    void getActiveFederationRedeemScript_withNewFederation_afterRSKIP293_returnsGenesisFederationRedeemScript() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Script genesisFederationRedeemScript = createRedeemScript(8, federationMainnetConstants.getGenesisFederationPublicKeys());

        Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();

        assertTrue(activeFederationRedeemScript.isPresent());
        assertThat(activeFederationRedeemScript.get(), is(genesisFederationRedeemScript));
    }

    @Test
    void getActiveFederatorPublicKeyOfType() {
        BtcECKey btcKey0 = BtcECKey.fromPublicOnly(Hex.decode("020000000000000000001111111111111111111122222222222222222222333333"));
        ECKey rskKey0 = new ECKey();
        ECKey mstKey0 = new ECKey();

        BtcECKey btcKey1 = BtcECKey.fromPublicOnly(Hex.decode("020000000000000000001111111111111111111122222222222222222222444444"));
        ECKey rskKey1 = new ECKey();
        ECKey mstKey1 = new ECKey();

        List<FederationMember> members = Arrays.asList(
            new FederationMember(btcKey0, rskKey0, mstKey0),
            new FederationMember(btcKey1, rskKey1, mstKey1)
        );
        FederationArgs federationArgs = new FederationArgs(
            members,
            Instant.ofEpochMilli(123),
            456,
            federationMainnetConstants.getBtcParams()
        );
        Federation theFederation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );
        storageProvider.setNewFederation(theFederation);

        assertArrayEquals(federationSupport.getActiveFederatorBtcPublicKey(0), btcKey0.getPubKey());
        assertArrayEquals(federationSupport.getActiveFederatorBtcPublicKey(1), btcKey1.getPubKey());

        assertArrayEquals(federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.BTC), btcKey0.getPubKey());
        assertArrayEquals(federationSupport.getActiveFederatorPublicKeyOfType(1, KeyType.BTC), btcKey1.getPubKey());

        assertArrayEquals(federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.RSK), rskKey0.getPubKey(true));
        assertArrayEquals(federationSupport.getActiveFederatorPublicKeyOfType(1, KeyType.RSK), rskKey1.getPubKey(true));

        assertArrayEquals(federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST), mstKey0.getPubKey(true));
        assertArrayEquals(federationSupport.getActiveFederatorPublicKeyOfType(1, KeyType.MST), mstKey1.getPubKey(true));

        // Out of bounds
        assertThrows(IndexOutOfBoundsException.class, () -> federationSupport.getActiveFederatorPublicKeyOfType(2, KeyType.BTC));
        assertThrows(IndexOutOfBoundsException.class, () -> federationSupport.getActiveFederatorPublicKeyOfType(-1, KeyType.BTC));
    }
}
