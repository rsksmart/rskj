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

    private static final FederationConstants federationMainnetConstants = FederationMainNetConstants.getInstance();
    private final Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationMainnetConstants);
    private Federation oldFederation;
    private Federation newFederation;
    private FederationStorageProvider storageProvider;
    private final FederationSupportBuilder federationSupportBuilder = new FederationSupportBuilder();
    private FederationSupport federationSupport;

    @BeforeEach
    void setUp() {
        StorageAccessor storageAccessor = new InMemoryStorage();
        storageProvider = new FederationStorageProviderImpl(storageAccessor);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .build();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("null federations")
    class ActiveFederationTestsWithNullFederations {
        @BeforeAll
        void setUp() {
            StorageAccessor storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();
        }

        @Test
        @Tag("getActiveFederation")
        void getActiveFederation_returnsGenesisFederation() {
            Federation activeFederation = federationSupport.getActiveFederation();
            assertThat(activeFederation, is(genesisFederation));
        }

        @Test
        @Tag("getActiveFederationRedeemScript")
        void getActiveFederationRedeemScript_beforeRSKIP293_returnsEmpty() {
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();

            assertFalse(federationSupport.getActiveFederationRedeemScript().isPresent());
        }

        @Test
        @Tag("getActiveFederationRedeemScript")
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
        @Tag("getActiveFederationAddress")
        void getActiveFederationAddress_returnsGenesisFederationAddress() {
            Address activeFederationAddress = federationSupport.getActiveFederationAddress();
            assertThat(activeFederationAddress, is(genesisFederation.getAddress()));
        }

        @Test
        @Tag("getActiveFederationSize")
        void getActiveFederationSize_returnsGenesisFederationSize() {
            int activeFederationSize = federationSupport.getActiveFederationSize();
            assertThat(activeFederationSize, is(genesisFederation.getSize()));
        }

        @Test
        @Tag("getActiveFederationThreshold")
        void getActiveFederationThreshold_returnsGenesisFederationThreshold() {
            int activeFederationThreshold = federationSupport.getActiveFederationThreshold();
            assertThat(activeFederationThreshold, is(genesisFederation.getNumberOfSignaturesRequired()));
        }

        @Test
        @Tag("getActiveFederationCreationTime")
        void getActiveFederationCreationTime_returnsGenesisFederationCreationTime() {
            Instant activeFederationCreationTime = federationSupport.getActiveFederationCreationTime();
            assertThat(activeFederationCreationTime, is(genesisFederation.getCreationTime()));
        }

        @Test
        @Tag("getActiveFederationCreationBlockNumber")
        void getActiveFederationCreationBlockNumber_returnsGenesisFederationCreationBlockNumber() {
            long activeFederationCreationBlockNumber = federationSupport.getActiveFederationCreationBlockNumber();
            assertThat(activeFederationCreationBlockNumber, is(genesisFederation.getCreationBlockNumber()));
        }
    }

    @Nested
    @Tag("null old federation, non null new federation")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithOneNullFederation {
        @BeforeAll
        void setUp() {
            // create old and new federations
            P2shErpFederationBuilder p2shErpFederationBuilder = new P2shErpFederationBuilder();
            oldFederation = p2shErpFederationBuilder
                .build();
            List<BtcECKey> newFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
            );
            newFederation = p2shErpFederationBuilder
                .withMembersBtcPublicKeys(newFederationKeys)
                .build();

            StorageAccessor storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();
        }

        @ParameterizedTest
        @Tag("getActiveFederation")
        @MethodSource("expectedFederationArgs")
        void getActiveFederation_returnsExpectedFederation(Federation oldFederation, Federation newFederation, Federation expectedFederation) {
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            Federation activeFederation = federationSupport.getActiveFederation();
            assertThat(activeFederation, is(expectedFederation));
        }

        private Stream<Arguments> expectedFederationArgs() {
            return Stream.of(
                Arguments.of(null, newFederation, newFederation)
            );
        }

        @Test
        @Tag("getActiveFederationRedeemScript")
        void getActiveFederationRedeemScript_beforeRSKIP293_returnsEmpty() {
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();
            assertFalse(federationSupport.getActiveFederationRedeemScript().isPresent());
        }

        @ParameterizedTest
        @Tag("getActiveFederationRedeemScript")
        @MethodSource("expectedRedeemScriptArgs")
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

        private Stream<Arguments> expectedRedeemScriptArgs() {
            return Stream.of(
                Arguments.of(null, newFederation, newFederation.getRedeemScript())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationAddress")
        @MethodSource("expectedAddressArgs")
        void getActiveFederationAddress_returnsExpectedAddress(Federation oldFederation, Federation newFederation, Address expectedAddress) {
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            Address activeFederationAddress = federationSupport.getActiveFederationAddress();
            assertThat(activeFederationAddress, is(expectedAddress));
        }

        private Stream<Arguments> expectedAddressArgs() {
            return Stream.of(
                Arguments.of(null, newFederation, newFederation.getAddress())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationSize")
        @MethodSource("expectedSizeArgs")
        void getActiveFederationSize_returnsExpectedSize(Federation oldFederation, Federation newFederation, int expectedSize) {
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            int activeFederationSize = federationSupport.getActiveFederationSize();
            assertThat(activeFederationSize, is(expectedSize));
        }

        private Stream<Arguments> expectedSizeArgs() {
            return Stream.of(
                Arguments.of(null, newFederation, newFederation.getSize())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationThreshold")
        @MethodSource("expectedThresholdArgs")
        void getActiveFederationThreshold_returnsExpectedThreshold(Federation oldFederation, Federation newFederation, int expectedThreshold) {
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            int activeFederationThreshold = federationSupport.getActiveFederationThreshold();
            assertThat(activeFederationThreshold, is(expectedThreshold));
        }

        private Stream<Arguments> expectedThresholdArgs() {
            return Stream.of(
                Arguments.of(null, newFederation, newFederation.getNumberOfSignaturesRequired())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationCreationTime")
        @MethodSource("expectedCreationTimeArgs")
        void getActiveFederationCreationTime_returnsExpectedCreationTime(Federation oldFederation, Federation newFederation, Instant expectedCreationTime) {
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            Instant activeFederationCreationTime = federationSupport.getActiveFederationCreationTime();
            assertThat(activeFederationCreationTime, is(expectedCreationTime));
        }

        private Stream<Arguments> expectedCreationTimeArgs() {
            return Stream.of(
                Arguments.of(null, newFederation, newFederation.getCreationTime())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationCreationBlockNumber")
        @MethodSource("expectedCreationBlockNumberArgs")
        void getActiveFederationCreationBlockNumber_returnsExpectedCreationBlockNumber(Federation oldFederation, Federation newFederation, long expectedCreationBlockNumber) {
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);

            long activeFederationCreationBlockNumber = federationSupport.getActiveFederationCreationBlockNumber();
            assertThat(activeFederationCreationBlockNumber, is(expectedCreationBlockNumber));
        }

        private Stream<Arguments> expectedCreationBlockNumberArgs() {
            return Stream.of(
                Arguments.of(null, newFederation, newFederation.getCreationBlockNumber())
            );
        }
    }

    @Nested
    @Tag("non null federations")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithNonNullFederations {
        // new federation should be active if we are past the activation block number
        // old federation should be active if we are before the activation block number
        // activation block number is smaller for hop than for fingerroot

        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        long blockNumberFederationActivationHop;
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        long blockNumberFederationActivationFingerroot;
        FederationStorageProvider storageProvider;

        @BeforeAll
        void setUp() {
            long oldFederationCreationBlockNumber = 20;
            long newFederationCreationBlockNumber = 65;

            // get block number activation for hop
            long newFederationActivationAgeHop = federationMainnetConstants.getFederationActivationAge(hopActivations);
            blockNumberFederationActivationHop = newFederationCreationBlockNumber + newFederationActivationAgeHop;

            // get block number activation for fingerroot
            long newFederationActivationAgeFingerroot = federationMainnetConstants.getFederationActivationAge(fingerrootActivations);
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
        @Tag("getActiveFederation")
        @MethodSource("expectedFederationArgs")
        void getActiveFederation_returnsExpectedFederationAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, Federation expectedFederation) {
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

        private Stream<Arguments> expectedFederationArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederation),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederation),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederation),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederation),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederation),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederation)
            );
        }

        @Test
        @Tag("getActiveFederationRedeemScript")
        void getActiveFederationRedeemScript_beforeRSKIP293_returnsEmpty() {
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();
            assertFalse(federationSupport.getActiveFederationRedeemScript().isPresent());
        }

        @ParameterizedTest
        @Tag("getActiveFederationRedeemScript")
        @MethodSource("expectedRedeemScriptArgs")
        void getActiveFederationRedeemScript_returnsExpectedRedeemScriptAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, Script expectedRedeemScript) {
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

        private Stream<Arguments> expectedRedeemScriptArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederation.getRedeemScript()),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederation.getRedeemScript()),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederation.getRedeemScript()),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederation.getRedeemScript()),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederation.getRedeemScript()),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederation.getRedeemScript())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationAddress")
        @MethodSource("expectedAddressArgs")
        void getActiveFederationAddress_returnsExpectedAddressAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, Address expectedAddress) {
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

        private Stream<Arguments> expectedAddressArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederation.getAddress()),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederation.getAddress()),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederation.getAddress()),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederation.getAddress()),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederation.getAddress()),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederation.getAddress())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationSize")
        @MethodSource("expectedSizeArgs")
        void getActiveFederationSize_returnsExpectedSizeAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, int expectedSize) {
            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            int activeFederationSize = federationSupport.getActiveFederationSize();
            assertThat(activeFederationSize, is(expectedSize));
        }

        private Stream<Arguments> expectedSizeArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederation.getSize()),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederation.getSize()),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederation.getSize()),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederation.getSize()),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederation.getSize()),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederation.getSize())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationThreshold")
        @MethodSource("expectedThresholdArgs")
        void getActiveFederationThreshold_returnsExpectedThresholdAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, int expectedThreshold) {
            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            int activeFederationThreshold = federationSupport.getActiveFederationThreshold();
            assertThat(activeFederationThreshold, is(expectedThreshold));
        }

        private Stream<Arguments> expectedThresholdArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederation.getNumberOfSignaturesRequired()),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederation.getNumberOfSignaturesRequired()),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederation.getNumberOfSignaturesRequired()),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederation.getNumberOfSignaturesRequired()),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederation.getNumberOfSignaturesRequired()),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederation.getNumberOfSignaturesRequired())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationCreationTime")
        @MethodSource("expectedCreationTimeArgs")
        void getActiveFederationCreationTime_returnsExpectedCreationTimeAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, Instant expectedCreationTime) {
            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Instant activeFederationCreationTime = federationSupport.getActiveFederationCreationTime();
            assertThat(activeFederationCreationTime, is(expectedCreationTime));
        }

        private Stream<Arguments> expectedCreationTimeArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederation.getCreationTime()),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederation.getCreationTime()),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederation.getCreationTime()),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederation.getCreationTime()),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederation.getCreationTime()),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederation.getCreationTime())
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationCreationBlockNumber")
        @MethodSource("expectedCreationBlockNumberArgs")
        void getActiveFederationCreationBlockNumber_returnsExpectedCreationBlockNumberAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, long expectedCreationBlockNumber) {
            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            long activeFederationCreationBlockNumber = federationSupport.getActiveFederationCreationBlockNumber();
            assertThat(activeFederationCreationBlockNumber, is(expectedCreationBlockNumber));
        }

        private Stream<Arguments> expectedCreationBlockNumberArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederation.getCreationBlockNumber()),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederation.getCreationBlockNumber()),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederation.getCreationBlockNumber()),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederation.getCreationBlockNumber()),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederation.getCreationBlockNumber()),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederation.getCreationBlockNumber())
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
