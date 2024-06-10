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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.FederationMember.KeyType;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationSupportImplTest {

    private FederationSupport federationSupport;
    private static FederationConstants federationMainnetConstants;
    Federation genesisFederation;
    Federation oldFederation;
    Federation newFederation;
    private FederationStorageProvider storageProvider;
    private FederationSupportBuilder federationSupportBuilder;

    @BeforeEach
    void setUp() {
        federationMainnetConstants = FederationMainNetConstants.getInstance();

        genesisFederation = FederationTestUtils.getGenesisFederation(federationMainnetConstants);

        StorageAccessor storageAccessor = new InMemoryStorage();
        storageProvider = new FederationStorageProviderImpl(storageAccessor);

        federationSupportBuilder = new FederationSupportBuilder();
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .build();
    }

    @Nested
    @Tag("getActiveFederationTestsWithNullFederations")
    class ActiveFederationTestsWithNullFederations {
        @Tag("getActiveFederationTestsWithNullFederations")

        @Test
        void getActiveFederation_returnsGenesisFederation() {
            Federation activeFederation = federationSupport.getActiveFederation();
            assertThat(activeFederation, is(genesisFederation));
        }

        @Test
        void getActiveFederationRedeemScript_beforeRSKIP293_returnsEmpty() {
            assertFalse(federationSupport.getActiveFederationRedeemScript().isPresent());
        }

        @Test
        void getActiveFederationRedeemScript_afterRSKIP293_returnsGenesisFederationRedeemScript() {
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();

            Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();
            assertTrue(activeFederationRedeemScript.isPresent());
            assertThat(activeFederationRedeemScript.get(), is(genesisFederation.getRedeemScript()));
        }

        @Test
        void getActiveFederationAddress_returnsGenesisFederationAddress() {
            Address activeFederationAddress = federationSupport.getActiveFederationAddress();
            assertThat(activeFederationAddress, is(genesisFederation.getAddress()));
        }
    }

    @Nested
    @Tag("getActiveFederationTestsWithOneNullFederation")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithOneNullFederation {
        @Tag("getActiveFederationTestsWithOneNullFederation")

        @BeforeAll
        void setUp() {
            federationMainnetConstants = FederationMainNetConstants.getInstance();

            genesisFederation = FederationTestUtils.getGenesisFederation(federationMainnetConstants);
            // create old and new federations
            List<BtcECKey> newFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
            );
            P2shErpFederationBuilder p2shErpFederationBuilder = new P2shErpFederationBuilder();
            oldFederation = p2shErpFederationBuilder
                .build();
            newFederation = p2shErpFederationBuilder
                .withMembersBtcPublicKeys(newFederationKeys)
                .build();

            // save federations in storage
            StorageAccessor storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            federationSupportBuilder = new FederationSupportBuilder();
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();
        }

        @ParameterizedTest
        @MethodSource("expectedFederation_args")
        void getActiveFederation_returnsExpectedFederation(Federation oldFederation, Federation newFederation, Federation expectedFederation) {
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            Federation activeFederation = federationSupport.getActiveFederation();
            assertThat(activeFederation, is(expectedFederation));
        }
        private Stream<Arguments> expectedFederation_args() {
            return Stream.of(
                Arguments.of(oldFederation, null, genesisFederation),
                Arguments.of(null, newFederation, newFederation)
            );
        }

        @Test
        void getActiveFederationRedeemScript_beforeRSKIP293_returnsEmpty() {
            assertFalse(federationSupport.getActiveFederationRedeemScript().isPresent());
        }

        @ParameterizedTest
        @MethodSource("expectedRedeemScript_args")
        void getActiveFederationRedeemScript_afterRSKIP293_returnsExpectedRedeemScript(Federation oldFederation, Federation newFederation, Script expectedRedeemScript) {
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();

            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();
            assertTrue(activeFederationRedeemScript.isPresent());
            assertThat(activeFederationRedeemScript.get(), is(expectedRedeemScript));
        }
        private Stream<Arguments> expectedRedeemScript_args() {
            return Stream.of(
                Arguments.of(oldFederation, null, genesisFederation.getRedeemScript()),
                Arguments.of(null, newFederation, newFederation.getRedeemScript())
            );
        }

        @ParameterizedTest
        @MethodSource("expectedAddress_args")
        void getActiveFederationAddress_afterRSKIP293_returnsExpectedAddress(Federation oldFederation, Federation newFederation, Address expectedAddress) {
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();

            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            Address activeFederationAddress = federationSupport.getActiveFederationAddress();
            assertThat(activeFederationAddress, is(expectedAddress));
        }
        private Stream<Arguments> expectedAddress_args() {
            return Stream.of(
                Arguments.of(oldFederation, null, genesisFederation.getAddress()),
                Arguments.of(null, newFederation, newFederation.getAddress())
            );
        }
    }

    @Nested
    @Tag("getActiveFederationTestsWithNonNullFederations")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithNonNullFederations {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        long blockNumberFederationActivationHop;
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        long blockNumberFederationActivationFingerroot;
        FederationStorageProvider storageProvider;

        @BeforeAll
        @Tag("getActiveFederationTestsWithNonNullFederations")
        void setUp() {

            long oldFederationCreationBlockNumber = 0;
            long newFederationCreationBlockNumber = 65;

            // get block number activation for hop
            long newFederationActivationAgeHop = FederationMainNetConstants.getInstance().getFederationActivationAge(hopActivations);
            blockNumberFederationActivationHop = newFederationCreationBlockNumber + newFederationActivationAgeHop;

            // get block number activation for fingerroot
            long newFederationActivationAgeFingerroot = FederationMainNetConstants.getInstance().getFederationActivationAge(fingerrootActivations);
            blockNumberFederationActivationFingerroot = newFederationCreationBlockNumber + newFederationActivationAgeFingerroot;

            // create old and new federations
            P2shErpFederationBuilder p2shErpFederationBuilder = new P2shErpFederationBuilder();
            oldFederation = p2shErpFederationBuilder
                .withCreationBlockNumber(oldFederationCreationBlockNumber)
                .build();
            List<BtcECKey> newFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
            );
            newFederation = p2shErpFederationBuilder
                .withMembersBtcPublicKeys(newFederationKeys)
                .withCreationBlockNumber(newFederationCreationBlockNumber)
                .build();

            // save federations in storage
            StorageAccessor storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);
        }

        @ParameterizedTest
        @MethodSource("expectedFederation_args")
        void getActiveFederation_returnsExpectedFederationAccordingToActivationAgeAndActivations(long currentBlock, Federation expectedFederation,
                                                                                                               ActivationConfig.ForBlock activations) {
            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Federation activeFederation = federationSupport.getActiveFederation();
            assertThat(activeFederation, is(expectedFederation));
        }
        private Stream<Arguments> expectedFederation_args() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, oldFederation, hopActivations), // new federation shouldn't be active
                Arguments.of(blockNumberFederationActivationHop + 1, newFederation, hopActivations), // new federation should be active
                Arguments.of(blockNumberFederationActivationFingerroot - 1, oldFederation, fingerrootActivations), // new federation shouldn't be active
                Arguments.of(blockNumberFederationActivationFingerroot + 1, newFederation, fingerrootActivations) // new federation should be active
            );
        }

        @ParameterizedTest
        @MethodSource("expectedRedeemScript_args")
        void getActiveFederationRedeemScript_returnsExpectedRedeemScriptAccordingToActivationAgeAndActivations(long currentBlock, Script expectedRedeemScript,
                                                                                                 ActivationConfig.ForBlock activations) {
            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();
            assertTrue(activeFederationRedeemScript.isPresent());
            assertThat(activeFederationRedeemScript.get(), is(expectedRedeemScript));
        }
        private Stream<Arguments> expectedRedeemScript_args() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, oldFederation.getRedeemScript(), hopActivations), // new federation shouldn't be active
                Arguments.of(blockNumberFederationActivationHop + 1, newFederation.getRedeemScript(), hopActivations), // new federation should be active
                Arguments.of(blockNumberFederationActivationFingerroot - 1, oldFederation.getRedeemScript(), fingerrootActivations), // new federation shouldn't be active
                Arguments.of(blockNumberFederationActivationFingerroot + 1, newFederation.getRedeemScript(), fingerrootActivations) // new federation should be active
            );
        }

        // get active federation address
        @ParameterizedTest
        @MethodSource("expectedAddress_args")
        void getActiveFederationAddress_returnsExpectedAddressAccordingToActivationAgeAndActivations(long currentBlock, Address expectedAddress,
                                                                                                          ActivationConfig.ForBlock activations) {
            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Address activeFederationAddress = federationSupport.getActiveFederationAddress();
            assertThat(activeFederationAddress, is(expectedAddress));
        }
        private Stream<Arguments> expectedAddress_args() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, oldFederation.getAddress(), hopActivations), // new federation shouldn't be active
                Arguments.of(blockNumberFederationActivationHop + 1, newFederation.getAddress(), hopActivations), // new federation should be active
                Arguments.of(blockNumberFederationActivationFingerroot - 1, oldFederation.getAddress(), fingerrootActivations), // new federation shouldn't be active
                Arguments.of(blockNumberFederationActivationFingerroot + 1, newFederation.getAddress(), fingerrootActivations) // new federation should be active
            );
        }
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
