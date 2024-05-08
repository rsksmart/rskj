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
package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederationSupportTest {

    private FederationSupport federationSupport;
    private BridgeConstants bridgeConstants;
    private BridgeStorageProvider provider;
    private Block executionBlock;
    private ActivationConfig.ForBlock activations;

    @BeforeEach
    void setUp() {
        provider = mock(BridgeStorageProvider.class);
        bridgeConstants = mock(BridgeConstants.class);
        executionBlock = mock(Block.class);
        activations = mock(ActivationConfig.ForBlock.class);

        federationSupport = new FederationSupport(
            bridgeConstants,
            provider,
            executionBlock,
            activations
        );
    }

    @Test
    void whenNewFederationIsNullThenActiveFederationIsGenesisFederation() {
        Federation genesisFederation = getNewFakeFederation(0);
        when(provider.getNewFederation())
            .thenReturn(null);
        when(bridgeConstants.getGenesisFederation())
            .thenReturn(genesisFederation);

        assertThat(federationSupport.getActiveFederation(), is(genesisFederation));
    }

    @Test
    void whenOldFederationIsNullThenActiveFederationIsNewFederation() {
        Federation newFederation = getNewFakeFederation(100);
        when(provider.getNewFederation())
            .thenReturn(newFederation);
        when(provider.getOldFederation())
            .thenReturn(null);


        assertThat(federationSupport.getActiveFederation(), is(newFederation));
    }

    private static Stream<Arguments> fedActivationAgeTestArgs() {
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        return Stream.of(
            Arguments.of(bridgeTestNetConstants, hopActivations, false),
            Arguments.of(bridgeTestNetConstants, hopActivations, true),
            Arguments.of(bridgeTestNetConstants, fingerrootActivations, false),
            Arguments.of(bridgeTestNetConstants, fingerrootActivations, true),
            Arguments.of(bridgeMainnetConstants, hopActivations, false),
            Arguments.of(bridgeMainnetConstants, hopActivations, true),
            Arguments.of(bridgeMainnetConstants, fingerrootActivations, false),
            Arguments.of(bridgeMainnetConstants, fingerrootActivations, true)
        );
    }


    @ParameterizedTest
    @MethodSource("fedActivationAgeTestArgs")
    void whenOldAndNewFederationArePresentReturnActiveFederationByActivationAge(
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations,
        boolean newFedExpectedToBeActive
    ) {
        int newFedCreationBlockNumber = 65;
        int oldFedCreationBlockNumber = 0;
        long currentBlockNumber = newFedCreationBlockNumber + bridgeConstants.getFederationActivationAge(activations);

        if (newFedExpectedToBeActive) {
            currentBlockNumber++;
        } else {
            currentBlockNumber--;
        }

        // Arrange
        Federation newFederation = getNewFakeFederation(newFedCreationBlockNumber);
        Federation oldFederation = getNewFakeFederation(oldFedCreationBlockNumber);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(newFederation);
        when(provider.getOldFederation()).thenReturn(oldFederation);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(currentBlockNumber);

        FederationSupport federationSupport = new FederationSupport(
            bridgeConstants,
            provider,
            executionBlock,
            activations
        );

        // Act
        Federation activeFederation = federationSupport.getActiveFederation();

        // Assert
        if (newFedExpectedToBeActive){
            assertThat(activeFederation, is(newFederation));
        } else {
            assertThat(activeFederation, is(oldFederation));
        }
    }

    @Test
    void getFederatorPublicKeys() {
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
        FederationArgs federationArgs = new FederationArgs(members, Instant.ofEpochMilli(123), 456,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Federation theFederation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );
        when(provider.getNewFederation()).thenReturn(theFederation);

        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC), btcKey0.getPubKey()));
        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(1, FederationMember.KeyType.BTC), btcKey1.getPubKey()));

        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorBtcPublicKey(0), btcKey0.getPubKey()));
        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorBtcPublicKey(1), btcKey1.getPubKey()));

        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK), rskKey0.getPubKey(true)));
        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(1, FederationMember.KeyType.RSK), rskKey1.getPubKey(true)));

        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(0, FederationMember.KeyType.MST), mstKey0.getPubKey(true)));
        Assertions.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(1, FederationMember.KeyType.MST), mstKey1.getPubKey(true)));
    }

    @Test
    void getMemberPublicKeyOfType() {
        BtcECKey btcKey0 = new BtcECKey();
        ECKey rskKey0 = new ECKey();
        ECKey mstKey0 = new ECKey();

        BtcECKey btcKey1 = new BtcECKey();
        ECKey rskKey1 = new ECKey();
        ECKey mstKey1 = new ECKey();

        List<FederationMember> members = Arrays.asList(
            new FederationMember(btcKey0, rskKey0, mstKey0),
            new FederationMember(btcKey1, rskKey1, mstKey1)
        );

        Assertions.assertTrue(Arrays.equals(federationSupport.getMemberPublicKeyOfType(members, 0, FederationMember.KeyType.BTC, "a prefix"), btcKey0.getPubKey()));
        Assertions.assertTrue(Arrays.equals(federationSupport.getMemberPublicKeyOfType(members, 1, FederationMember.KeyType.BTC, "a prefix"), btcKey1.getPubKey()));

        Assertions.assertTrue(Arrays.equals(federationSupport.getMemberPublicKeyOfType(members, 0, FederationMember.KeyType.RSK, "a prefix"), rskKey0.getPubKey(true)));
        Assertions.assertTrue(Arrays.equals(federationSupport.getMemberPublicKeyOfType(members, 1, FederationMember.KeyType.RSK, "a prefix"), rskKey1.getPubKey(true)));

        Assertions.assertTrue(Arrays.equals(federationSupport.getMemberPublicKeyOfType(members, 0, FederationMember.KeyType.MST, "a prefix"), mstKey0.getPubKey(true)));
        Assertions.assertTrue(Arrays.equals(federationSupport.getMemberPublicKeyOfType(members, 1, FederationMember.KeyType.MST, "a prefix"), mstKey1.getPubKey(true)));
    }

    @Test
    void getMemberPublicKeyOfType_OutOfBounds() {
        List<FederationMember> members = Arrays.asList(
            new FederationMember(new BtcECKey(), new ECKey(), new ECKey()),
            new FederationMember(new BtcECKey(), new ECKey(), new ECKey())
        );

        try {
            federationSupport.getMemberPublicKeyOfType(members,2, FederationMember.KeyType.BTC, "a prefix");
            Assertions.fail();
        } catch (IndexOutOfBoundsException e) {
            Assertions.assertTrue(e.getMessage().startsWith("a prefix"));
        }

        try {
            federationSupport.getMemberPublicKeyOfType(members,-1, FederationMember.KeyType.MST, "another prefix");
            Assertions.fail();
        } catch (IndexOutOfBoundsException e) {
            Assertions.assertTrue(e.getMessage().startsWith("another prefix"));
        }
    }

    private Federation getNewFakeFederation(long creationBlockNumber) {
        List<BtcECKey> keys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fed1", "fed2"},
            true
        );
        List<FederationMember> members = FederationTestUtils.getFederationMembersWithBtcKeys(keys);
        FederationArgs federationArgs = new FederationArgs(members,
            Instant.ofEpochMilli(123),
            creationBlockNumber,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        return FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );
    }
}
