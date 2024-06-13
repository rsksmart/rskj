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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.FederationSupportBuilder;
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
    private Federation newFederation;
    private FederationStorageProvider storageProvider;
    private final FederationSupportBuilder federationSupportBuilder = new FederationSupportBuilder();
    private FederationSupport federationSupport;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("null federations")
    class ActiveFederationTestsWithNullFederations {
        @BeforeEach
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

        @Test
        @Tag("getActiveFederatorBtcPublicKey")
        void getActiveFederatorBtcPublicKey_returnsFederatorBtcPublicKeyFromGenesisFederation() {
            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorBtcPublicKey(0);
            assertThat(activeFederatorBtcPublicKey, is(genesisFederation.getBtcPublicKeys().get(0).getPubKey()));
        }

        @Test
        @Tag("getActiveFederatorBtcPublicKey")
        void getActiveFederatorBtcPublicKey_withNegativeIndex_throwsIndexOutOfBoundsException() {
            assertThrows(IndexOutOfBoundsException.class, () -> federationSupport.getActiveFederatorBtcPublicKey(-1));
        }

        @Test
        @Tag("getActiveFederatorBtcPublicKey")
        void getActiveFederatorBtcPublicKey_withIndexGreaterThanGenesisFederationSize_throwsIndexOutOfBoundsException() {
            int genesisFederationSize = genesisFederation.getSize();
            assertThrows(
                IndexOutOfBoundsException.class, () ->
                federationSupport.getActiveFederatorBtcPublicKey(genesisFederationSize)
            );
        }

        @Test
        @Tag("getActiveFederatorPublicKeyOfType")
        void getActiveFederatorPublicKeyOfType_returnsFederatorPublicKeysFromGenesisFederation() {
            BtcECKey federatorFromGenesisFederationBtcPublicKey = genesisFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromGenesisFederationRskPublicKey = genesisFederation.getRskPublicKeys().get(0);
            ECKey federatorFromGenesisFederationMstPublicKey = genesisFederation.getMstPublicKeys().get(0);

            // since genesis federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(federatorFromGenesisFederationBtcPublicKey.getPubKey());;
            assertThat(federatorFromGenesisFederationRskPublicKey, is(ecKeyDerivedFromBtcKey));
            // since genesis federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(federatorFromGenesisFederationMstPublicKey, is(federatorFromGenesisFederationRskPublicKey));

            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(activeFederatorBtcPublicKey, is(federatorFromGenesisFederationBtcPublicKey.getPubKey()));

            byte[] activeFederatorRskPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(activeFederatorRskPublicKey, is(federatorFromGenesisFederationRskPublicKey.getPubKey(true)));

            byte[] activeFederatorMstPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(activeFederatorMstPublicKey, is(federatorFromGenesisFederationMstPublicKey.getPubKey(true)));
        }
    }

    @Nested
    @Tag("null old federation, non null new federation")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithNullOldFederation {
        @BeforeEach
        void setUp() {
            StorageAccessor storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            // create new federation
            P2shErpFederationBuilder p2shErpFederationBuilder = new P2shErpFederationBuilder();
            newFederation = p2shErpFederationBuilder
                .build();

            storageProvider.setNewFederation(newFederation);
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();

        }

        @Test
        @Tag("getActiveFederation")
        void getActiveFederation_returnsExpectedFederation() {
            Federation activeFederation = federationSupport.getActiveFederation();
            assertThat(activeFederation, is(newFederation));
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
        void getActiveFederationRedeemScript_afterRSKIP293_returnsExpectedRedeemScript() {
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();

            Optional<Script> activeFederationRedeemScript = federationSupport.getActiveFederationRedeemScript();
            assertTrue(activeFederationRedeemScript.isPresent());
            assertThat(activeFederationRedeemScript.get(), is(newFederation.getRedeemScript()));
        }

        @Test
        @Tag("getActiveFederationAddress")
        void getActiveFederationAddress_returnsExpectedAddress() {
            Address activeFederationAddress = federationSupport.getActiveFederationAddress();
            assertThat(activeFederationAddress, is(newFederation.getAddress()));
        }

        @Test
        @Tag("getActiveFederationSize")
        void getActiveFederationSize_returnsExpectedSize() {
            int activeFederationSize = federationSupport.getActiveFederationSize();
            assertThat(activeFederationSize, is(newFederation.getSize()));
        }

        @Test
        @Tag("getActiveFederationThreshold")
        void getActiveFederationThreshold_returnsExpectedThreshold() {
            int activeFederationThreshold = federationSupport.getActiveFederationThreshold();
            assertThat(activeFederationThreshold, is(newFederation.getNumberOfSignaturesRequired()));
        }

        @Test
        @Tag("getActiveFederationCreationTime")
        void getActiveFederationCreationTime_returnsExpectedCreationTime() {
            Instant activeFederationCreationTime = federationSupport.getActiveFederationCreationTime();
            assertThat(activeFederationCreationTime, is(newFederation.getCreationTime()));
        }

        @Test
        @Tag("getActiveFederationCreationBlockNumber")
        void getActiveFederationCreationBlockNumber_returnsExpectedCreationBlockNumber() {
            long activeFederationCreationBlockNumber = federationSupport.getActiveFederationCreationBlockNumber();
            assertThat(activeFederationCreationBlockNumber, is(newFederation.getCreationBlockNumber()));
        }

        @Test
        @Tag("getActiveFederatorBtcPublicKey")
        void getActiveFederatorBtcPublicKey_returnsExpectedFederatorBtcPublicKey() {
            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorBtcPublicKey(0);
            assertThat(activeFederatorBtcPublicKey, is(newFederation.getBtcPublicKeys().get(0).getPubKey()));
        }

        @Test
        @Tag("getActiveFederatorPublicKeyOfType")
        void getActiveFederatorPublicKeyOfType_returnsExpectedFederatorPublicKeys() {
            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = newFederation.getRskPublicKeys().get(0);
            ECKey federatorFromNewFederationMstPublicKey = newFederation.getMstPublicKeys().get(0);

            // since new federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(federatorFromNewFederationBtcPublicKey.getPubKey());;
            assertThat(federatorFromNewFederationRskPublicKey, is(ecKeyDerivedFromBtcKey));
            // since new federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(federatorFromNewFederationMstPublicKey, is(federatorFromNewFederationRskPublicKey));

            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(activeFederatorBtcPublicKey, is(federatorFromNewFederationBtcPublicKey.getPubKey()));

            byte[] activeFederatorRskPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.RSK);
            // for rsk, the method returns the pubkey compressed
            assertThat(activeFederatorRskPublicKey, is(federatorFromNewFederationRskPublicKey.getPubKey(true)));

            byte[] activeFederatorMstPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST);
            // for mst, the method returns the pubkey compressed
            assertThat(activeFederatorMstPublicKey, is(federatorFromNewFederationMstPublicKey.getPubKey(true)));
        }

        @Test
        @Tag("getActiveFederatorPublicKeyOfType")
        void getActiveFederatorPublicKeyOfType_withSpecificRskKeys_returnsExpectedFederatorPublicKeys() {
            // create new federation with specific rsk public keys
            List<ECKey> rskECKeys = BitcoinTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            newFederation = new P2shErpFederationBuilder()
                .withMembersRskPublicKeys(rskECKeys)
                .build();
            storageProvider.setNewFederation(newFederation);

            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = newFederation.getRskPublicKeys().get(0);
            ECKey federatorFromNewFederationMstPublicKey = newFederation.getMstPublicKeys().get(0);

            // since new federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(federatorFromNewFederationMstPublicKey, is(federatorFromNewFederationRskPublicKey));

            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(activeFederatorBtcPublicKey, is(federatorFromNewFederationBtcPublicKey.getPubKey()));

            byte[] activeFederatorRskPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(activeFederatorRskPublicKey, is(federatorFromNewFederationRskPublicKey.getPubKey(true)));

            byte[] activeFederatorMstPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(activeFederatorMstPublicKey, is(federatorFromNewFederationMstPublicKey.getPubKey(true)));
        }

        @Test
        @Tag("getActiveFederatorPublicKeyOfType")
        void getActiveFederatorPublicKeyOfType_withSpecificRskAndMstKeys_returnsExpectedFederatorPublicKeys() {
            // create new federation with specific rsk and mst public keys
            List<ECKey> rskECKeys = BitcoinTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            List<ECKey> mstECKeys = BitcoinTestUtils.getEcKeysFromSeeds(
                new String[]{"mst01", "mst02", "mst03", "mst04", "mst05", "mst06", "mst07", "mst08", "mst09"}
            );
            newFederation = new P2shErpFederationBuilder()
                .withMembersRskPublicKeys(rskECKeys)
                .withMembersMstPublicKeys(mstECKeys)
                .build();
            storageProvider.setNewFederation(newFederation);

            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = newFederation.getRskPublicKeys().get(0);
            ECKey federatorFromNewFederationMstPublicKey = newFederation.getMstPublicKeys().get(0);

            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(activeFederatorBtcPublicKey, is(federatorFromNewFederationBtcPublicKey.getPubKey()));

            byte[] activeFederatorRskPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(activeFederatorRskPublicKey, is(federatorFromNewFederationRskPublicKey.getPubKey(true)));

            byte[] activeFederatorMstPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(activeFederatorMstPublicKey, is(federatorFromNewFederationMstPublicKey.getPubKey(true)));
        }
    }

    @Nested
    @Tag("non null federations")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithNonNullFederations {
        // new federation should be active if we are past the activation block number
        // old federation should be active if we are before the activation block number
        // activation block number is smaller for hop than for fingerroot

        // create old and new federations
        long oldFederationCreationBlockNumber = 20;
        long newFederationCreationBlockNumber = 65;
        Federation oldFederation = new P2shErpFederationBuilder()
            .withCreationBlockNumber(oldFederationCreationBlockNumber)
            .build();
        List<BtcECKey> newFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
        );
        Federation newFederation = new P2shErpFederationBuilder()
            .withMembersBtcPublicKeys(newFederationKeys)
            .withCreationBlockNumber(newFederationCreationBlockNumber)
            .build();

        // get block number activations for hop and fingerroot
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        long newFederationActivationAgeHop = federationMainnetConstants.getFederationActivationAge(hopActivations);
        long blockNumberFederationActivationHop = newFederationCreationBlockNumber + newFederationActivationAgeHop;
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        long newFederationActivationAgeFingerroot = federationMainnetConstants.getFederationActivationAge(fingerrootActivations);
        long blockNumberFederationActivationFingerroot = newFederationCreationBlockNumber + newFederationActivationAgeFingerroot;

        FederationStorageProvider storageProvider;

        @BeforeEach
        void setUp() {
            // save federations in storage
            StorageAccessor storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);
        }

        @ParameterizedTest
        @Tag("getActiveFederation")
        @MethodSource("expectedFederationArgs")
        void getActiveFederation_returnsExpectedFederationAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            Federation expectedFederation) {

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
        void getActiveFederationRedeemScript_returnsExpectedRedeemScriptAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            Script expectedRedeemScript) {

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
        void getActiveFederationAddress_returnsExpectedAddressAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            Address expectedAddress) {

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
        void getActiveFederationSize_returnsExpectedSizeAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            int expectedSize) {

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
        void getActiveFederationThreshold_returnsExpectedThresholdAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            int expectedThreshold) {

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
        void getActiveFederationCreationTime_returnsExpectedCreationTimeAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            Instant expectedCreationTime) {

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
        void getActiveFederationCreationBlockNumber_returnsExpectedCreationBlockNumberAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            long expectedCreationBlockNumber) {

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

        @ParameterizedTest
        @Tag("getActiveFederatorBtcPublicKey")
        @MethodSource("expectedFederatorBtcPublicKeyArgs")
        void getActiveFederatorBtcPublicKey_returnsExpectedFederatorBtcPublicKeyAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            BtcECKey expectedFederatorBtcPublicKey) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorBtcPublicKey(0);
            assertThat(activeFederatorBtcPublicKey, is(expectedFederatorBtcPublicKey.getPubKey()));
        }

        private Stream<Arguments> expectedFederatorBtcPublicKeyArgs() {
            BtcECKey federatorFromOldFederationBtcPublicKey = oldFederation.getBtcPublicKeys().get(0);
            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);

            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, federatorFromOldFederationBtcPublicKey),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, federatorFromNewFederationBtcPublicKey),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, federatorFromOldFederationBtcPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, federatorFromOldFederationBtcPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, federatorFromNewFederationBtcPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, federatorFromNewFederationBtcPublicKey)
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederatorPublicKeyOfType")
        @MethodSource("expectedFederatorPublicKeyOfTypeArgs")
        void getActiveFederatorPublicKeyOfType_returnsExpectedFederatorPublicKeysAccordingToActivationAgeAndActivations(long currentBlock, ActivationConfig.ForBlock activations, BtcECKey expectedFederatorBtcPublicKey, ECKey expectedFederatorRskPublicKey, ECKey expectedFederatorMstPublicKey) {
            // since new federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(expectedFederatorBtcPublicKey.getPubKey());;
            assertThat(expectedFederatorRskPublicKey, is(ecKeyDerivedFromBtcKey));
            // since new federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(expectedFederatorMstPublicKey, is(expectedFederatorRskPublicKey));

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(activeFederatorBtcPublicKey, is(expectedFederatorBtcPublicKey.getPubKey()));

            byte[] activeFederatorRskPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.RSK);
            // for rsk, the method returns the pubkey compressed
            assertThat(activeFederatorRskPublicKey, is(expectedFederatorRskPublicKey.getPubKey(true)));

            byte[] activeFederatorMstPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST);
            // for mst, the method returns the pubkey compressed
            assertThat(activeFederatorMstPublicKey, is(expectedFederatorMstPublicKey.getPubKey(true)));
        }

        private Stream<Arguments> expectedFederatorPublicKeyOfTypeArgs() {
            BtcECKey federatorFromOldFederationBtcPublicKey = oldFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromOldFederationRskPublicKey = oldFederation.getRskPublicKeys().get(0);
            ECKey federatorFromOldFederationMstPublicKey = oldFederation.getMstPublicKeys().get(0);

            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = newFederation.getRskPublicKeys().get(0);
            ECKey federatorFromNewFederationMstPublicKey = newFederation.getMstPublicKeys().get(0);

            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, federatorFromOldFederationBtcPublicKey, federatorFromOldFederationRskPublicKey, federatorFromOldFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, federatorFromNewFederationBtcPublicKey, federatorFromNewFederationRskPublicKey, federatorFromNewFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, federatorFromOldFederationBtcPublicKey, federatorFromOldFederationRskPublicKey, federatorFromOldFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, federatorFromOldFederationBtcPublicKey, federatorFromOldFederationRskPublicKey, federatorFromOldFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, federatorFromNewFederationBtcPublicKey, federatorFromNewFederationRskPublicKey, federatorFromNewFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, federatorFromNewFederationBtcPublicKey, federatorFromNewFederationRskPublicKey, federatorFromNewFederationMstPublicKey)
            );
        }
    }
}
