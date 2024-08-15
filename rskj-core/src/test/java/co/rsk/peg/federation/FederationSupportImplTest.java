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

import static co.rsk.peg.bitcoin.BitcoinTestUtils.flatKeysAsByteArray;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static co.rsk.peg.federation.FederationStorageIndexKey.OLD_FEDERATION_BTC_UTXOS_KEY;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP377;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.*;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.FederationMember.KeyType;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.util.HexUtils;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.test.builders.FederationSupportBuilder;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;

class FederationSupportImplTest {

    private static final FederationConstants federationMainnetConstants = FederationMainNetConstants.getInstance();
    private final Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationMainnetConstants);
    private ErpFederation newFederation;
    private StorageAccessor storageAccessor;
    private FederationStorageProvider storageProvider;
    private final FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
    private FederationSupport federationSupport;
    private SignatureCache signatureCache;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        storageProvider = new FederationStorageProviderImpl(storageAccessor);
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withActivations(activations)
            .build();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("null new and old federations")
    class ActiveFederationTestsWithNullFederations {
        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
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
            ECKey federatorFromGenesisFederationRskPublicKey = getRskPublicKeysFromFederationMembers(genesisFederation.getMembers()).get(0);
            ECKey federatorFromGenesisFederationMstPublicKey = getMstPublicKeysFromFederationMembers(genesisFederation.getMembers()).get(0);

            // since genesis federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(federatorFromGenesisFederationBtcPublicKey.getPubKey());
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

        @Test
        @Tag("getActiveFederationBtcUTXOs")
        void getActiveFederationUTXOs_returnsGenesisFederationUTXOs() {
            List<UTXO> genesisFederationUTXOs = BitcoinTestUtils.createUTXOs(10, genesisFederation.getAddress());
            storageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), genesisFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            List<UTXO> activeFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();
            assertThat(activeFederationUTXOs, is(genesisFederationUTXOs));
        }
    }

    @Nested
    @Tag("null old federation, non null new federation")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithNullOldFederation {
        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            // create new federation
            newFederation = P2shErpFederationBuilder.builder().build();

            storageProvider.setNewFederation(newFederation);
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();
        }

        @Test
        @Tag("getActiveFederation")
        void getActiveFederation_returnsNewFederation() {
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
        void getActiveFederationRedeemScript_afterRSKIP293_returnsNewFederationRedeemScript() {
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
        void getActiveFederationAddress_returnsNewFederationAddress() {
            Address activeFederationAddress = federationSupport.getActiveFederationAddress();
            assertThat(activeFederationAddress, is(newFederation.getAddress()));
        }

        @Test
        @Tag("getActiveFederationSize")
        void getActiveFederationSize_returnsNewFederationSize() {
            int activeFederationSize = federationSupport.getActiveFederationSize();
            assertThat(activeFederationSize, is(newFederation.getSize()));
        }

        @Test
        @Tag("getActiveFederationThreshold")
        void getActiveFederationThreshold_returnsNewFederationThreshold() {
            int activeFederationThreshold = federationSupport.getActiveFederationThreshold();
            assertThat(activeFederationThreshold, is(newFederation.getNumberOfSignaturesRequired()));
        }

        @Test
        @Tag("getActiveFederationCreationTime")
        void getActiveFederationCreationTime_returnsNewFederationCreationTime() {
            Instant activeFederationCreationTime = federationSupport.getActiveFederationCreationTime();
            assertThat(activeFederationCreationTime, is(newFederation.getCreationTime()));
        }

        @Test
        @Tag("getActiveFederationCreationBlockNumber")
        void getActiveFederationCreationBlockNumber_returnsNewFederationCreationBlockNumber() {
            long activeFederationCreationBlockNumber = federationSupport.getActiveFederationCreationBlockNumber();
            assertThat(activeFederationCreationBlockNumber, is(newFederation.getCreationBlockNumber()));
        }

        @Test
        @Tag("getActiveFederatorBtcPublicKey")
        void getActiveFederatorBtcPublicKey_returnsFederatorFromNewFederationBtcPublicKey() {
            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorBtcPublicKey(0);
            assertThat(activeFederatorBtcPublicKey, is(newFederation.getBtcPublicKeys().get(0).getPubKey()));
        }

        @Test
        @Tag("getActiveFederatorPublicKeyOfType")
        void getActiveFederatorPublicKeyOfType_returnsFederatorFromNewFederationPublicKeys() {
            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = getRskPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);
            ECKey federatorFromNewFederationMstPublicKey = getMstPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);

            // since new federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(federatorFromNewFederationBtcPublicKey.getPubKey());
            assertThat(federatorFromNewFederationRskPublicKey, is(ecKeyDerivedFromBtcKey));
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
        void getActiveFederatorPublicKeyOfType_withSpecificRskKeys_returnsFederatorFromNewFederationPublicKeys() {
            // create new federation with specific rsk public keys
            List<ECKey> rskECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            newFederation = P2shErpFederationBuilder.builder()
                .withMembersRskPublicKeys(rskECKeys)
                .build();
            storageProvider.setNewFederation(newFederation);

            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = getRskPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);
            ECKey federatorFromNewFederationMstPublicKey = getMstPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);

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
        void getActiveFederatorPublicKeyOfType_withSpecificRskAndMstKeys_returnsFederatorFromNewFederationPublicKeys() {
            // create new federation with specific rsk and mst public keys
            List<ECKey> rskECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            List<ECKey> mstECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"mst01", "mst02", "mst03", "mst04", "mst05", "mst06", "mst07", "mst08", "mst09"}
            );
            newFederation = P2shErpFederationBuilder.builder()
                .withMembersRskPublicKeys(rskECKeys)
                .withMembersMstPublicKeys(mstECKeys)
                .build();
            storageProvider.setNewFederation(newFederation);

            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = getRskPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);
            ECKey federatorFromNewFederationMstPublicKey = getMstPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);

            byte[] activeFederatorBtcPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(activeFederatorBtcPublicKey, is(federatorFromNewFederationBtcPublicKey.getPubKey()));

            byte[] activeFederatorRskPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(activeFederatorRskPublicKey, is(federatorFromNewFederationRskPublicKey.getPubKey(true)));

            byte[] activeFederatorMstPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(activeFederatorMstPublicKey, is(federatorFromNewFederationMstPublicKey.getPubKey(true)));
        }

        @Test
        @Tag("getActiveFederationBtcUTXOs")
        void getActiveFederationUTXOs_returnsNewFederationUTXOs() {
            List<UTXO> newFederationUTXOs = BitcoinTestUtils.createUTXOs(10, newFederation.getAddress());
            storageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), newFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            List<UTXO> activeFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();
            assertThat(activeFederationUTXOs, is(newFederationUTXOs));
        }
    }

    @Nested
    @Tag("non null new and old federations")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ActiveFederationTestsWithNonNullFederations {
        // new federation should be active if we are past the activation block number
        // old federation should be active if we are before the activation block number
        // activation block number is smaller for hop than for fingerroot

        // create old and new federations
        long oldFederationCreationBlockNumber = 20;
        long newFederationCreationBlockNumber = 65;
        Federation oldFederation = P2shErpFederationBuilder.builder()
            .withCreationBlockNumber(oldFederationCreationBlockNumber)
            .build();
        List<BtcECKey> newFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
        );
        Federation newFederation = P2shErpFederationBuilder.builder()
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
            storageAccessor = new InMemoryStorage();
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
        void getActiveFederatorPublicKeyOfType_returnsExpectedFederatorPublicKeysAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            BtcECKey expectedFederatorBtcPublicKey,
            ECKey expectedFederatorRskPublicKey,
            ECKey expectedFederatorMstPublicKey) {
            // since new federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(expectedFederatorBtcPublicKey.getPubKey());
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
            assertThat(activeFederatorRskPublicKey, is(expectedFederatorRskPublicKey.getPubKey(true)));

            byte[] activeFederatorMstPublicKey = federationSupport.getActiveFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(activeFederatorMstPublicKey, is(expectedFederatorMstPublicKey.getPubKey(true)));
        }

        private Stream<Arguments> expectedFederatorPublicKeyOfTypeArgs() {
            BtcECKey federatorFromOldFederationBtcPublicKey = oldFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromOldFederationRskPublicKey = getRskPublicKeysFromFederationMembers(oldFederation.getMembers()).get(0);
            ECKey federatorFromOldFederationMstPublicKey = getMstPublicKeysFromFederationMembers(oldFederation.getMembers()).get(0);

            BtcECKey federatorFromNewFederationBtcPublicKey = newFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromNewFederationRskPublicKey = getRskPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);
            ECKey federatorFromNewFederationMstPublicKey = getMstPublicKeysFromFederationMembers(newFederation.getMembers()).get(0);

            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, federatorFromOldFederationBtcPublicKey, federatorFromOldFederationRskPublicKey, federatorFromOldFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, federatorFromNewFederationBtcPublicKey, federatorFromNewFederationRskPublicKey, federatorFromNewFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, federatorFromOldFederationBtcPublicKey, federatorFromOldFederationRskPublicKey, federatorFromOldFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, federatorFromOldFederationBtcPublicKey, federatorFromOldFederationRskPublicKey, federatorFromOldFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, federatorFromNewFederationBtcPublicKey, federatorFromNewFederationRskPublicKey, federatorFromNewFederationMstPublicKey),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, federatorFromNewFederationBtcPublicKey, federatorFromNewFederationRskPublicKey, federatorFromNewFederationMstPublicKey)
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationUTXOs")
        @MethodSource("expectedUTXOsArgs")
        void getActiveFederationBtcUTXOs_returnsExpectedUTXOsAccordingToActivationAgeAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            List<UTXO> expectedUTXOs) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            List<UTXO> oldFederationUTXOs = BitcoinTestUtils.createUTXOs(5, oldFederation.getAddress());
            storageAccessor.saveToRepository(OLD_FEDERATION_BTC_UTXOS_KEY.getKey(), oldFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);
            List<UTXO> newFederationUTXOs = BitcoinTestUtils.createUTXOs(10, newFederation.getAddress());
            storageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), newFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            List<UTXO> activeFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();
            assertThat(activeFederationUTXOs, is(expectedUTXOs));
        }

        private Stream<Arguments> expectedUTXOsArgs() {
            List<UTXO> oldFederationUTXOs = BitcoinTestUtils.createUTXOs(5, oldFederation.getAddress());
            List<UTXO> newFederationUTXOs = BitcoinTestUtils.createUTXOs(10, newFederation.getAddress());

            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations, oldFederationUTXOs),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations, oldFederationUTXOs),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations, oldFederationUTXOs),
                Arguments.of(blockNumberFederationActivationHop, hopActivations, newFederationUTXOs),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations, newFederationUTXOs),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations, newFederationUTXOs)
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("active federation creation block height tests")
    class ActiveFederationCreationBlockHeightTests {
        // if nextFederationCreationBlockHeight is not set,
        // method should return activeFederationCreationBlockHeight.
        // if nextFederationCreationBlockHeight is set, method should return:
        // nextFederationCreationBlockHeight if enough blocks have passed,
        // activeFederationCreationBlockHeight if not.
        // if activeFederationCreationBlockHeight is not set,
        // method should return 0L.

        long nextFederationCreationBlockHeight = 200L;
        long activeFederationCreationBlockHeight = 100L;

        // get activation age and activations for hop and fingerroot
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        long newFederationActivationAgeHop = federationMainnetConstants.getFederationActivationAge(hopActivations);
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        long newFederationActivationAgeFingerroot = federationMainnetConstants.getFederationActivationAge(fingerrootActivations);

        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();
        }

        @Test
        @Tag("getActiveFederationCreationBlockHeight")
        void getActiveFederationCreationBlockHeight_beforeRSKIP186_returnsZero() {
            ActivationConfig.ForBlock activationsPreIris = ActivationConfigsForTest.papyrus200().forBlock(0L);

            FederationSupport federationSupportPreIris = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activationsPreIris)
                .build();

            long activeFederationCreationBlockHeightPreIris = federationSupportPreIris.getActiveFederationCreationBlockHeight();
            assertThat(activeFederationCreationBlockHeightPreIris, is(0L));
        }

        @Test
        @Tag("getActiveFederationCreationBlockHeight")
        void getActiveFederationCreationBlockHeight_withNextAndActiveFederationCreationBlockHeightsUnset_returnsZero() {
            long activeFedCreationBlockHeight = federationSupport.getActiveFederationCreationBlockHeight();
            assertThat(activeFedCreationBlockHeight, is(0L));
        }

        @Test
        @Tag("getActiveFederationCreationBlockHeight")
        void getActiveFederationCreationBlockHeight_withNextFederationCreationBlockHeightUnsetAndActiveFederationCreationBlockHeightSet_returnsActiveFederationCreationBlockHeight() {
            storageProvider.setActiveFederationCreationBlockHeight(activeFederationCreationBlockHeight);

            long activeFedCreationBlockHeight = federationSupport.getActiveFederationCreationBlockHeight();
            assertThat(activeFedCreationBlockHeight, is(activeFederationCreationBlockHeight));
        }

        @ParameterizedTest
        @Tag("getActiveFederationCreationBlockHeight")
        @MethodSource("newFederationCreationBlockHeightAndCurrentBlockAndActivationsArgs")
        void getActiveFederationCreationBlockHeight_withNewFederationCreationBlockHeightSetAndActiveFederationCreationBlockHeightUnset_returnsCreationBlockHeightAccordingToCurrentBlockAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            long expectedActiveFederationCreationBlockHeight
        ) {
            Block rskExecutionBlock = mock(Block.class);
            when(rskExecutionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(rskExecutionBlock)
                .withActivations(activations)
                .build();
            storageProvider.setNextFederationCreationBlockHeight(nextFederationCreationBlockHeight);

            long activeFedCreationBlockHeight = federationSupport.getActiveFederationCreationBlockHeight();
            assertThat(activeFedCreationBlockHeight, is(expectedActiveFederationCreationBlockHeight));
        }

        private Stream<Arguments> newFederationCreationBlockHeightAndCurrentBlockAndActivationsArgs() {
            return Stream.of(
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop - 1, hopActivations, 0L),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot - 1, fingerrootActivations, 0L),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop, fingerrootActivations, 0L),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop, hopActivations, nextFederationCreationBlockHeight),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot, fingerrootActivations, nextFederationCreationBlockHeight),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot, hopActivations, nextFederationCreationBlockHeight)
            );
        }

        @ParameterizedTest
        @Tag("getActiveFederationCreationBlockHeight")
        @MethodSource("newAndActiveFederationCreationBlockHeightsAndCurrentBlockAndActivationsArgs")
        void getActiveFederationCreationBlockHeight_withNewAndActiveFederationCreationBlockHeightsSet_returnsCreationBlockHeightAccordingToCurrentBlockAndActivations(
            long currentBlock,
            ActivationConfig.ForBlock activations,
            long expectedActiveFederationCreationBlockHeight
        ) {
            Block rskExecutionBlock = mock(Block.class);
            when(rskExecutionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(rskExecutionBlock)
                .withActivations(activations)
                .build();
            storageProvider.setActiveFederationCreationBlockHeight(activeFederationCreationBlockHeight);
            storageProvider.setNextFederationCreationBlockHeight(nextFederationCreationBlockHeight);

            long activeFedCreationBlockHeight = federationSupport.getActiveFederationCreationBlockHeight();
            assertThat(activeFedCreationBlockHeight, is(expectedActiveFederationCreationBlockHeight));
        }

        private Stream<Arguments> newAndActiveFederationCreationBlockHeightsAndCurrentBlockAndActivationsArgs() {
            return Stream.of(
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop - 1, hopActivations, activeFederationCreationBlockHeight),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot - 1, fingerrootActivations, activeFederationCreationBlockHeight),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop, fingerrootActivations, activeFederationCreationBlockHeight),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop, hopActivations, nextFederationCreationBlockHeight),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot, fingerrootActivations, nextFederationCreationBlockHeight),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot, hopActivations, nextFederationCreationBlockHeight)
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("update federation creation block height tests")
    class UpdateFederationCreationBlockHeightTests {
        // this method updates its provider's block heights.
        // if RSKIP186 is not active yet,
        // method should do nothing.
        // if RSKIP186 is active:
        // if nextFederationCreationBlockHeight is not set,
        // method should do nothing.
        // if nextFederationCreationBlockHeight is set, method should:
        // if not enough blocks have passed, do nothing.
        // if enough blocks have passed,
        // update activeFederationCreationBlockHeight with nextFederationCreationBlockHeight
        // and clear nextFederationCreationBlockHeight by setting a -1L.

        long nextFederationCreationBlockHeight = 200L;

        // get activation age and activations for hop and fingerroot
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        long newFederationActivationAgeHop = federationMainnetConstants.getFederationActivationAge(hopActivations);
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        long newFederationActivationAgeFingerroot = federationMainnetConstants.getFederationActivationAge(fingerrootActivations);
        ActivationConfig.ForBlock activations;

        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            activations = mock(ActivationConfig.ForBlock.class);
            when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activations)
                .build();
        }

        @Test
        @Tag("updateFederationCreationBlockHeights")
        void updateFederationCreationBlockHeights_beforeRSKIP186_doesNothing() {
            ActivationConfig.ForBlock activationsPreIris = ActivationConfigsForTest.papyrus200().forBlock(0L);
            FederationSupport federationSupportPreIris = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activationsPreIris)
                .build();

            federationSupportPreIris.updateFederationCreationBlockHeights();
            Optional<Long> activeFederationCreationBlockHeight = storageProvider.getActiveFederationCreationBlockHeight(activationsPreIris);

            assertFalse(activeFederationCreationBlockHeight.isPresent());
        }

        @Test
        @Tag("updateFederationCreationBlockHeights")
        void updateFederationCreationBlockHeights_withNextFederationCreationBlockHeightUnset_doesNothing() {
            assertFalse(storageProvider.getActiveFederationCreationBlockHeight(activations).isPresent());
        }

        @ParameterizedTest
        @Tag("updateFederationCreationBlockHeights")
        @MethodSource("newFederationNotActiveActivationArgs")
        void updateFederationCreationBlockHeights_withNextFederationCreationBlockHeightSetButInactiveNewFederation_doesNothing(long currentBlockNumber, ActivationConfig.ForBlock activations) {
            Block block = mock(Block.class);
            when(block.getNumber()).thenReturn(currentBlockNumber);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(block)
                .withActivations(activations)
                .build();
            storageProvider.setNextFederationCreationBlockHeight(nextFederationCreationBlockHeight);

            federationSupport.updateFederationCreationBlockHeights();
            assertFalse(storageProvider.getActiveFederationCreationBlockHeight(activations).isPresent());
        }

        @ParameterizedTest
        @Tag("updateFederationCreationBlockHeights")
        @MethodSource("newFederationActiveActivationArgs")
        void updateFederationCreationBlockHeights_withNextFederationCreationBlockHeightSetAndActiveNewFederation_shouldUpdateBlockHeights(long currentBlockNumber, ActivationConfig.ForBlock activations) {
            Block block = mock(Block.class);
            when(block.getNumber()).thenReturn(currentBlockNumber);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(block)
                .withActivations(activations)
                .build();
            storageProvider.setNextFederationCreationBlockHeight(nextFederationCreationBlockHeight);

            federationSupport.updateFederationCreationBlockHeights();
            assertTrue(storageProvider.getActiveFederationCreationBlockHeight(activations).isPresent());
            assertThat(storageProvider.getActiveFederationCreationBlockHeight(activations).get(), is(nextFederationCreationBlockHeight));
            assertTrue(storageProvider.getNextFederationCreationBlockHeight(activations).isPresent());
            assertThat(storageProvider.getNextFederationCreationBlockHeight(activations).get(), is(-1L));
        }

        private Stream<Arguments> newFederationNotActiveActivationArgs() {
            return Stream.of(
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop - 1, hopActivations),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot - 1, fingerrootActivations),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop, fingerrootActivations)
            );
        }

        private Stream<Arguments> newFederationActiveActivationArgs() {
            return Stream.of(
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeHop, hopActivations),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot, fingerrootActivations),
                Arguments.of(nextFederationCreationBlockHeight + newFederationActivationAgeFingerroot, hopActivations)
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("null federations")
    class RetiringFederationTestsWithNullFederations {
        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();
        }

        @Test
        @Tag("getRetiringFederation")
        void getRetiringFederation_returnsNull() {
            Federation retiringFederation = federationSupport.getRetiringFederation();
            assertThat(retiringFederation, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationAddress")
        void getRetiringFederationAddress_returnsNull() {
            Address retiringFederationAddress = federationSupport.getRetiringFederationAddress();
            assertThat(retiringFederationAddress, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationSize")
        void getRetiringFederationSize_returnsRetiringFederationNonExistentResponseCode() {
            int retiringFederationSize = federationSupport.getRetiringFederationSize();
            assertThat(retiringFederationSize, is(FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @Test
        @Tag("getRetiringFederationThreshold")
        void getRetiringFederationThreshold_returnsRetiringFederationNonExistentResponseCode() {
            int retiringFederationThreshold = federationSupport.getRetiringFederationThreshold();
            assertThat(retiringFederationThreshold, is(FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @Test
        @Tag("getRetiringFederationCreationTime")
        void getRetiringFederationCreationTime_returnsNull() {
            Instant retiringFederationCreationTime = federationSupport.getRetiringFederationCreationTime();
            assertThat(retiringFederationCreationTime, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationCreationBlockNumber")
        void getRetiringFederationCreationBlockNumber_returnsRetiringFederationNonExistentResponseCode() {
            long retiringFederationCreationBlockNumber = federationSupport.getRetiringFederationCreationBlockNumber();
            assertThat(retiringFederationCreationBlockNumber, is((long) FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @Test
        @Tag("getRetiringFederatorBtcPublicKey")
        void getRetiringFederatorBtcPublicKey_returnsNull() {
            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorBtcPublicKey(0);
            assertThat(retiringFederatorBtcPublicKey, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederatorPublicKeyOfType")
        void getRetiringFederatorPublicKeyOfType_returnsNull() {
            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(retiringFederatorBtcPublicKey, is(nullValue()));

            byte[] retiringFederatorRskPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(retiringFederatorRskPublicKey, is(nullValue()));

            byte[] retiringFederatorMstPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(retiringFederatorMstPublicKey, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationBtcUTXOs")
        void getRetiringFederationUTXOs_returnsEmptyList() {
            List<UTXO> retiringFederationUTXOs = federationSupport.getRetiringFederationBtcUTXOs();
            assertThat(retiringFederationUTXOs, is(Collections.emptyList()));
        }
    }

    @Nested
    @Tag("null old federation, non null new federation")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class RetiringFederationTestsWithNullOldFederation {
        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            // create new federation
            newFederation = P2shErpFederationBuilder.builder().build();

            storageProvider.setNewFederation(newFederation);
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();
        }

        @Test
        @Tag("getRetiringFederation")
        void getRetiringFederation_returnsNull() {
            Federation retiringFederation = federationSupport.getRetiringFederation();
            assertThat(retiringFederation, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationAddress")
        void getRetiringFederationAddress_returnsNull() {
            Address retiringFederationAddress = federationSupport.getRetiringFederationAddress();
            assertThat(retiringFederationAddress, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationSize")
        void getRetiringFederationSize_returnsRetiringFederationNonExistentResponseCode() {
            int retiringFederationSize = federationSupport.getRetiringFederationSize();
            assertThat(retiringFederationSize, is(FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @Test
        @Tag("getRetiringFederationThreshold")
        void getRetiringFederationThreshold_returnsRetiringFederationNonExistentResponseCode() {
            int retiringFederationThreshold = federationSupport.getRetiringFederationThreshold();
            assertThat(retiringFederationThreshold, is(FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @Test
        @Tag("getRetiringFederationCreationTime")
        void getRetiringFederationCreationTime_returnsNull() {
            Instant retiringFederationCreationTime = federationSupport.getRetiringFederationCreationTime();
            assertThat(retiringFederationCreationTime, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationCreationBlockNumber")
        void getRetiringFederationCreationBlockNumber_returnsRetiringFederationNonExistentResponseCode() {
            long retiringFederationCreationBlockNumber = federationSupport.getRetiringFederationCreationBlockNumber();
            assertThat(retiringFederationCreationBlockNumber, is((long) FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @Test
        @Tag("getRetiringFederatorBtcPublicKey")
        void getRetiringFederatorBtcPublicKey_returnsNull() {
            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorBtcPublicKey(0);
            assertThat(retiringFederatorBtcPublicKey, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederatorPublicKeyOfType")
        void getRetiringFederatorPublicKeyOfType_returnsNull() {
            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(retiringFederatorBtcPublicKey, is(nullValue()));

            byte[] retiringFederatorRskPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(retiringFederatorRskPublicKey, is(nullValue()));

            byte[] retiringFederatorMstPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(retiringFederatorMstPublicKey, is(nullValue()));
        }

        @Test
        @Tag("getRetiringFederationBtcUTXOs")
        void getRetiringFederationUTXOs_returnsEmptyList() {
            // set UTXOs for new federation since there is no retiring federation
            List<UTXO> newFederationUTXOs = BitcoinTestUtils.createUTXOs(10, newFederation.getAddress());
            storageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), newFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            List<UTXO> retiringFederationUTXOs = federationSupport.getRetiringFederationBtcUTXOs();
            assertThat(retiringFederationUTXOs, is(Collections.emptyList()));
        }
    }

    @Nested
    @Tag("non null federations")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class RetiringFederationTestsWithNonNullFederations {
        // new federation should be active if we are past the activation block number
        // old federation should be active if we are before the activation block number
        // activation block number is smaller for hop than for fingerroot

        // create old and new federations
        long oldFederationCreationBlockNumber = 20;
        long newFederationCreationBlockNumber = 65;
        Federation oldFederation = P2shErpFederationBuilder.builder()
            .withCreationBlockNumber(oldFederationCreationBlockNumber)
            .build();
        List<BtcECKey> newFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
        );
        Federation newFederation = P2shErpFederationBuilder.builder()
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
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);
            storageProvider.setOldFederation(oldFederation);
            storageProvider.setNewFederation(newFederation);
        }

        @ParameterizedTest
        @Tag("getRetiringFederation")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederation_withNewFederationNotActive_returnsNull(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Federation retiringFederation = federationSupport.getRetiringFederation();
            assertThat(retiringFederation, is(nullValue()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederation")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederation_withNewFederationActive_returnsOldFederation(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Federation retiringFederation = federationSupport.getRetiringFederation();
            assertThat(retiringFederation, is(oldFederation));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationAddress")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederationAddress_withNewFederationNotActive_returnsNull(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Address retiringFederationAddress = federationSupport.getRetiringFederationAddress();
            assertThat(retiringFederationAddress, is(nullValue()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederation")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederationAddress_withNewFederationActive_returnsOldFederationAddress(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Address retiringFederationAddress = federationSupport.getRetiringFederationAddress();
            assertThat(retiringFederationAddress, is(oldFederation.getAddress()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationSize")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederationSize_withNewFederationNotActive_returnsRetiringFederationNonExistentResponseCode(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            int retiringFederationSize = federationSupport.getRetiringFederationSize();
            assertThat(retiringFederationSize, is(FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationSize")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederationSize_withNewFederationActive_returnsOldFederationSize(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            int retiringFederationSize = federationSupport.getRetiringFederationSize();
            assertThat(retiringFederationSize, is(oldFederation.getSize()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationThreshold")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederationThreshold_withNewFederationNotActive_returnsRetiringFederationNonExistentResponseCode(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            int retiringFederationThreshold = federationSupport.getRetiringFederationThreshold();
            assertThat(retiringFederationThreshold, is(FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationThreshold")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederationThreshold_withNewFederationActive_returnsOldFederationThreshold(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            int retiringFederationThreshold = federationSupport.getRetiringFederationThreshold();
            assertThat(retiringFederationThreshold, is(oldFederation.getNumberOfSignaturesRequired()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationCreationTime")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederationCreationTime_withNewFederationNotActive_returnsNull(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Instant retiringFederationCreationTime = federationSupport.getRetiringFederationCreationTime();
            assertThat(retiringFederationCreationTime, is(nullValue()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationCreationTime")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederationCreationTime_withNewFederationActive_returnsOldFederationCreationTime(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Instant retiringFederationCreationTime = federationSupport.getRetiringFederationCreationTime();
            assertThat(retiringFederationCreationTime, is(oldFederation.getCreationTime()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationCreationBlockNumber")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederationCreationBlockNumber_withNewFederationNotActive_returnsRetiringFederationNonExistentResponseCode(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            long retiringFederationCreationBlockNumber = federationSupport.getRetiringFederationCreationBlockNumber();
            assertThat(retiringFederationCreationBlockNumber, is((long) FederationChangeResponseCode.RETIRING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationCreationBlockNumber")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederationCreationBlockNumber_withNewFederationActive_returnsOldFederationCreationBlockNumber(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            long retiringFederationCreationBlockNumber = federationSupport.getRetiringFederationCreationBlockNumber();
            assertThat(retiringFederationCreationBlockNumber, is(oldFederation.getCreationBlockNumber()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorBtcPublicKey")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederatorBtcPublicKey_withNewFederationNotActive_returnsNull(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorBtcPublicKey(0);
            assertThat(retiringFederatorBtcPublicKey, is(nullValue()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorBtcPublicKey")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederatorBtcPublicKey_withNewFederationActive_returnsFederatorFromOldFederationBtcPublicKey(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorBtcPublicKey(0);
            assertThat(retiringFederatorBtcPublicKey, is(oldFederation.getBtcPublicKeys().get(0).getPubKey()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorBtcPublicKey")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederatorBtcPublicKey_withNewFederationActiveAndNegativeIndex_throwsIndexOutOfBoundsException(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            assertThrows(IndexOutOfBoundsException.class, () -> federationSupport.getRetiringFederatorBtcPublicKey(-1));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorBtcPublicKey")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederatorBtcPublicKey_withNewFederationActiveAndIndexGreaterThanRetiringFederationSize_throwsIndexOutOfBoundsException(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            int retiringFederationSize = oldFederation.getSize();
            assertThrows(IndexOutOfBoundsException.class, () -> federationSupport.getRetiringFederatorBtcPublicKey(retiringFederationSize));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorPublicKeyOfType")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederatorPublicKeyOfType_withNewFederationNotActive_returnsNull(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(retiringFederatorBtcPublicKey, is(nullValue()));

            byte[] retiringFederatorRskPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(retiringFederatorRskPublicKey, is(nullValue()));

            byte[] retiringFederatorMstPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(retiringFederatorMstPublicKey, is(nullValue()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorPublicKeyOfType")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederatorPublicKeyOfType_withNewFederationActive_returnsFederatorFromOldFederationPublicKeys(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            BtcECKey federatorFromOldFederationBtcPublicKey = oldFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromOldFederationRskPublicKey = getRskPublicKeysFromFederationMembers(oldFederation.getMembers()).get(0);
            ECKey federatorFromOldFederationMstPublicKey = getMstPublicKeysFromFederationMembers(oldFederation.getMembers()).get(0);

            // since old federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(federatorFromOldFederationBtcPublicKey.getPubKey());
            assertThat(federatorFromOldFederationRskPublicKey, is(ecKeyDerivedFromBtcKey));
            // since old federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(federatorFromOldFederationMstPublicKey, is(federatorFromOldFederationRskPublicKey));

            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(retiringFederatorBtcPublicKey, is(federatorFromOldFederationBtcPublicKey.getPubKey()));

            byte[] retiringFederatorRskPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(retiringFederatorRskPublicKey, is(federatorFromOldFederationRskPublicKey.getPubKey(true)));

            byte[] retiringFederatorMstPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(retiringFederatorMstPublicKey, is(federatorFromOldFederationMstPublicKey.getPubKey(true)));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorPublicKeyOfType")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederatorPublicKeyOfType_withSpecificRskKeysAndNewFederationActive_returnsExpectedFederatorPublicKeys(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            // create old federation with specific rsk public keys
            List<ECKey> rskECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            Federation oldFederationWithRskKeys = P2shErpFederationBuilder.builder()
                .withMembersRskPublicKeys(rskECKeys)
                .build();
            storageProvider.setOldFederation(oldFederationWithRskKeys);

            BtcECKey federatorFromOldFederationBtcPublicKey = oldFederationWithRskKeys.getBtcPublicKeys().get(0);
            ECKey federatorFromOldFederationRskPublicKey = getRskPublicKeysFromFederationMembers(oldFederationWithRskKeys.getMembers()).get(0);
            ECKey federatorFromOldFederationMstPublicKey = getMstPublicKeysFromFederationMembers(oldFederationWithRskKeys.getMembers()).get(0);

            // since old federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(federatorFromOldFederationMstPublicKey, is(federatorFromOldFederationRskPublicKey));

            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(retiringFederatorBtcPublicKey, is(federatorFromOldFederationBtcPublicKey.getPubKey()));

            byte[] retiringFederatorRskPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(retiringFederatorRskPublicKey, is(federatorFromOldFederationRskPublicKey.getPubKey(true)));

            byte[] retiringFederatorMstPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(retiringFederatorMstPublicKey, is(federatorFromOldFederationMstPublicKey.getPubKey(true)));
        }

        @ParameterizedTest
        @Tag("getRetiringFederatorPublicKeyOfType")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederatorPublicKeyOfType_withSpecificRskAndMstKeysAndNewFederationActive_returnsExpectedFederatorPublicKeys(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            // create old federation with specific rsk and mst public keys
            List<ECKey> rskECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            List<ECKey> mstECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"mst01", "mst02", "mst03", "mst04", "mst05", "mst06", "mst07", "mst08", "mst09"}
            );
            Federation oldFederationWithRskAndMstKeys = P2shErpFederationBuilder.builder()
                .withMembersRskPublicKeys(rskECKeys)
                .withMembersMstPublicKeys(mstECKeys)
                .build();
            storageProvider.setOldFederation(oldFederationWithRskAndMstKeys);

            BtcECKey federatorFromOldFederationBtcPublicKey = oldFederationWithRskAndMstKeys.getBtcPublicKeys().get(0);
            ECKey federatorFromOldFederationRskPublicKey = getRskPublicKeysFromFederationMembers(oldFederationWithRskAndMstKeys.getMembers()).get(0);
            ECKey federatorFromOldFederationMstPublicKey = getMstPublicKeysFromFederationMembers(oldFederationWithRskAndMstKeys.getMembers()).get(0);

            byte[] retiringFederatorBtcPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(retiringFederatorBtcPublicKey, is(federatorFromOldFederationBtcPublicKey.getPubKey()));

            byte[] retiringFederatorRskPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(retiringFederatorRskPublicKey, is(federatorFromOldFederationRskPublicKey.getPubKey(true)));

            byte[] retiringFederatorMstPublicKey = federationSupport.getRetiringFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(retiringFederatorMstPublicKey, is(federatorFromOldFederationMstPublicKey.getPubKey(true)));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationUTXOs")
        @MethodSource("newFederationNotActiveActivationArgs")
        void getRetiringFederationBtcUTXOs_withNewFederationNotActive_returnsEmptyList(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            // set UTXOs for both feds
            List<UTXO> oldFederationUTXOs = BitcoinTestUtils.createUTXOs(5, oldFederation.getAddress());
            storageAccessor.saveToRepository(OLD_FEDERATION_BTC_UTXOS_KEY.getKey(), oldFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);
            List<UTXO> newFederationUTXOs = BitcoinTestUtils.createUTXOs(10, newFederation.getAddress());
            storageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), newFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            List<UTXO> retiringFederationUTXOs = federationSupport.getRetiringFederationBtcUTXOs();
            assertThat(retiringFederationUTXOs, is(Collections.emptyList()));
        }

        @ParameterizedTest
        @Tag("getRetiringFederationUTXOs")
        @MethodSource("newFederationActiveActivationArgs")
        void getRetiringFederationBtcUTXOs_withNewFederationActive_returnsOldFederationBtcUTXOs(
            long currentBlock,
            ActivationConfig.ForBlock activations) {

            Block executionBlock = mock(Block.class);
            when(executionBlock.getNumber()).thenReturn(currentBlock);

            // set UTXOs for both feds
            List<UTXO> oldFederationUTXOs = BitcoinTestUtils.createUTXOs(5, oldFederation.getAddress());
            storageAccessor.saveToRepository(OLD_FEDERATION_BTC_UTXOS_KEY.getKey(), oldFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);
            List<UTXO> newFederationUTXOs = BitcoinTestUtils.createUTXOs(10, newFederation.getAddress());
            storageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), newFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            List<UTXO> retiringFederationUTXOs = federationSupport.getRetiringFederationBtcUTXOs();
            assertThat(retiringFederationUTXOs, is(oldFederationUTXOs));
        }

        private Stream<Arguments> newFederationNotActiveActivationArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop - 1, hopActivations),
                Arguments.of(blockNumberFederationActivationHop, fingerrootActivations),
                Arguments.of(blockNumberFederationActivationFingerroot - 1, fingerrootActivations)
            );
        }

        private Stream<Arguments> newFederationActiveActivationArgs() {
            return Stream.of(
                Arguments.of(blockNumberFederationActivationHop, hopActivations),
                Arguments.of(blockNumberFederationActivationFingerroot, fingerrootActivations),
                Arguments.of(blockNumberFederationActivationFingerroot, hopActivations)
            );
        }
    }

    @Nested
    @Tag("null pending federation")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PendingFederationTestsWithNullFederation {

        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();
        }

        @Test
        @Tag("getPendingFederationSize")
        void getPendingFederationSize_returnsPendingFederationNonExistentResponseCode() {
            int pendingFederationSize = federationSupport.getPendingFederationSize();
            assertThat(pendingFederationSize, is(FederationChangeResponseCode.PENDING_FEDERATION_NON_EXISTENT.getCode()));
        }

        @Test
        @Tag("getPendingFederationHash")
        void getPendingFederationHash_returnsNull() {
            Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();
            assertThat(pendingFederationHash, is(nullValue()));
        }

        @Test
        @Tag("getPendingFederatorBtcPublicKey")
        void getPendingFederatorBtcPublicKey_returnsNull() {
            byte[] pendingFederatorBtcPublicKey = federationSupport.getPendingFederatorBtcPublicKey(0);
            assertThat(pendingFederatorBtcPublicKey, is(nullValue()));
        }

        @Test
        @Tag("getPendingFederatorPublicKeyOfType")
        void getPendingFederatorPublicKeyOfType_returnsNull() {
            byte[] pendingFederatorBtcPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(pendingFederatorBtcPublicKey, is(nullValue()));

            byte[] pendingFederatorRskPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(pendingFederatorRskPublicKey, is(nullValue()));

            byte[] pendingFederatorMstPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(pendingFederatorMstPublicKey, is(nullValue()));
        }
    }

    @Nested
    @Tag("non null pending federation")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PendingFederationTestsWithNonNullFederation {

        PendingFederation pendingFederation = PendingFederationBuilder.builder().build();

        @BeforeEach
        void setUp() {
            storageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(storageAccessor);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .build();

            storageProvider.setPendingFederation(pendingFederation);
        }

        @Test
        @Tag("getPendingFederationSize")
        void getPendingFederationSize_returnsPendingFederationSize() {
            int pendingFederationSize = federationSupport.getPendingFederationSize();
            assertThat(pendingFederationSize, is(pendingFederation.getSize()));
        }

        @Test
        @Tag("getPendingFederationHash")
        void getPendingFederationHash_returnsPendingFederationHash() {
            Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();
            assertThat(pendingFederationHash, is(pendingFederation.getHash()));
        }

        @Test
        @Tag("getPendingFederatorBtcPublicKey")
        void getPendingFederatorBtcPublicKey_returnsFederatorFromPendingFederationBtcPublicKey() {
            byte[] pendingFederatorBtcPublicKey = federationSupport.getPendingFederatorBtcPublicKey(0);
            assertThat(pendingFederatorBtcPublicKey, is(pendingFederation.getBtcPublicKeys().get(0).getPubKey()));
        }

        @Test
        @Tag("getPendingFederatorBtcPublicKey")
        void getPendingFederatorBtcPublicKey_withNegativeIndex_throwsIndexOutOfBoundsException() {
            assertThrows(IndexOutOfBoundsException.class, () -> federationSupport.getPendingFederatorBtcPublicKey(-1));
        }

        @Test
        @Tag("getPendingFederatorBtcPublicKey")
        void getPendingFederatorBtcPublicKey_withIndexGreaterThanPendingFederationSize_throwsIndexOutOfBoundsException() {
            int pendingFederationSize = pendingFederation.getSize();
            assertThrows(IndexOutOfBoundsException.class, () -> federationSupport.getPendingFederatorBtcPublicKey(pendingFederationSize));
        }

        @Test
        @Tag("getPendingFederatorPublicKeyOfType")
        void getPendingFederatorPublicKeyOfType_returnsFederatorFromPendingFederationPublicKeys() {
            BtcECKey federatorFromPendingFederationBtcPublicKey = pendingFederation.getBtcPublicKeys().get(0);
            ECKey federatorFromPendingFederationRskPublicKey = getRskPublicKeysFromFederationMembers(pendingFederation.getMembers()).get(0);
            ECKey federatorFromPendingFederationMstPublicKey = getMstPublicKeysFromFederationMembers(pendingFederation.getMembers()).get(0);

            // since pending federation was created without specifying rsk public keys
            // these are set deriving the btc public keys,
            // so we should first assert that
            ECKey ecKeyDerivedFromBtcKey = ECKey.fromPublicOnly(federatorFromPendingFederationBtcPublicKey.getPubKey());
            assertThat(federatorFromPendingFederationRskPublicKey, is(ecKeyDerivedFromBtcKey));
            // since pending federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(federatorFromPendingFederationMstPublicKey, is(federatorFromPendingFederationRskPublicKey));

            byte[] pendingFederatorBtcPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(pendingFederatorBtcPublicKey, is(federatorFromPendingFederationBtcPublicKey.getPubKey()));

            byte[] pendingFederatorRskPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(pendingFederatorRskPublicKey, is(federatorFromPendingFederationRskPublicKey.getPubKey(true)));

            byte[] pendingFederatorMstPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(pendingFederatorMstPublicKey, is(federatorFromPendingFederationMstPublicKey.getPubKey(true)));
        }

        @Test
        @Tag("getPendingFederatorPublicKeyOfType")
        void getPendingFederatorPublicKeyOfType_withSpecificRskKeys_returnsFederatorFromPendingFederationPublicKeys() {
            // create pending federation with specific rsk public keys
            List<ECKey> rskECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            PendingFederation pendingFederationWithRskKeys = PendingFederationBuilder.builder()
                .withMembersRskPublicKeys(rskECKeys)
                .build();
            storageProvider.setPendingFederation(pendingFederationWithRskKeys);

            BtcECKey federatorFromPendingFederationBtcPublicKey = pendingFederationWithRskKeys.getBtcPublicKeys().get(0);
            ECKey federatorFromPendingFederationRskPublicKey = getRskPublicKeysFromFederationMembers(pendingFederationWithRskKeys.getMembers()).get(0);
            ECKey federatorFromPendingFederationMstPublicKey = getMstPublicKeysFromFederationMembers(pendingFederationWithRskKeys.getMembers()).get(0);

            // since pending federation was created without specifying mst public keys
            // these are set copying the rsk public keys,
            // so we should first assert that
            assertThat(federatorFromPendingFederationMstPublicKey, is(federatorFromPendingFederationRskPublicKey));

            byte[] pendingFederatorBtcPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(pendingFederatorBtcPublicKey, is(federatorFromPendingFederationBtcPublicKey.getPubKey()));

            byte[] pendingFederatorRskPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(pendingFederatorRskPublicKey, is(federatorFromPendingFederationRskPublicKey.getPubKey(true)));

            byte[] pendingFederatorMstPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(pendingFederatorMstPublicKey, is(federatorFromPendingFederationMstPublicKey.getPubKey(true)));
        }

        @Test
        @Tag("getPendingFederatorPublicKeyOfType")
        void getPendingFederatorPublicKeyOfType_withSpecificRskAndMstKeys_returnsFederatorFromPendingFederationPublicKeys() {
            // create pending federation with specific rsk and mst public keys
            List<ECKey> rskECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"rsk01", "rsk02", "rsk03", "rsk04", "rsk05", "rsk06", "rsk07", "rsk08", "rsk09"}
            );
            List<ECKey> mstECKeys = RskTestUtils.getEcKeysFromSeeds(
                new String[]{"mst01", "mst02", "mst03", "mst04", "mst05", "mst06", "mst07", "mst08", "mst09"}
            );
            PendingFederation pendingFederationWithRskAndMstKeys = PendingFederationBuilder.builder()
                .withMembersRskPublicKeys(rskECKeys)
                .withMembersMstPublicKeys(mstECKeys)
                .build();
            storageProvider.setPendingFederation(pendingFederationWithRskAndMstKeys);

            BtcECKey federatorFromPendingFederationBtcPublicKey = pendingFederationWithRskAndMstKeys.getBtcPublicKeys().get(0);
            ECKey federatorFromPendingFederationRskPublicKey = getRskPublicKeysFromFederationMembers(pendingFederationWithRskAndMstKeys.getMembers()).get(0);
            ECKey federatorFromPendingFederationMstPublicKey = getMstPublicKeysFromFederationMembers(pendingFederationWithRskAndMstKeys.getMembers()).get(0);

            byte[] pendingFederatorBtcPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC);
            assertThat(pendingFederatorBtcPublicKey, is(federatorFromPendingFederationBtcPublicKey.getPubKey()));

            byte[] pendingFederatorRskPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK);
            assertThat(pendingFederatorRskPublicKey, is(federatorFromPendingFederationRskPublicKey.getPubKey(true)));

            byte[] pendingFederatorMstPublicKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST);
            assertThat(pendingFederatorMstPublicKey, is(federatorFromPendingFederationMstPublicKey.getPubKey(true)));
        }
    }

    @Test
    @Tag("new federation btc utxos")
    void getNewFederationBtcUTXOs_whenNoUTXOsWereSaved_returnsEmptyList() {
        List<UTXO> newFederationUTXOs = federationSupport.getNewFederationBtcUTXOs();
        assertThat(newFederationUTXOs, is(Collections.emptyList()));
    }

    @Test
    @Tag("new federation btc utxos")
    void getNewFederationBtcUTXOs_whenSavingUTXOs_returnsNewFederationUTXOs() {
        Address btcAddress = BitcoinTestUtils.createP2PKHAddress(federationMainnetConstants.getBtcParams(), "address");
        List<UTXO> newFederationUTXOs = BitcoinTestUtils.createUTXOs(10, btcAddress);

        storageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), newFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

        List<UTXO> actualNewFederationUTXOs = federationSupport.getNewFederationBtcUTXOs();
        assertThat(actualNewFederationUTXOs, is(newFederationUTXOs));
    }

    @Test
    @Tag("last retired federation p2sh script")
    void getLastRetiredFederationP2SHScript_beforeRSKIP186_returnsOptionalEmpty() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(false);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withActivations(activations)
            .build();

        Optional<Script> lastRetiredFederationP2SHScript = federationSupport.getLastRetiredFederationP2SHScript();
        assertThat(lastRetiredFederationP2SHScript, is(Optional.empty()));
    }

    @Test
    @Tag("last retired federation p2sh script")
    void getLastRetiredFederationP2SHScript_afterRSKIP186_whenNoScriptWasSaved_returnsOptionalEmpty() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withActivations(activations)
            .build();

        Optional<Script> lastRetiredFederationP2SHScript = federationSupport.getLastRetiredFederationP2SHScript();
        assertThat(lastRetiredFederationP2SHScript, is(Optional.empty()));
    }

    @Test
    @Tag("last retired federation p2sh script")
    void getLastRetiredFederationP2SHScript_afterRSKIP186_whenSavingScript_returnsScript() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withActivations(activations)
            .build();

        // get a real p2sh script
        ErpFederation federation = P2shErpFederationBuilder.builder().build();
        Script p2shScript = federation.getDefaultP2SHScript();

        storageProvider.setLastRetiredFederationP2SHScript(p2shScript);
        Optional<Script> lastRetiredFederationP2SHScript = federationSupport.getLastRetiredFederationP2SHScript();

        assertTrue(lastRetiredFederationP2SHScript.isPresent());
        assertThat(lastRetiredFederationP2SHScript.get(), is(p2shScript));
    }

    @Test
    @Tag("clear retired federation")
    void clearRetiredFederation_whenHavingOldFederation_removesOldFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        ErpFederation federation = P2shErpFederationBuilder.builder().build();
        storageProvider.setOldFederation(federation);

        // check the old federation was correctly saved
        Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
        assertThat(oldFederation, is(federation));

        federationSupport.clearRetiredFederation();
        // check the old federation was removed
        oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
        assertThat(oldFederation, is(nullValue()));
    }

    @Test
    @Tag("save")
    void save_callsStorageProviderSave() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        storageProvider = mock(FederationStorageProviderImpl.class);
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withActivations(activations)
            .build();

        federationSupport.save();
        verify(storageProvider).save(federationMainnetConstants.getBtcParams(), activations);
    }

    @Nested
    @Tag("vote federation change")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class VoteFederationChangeTest {

        private final long rskExecutionBlockNumber = 1000L;
        private final long rskExecutionBlockTimestamp = 10L;
        private final CallTransaction.Function commitFederationEvent = BridgeEvents.COMMIT_FEDERATION.getEvent();

        private ActivationConfig.ForBlock activations;
        private ABICallSpec createFederationAbiCallSpec;
        private Transaction firstAuthorizedTx;
        private Transaction secondAuthorizedTx;
        private List<LogInfo> logs;
        private BridgeEventLogger bridgeEventLogger;
        private FederationStorageProvider storageProvider;
        private FederationSupport federationSupport;
        private Block rskExecutionBlock;
        private PendingFederation pendingFederationToBe;

        @BeforeEach
        void setUp() {
            signatureCache = mock(SignatureCache.class);
            firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});

            activations = ActivationConfigsForTest.all().forBlock(0L);

            logs = new ArrayList<>();
            bridgeEventLogger = new BridgeEventLoggerImpl(BridgeMainNetConstants.getInstance(), activations, logs, signatureCache);

            StorageAccessor inMemoryStorageAccessor = new InMemoryStorage();
            storageProvider = new FederationStorageProviderImpl(inMemoryStorageAccessor);

            BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
                .setNumber(rskExecutionBlockNumber)
                .setTimestamp(rskExecutionBlockTimestamp)
                .build();
            rskExecutionBlock = Block.createBlockFromHeader(blockHeader, true);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(rskExecutionBlock)
                .withActivations(activations)
                .build();

            pendingFederationToBe = new PendingFederation(FederationTestUtils.getFederationMembers(9));
        }

        @Test
        void voteCommitFederation_preRSKIP186_whenPendingFederationIsSet_shouldPerformLegacyCommitFederationActionsButNotSetFederationChangeInfo() {
            // arrange
            activations = ActivationConfigsForTest.papyrus200().forBlock(0L);
            bridgeEventLogger = new BridgeEventLoggerImpl(BridgeMainNetConstants.getInstance(), activations, logs, signatureCache);
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(rskExecutionBlock)
                .withActivations(activations)
                .build();

            List<UTXO> utxosToMove = new ArrayList<>(storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations));

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);
            voteToAddFederatorPublicKeysToPendingFederation();

            // act
            voteToCommitPendingFederation();


            // assertions

            assertUTXOsWereMovedFromNewToOldFederation(utxosToMove);

            assertNewAndOldFederationsWereSet();

            assertPendingFederationVotingWasCleaned();

            assertFederationChangeInfoWasNotSet();

            Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
            Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);
            assertLogCommitFederation(oldFederation, newFederation);
        }

        @Test
        void voteCommitFederation_postRSKIP186_preRSKIP419_whenPendingFederationIsSet_shouldPerformLegacyCommitFederationActions() {
            // arrange
            activations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeEventLogger = new BridgeEventLoggerImpl(BridgeMainNetConstants.getInstance(), activations, logs, signatureCache);
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(rskExecutionBlock)
                .withActivations(activations)
                .build();

            List<UTXO> utxosToMove = new ArrayList<>(storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations));

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);
            voteToAddFederatorPublicKeysToPendingFederation();

            // act
            voteToCommitPendingFederation();


            // assertions

            assertUTXOsWereMovedFromNewToOldFederation(utxosToMove);

            assertNewAndOldFederationsWereSet();

            assertPendingFederationVotingWasCleaned();

            assertFederationChangeInfoWasSet();

            Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
            Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);
            assertLogCommitFederation(oldFederation, newFederation);
        }

        @Test
        void voteCommitFederation_postRSKIP419_whenPendingFederationIsSet_shouldPerformCommitFederationActions() {
            // arrange
            Federation activeFederation = federationSupport.getActiveFederation();
            Federation federationBuiltFromPendingFederation =
                pendingFederationToBe.buildFederation(Instant.ofEpochMilli(rskExecutionBlockTimestamp), rskExecutionBlockNumber, federationMainnetConstants, activations);

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);
            voteToAddFederatorPublicKeysToPendingFederation();

            // act
            voteToCommitPendingFederation();


            // assertions

            // assert proposed federation was set correctly
            Optional<Federation> proposedFederation =
                storageProvider.getProposedFederation(federationMainnetConstants, activations);
            assertTrue(proposedFederation.isPresent());
            assertEquals(federationBuiltFromPendingFederation, proposedFederation.get());

            assertPendingFederationVotingWasCleaned();

            assertFederationChangeInfoWasSet();

            assertLogCommitFederation(activeFederation, proposedFederation.get());

            // assert new and old federation were not set and utxos were not moved
            assertNewAndOldFederationsWereNotSet();
            assertUTXOsWereNotMovedFromNewToOldFederation();
        }

        private void assertUTXOsWereMovedFromNewToOldFederation(List<UTXO> utxosToMove) {
            // assert utxos were moved from new federation to old federation
            List<UTXO> oldFederationUTXOs = storageProvider.getOldFederationBtcUTXOs();
            assertEquals(utxosToMove, oldFederationUTXOs);

            // assert new federation utxos were cleaned
            List<UTXO> newFederationUTXOs = storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations);
            assertTrue(newFederationUTXOs.isEmpty());
        }

        private void assertUTXOsWereNotMovedFromNewToOldFederation() {
            // assert old and new federation utxos are still empty
            List<UTXO> oldFederationUTXOs = storageProvider.getOldFederationBtcUTXOs();
            List<UTXO> newFederationUTXOs = storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations);

            assertTrue(oldFederationUTXOs.isEmpty());
            assertTrue(newFederationUTXOs.isEmpty());
        }

        private void assertNewAndOldFederationsWereSet() {
            // assert old federation was set as the active federation
            Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
            assertEquals(federationSupport.getActiveFederation(), oldFederation);

            // assert new federation was set as the federation built from the pending one
            Federation federationBuiltFromPendingFederation =
                pendingFederationToBe.buildFederation(Instant.ofEpochMilli(rskExecutionBlockTimestamp), rskExecutionBlockNumber, federationMainnetConstants, activations);
            Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);
            assertEquals(federationBuiltFromPendingFederation, newFederation);
        }

        private void assertNewAndOldFederationsWereNotSet() {
            // assert old and new federations are still null
            Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
            Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);

            assertNull(oldFederation);
            assertNull(newFederation);
        }

        private void assertPendingFederationVotingWasCleaned() {
            assertNull(storageProvider.getPendingFederation());

            Map<ABICallSpec, List<RskAddress>> federationElectionVotes = storageProvider.getFederationElection(federationMainnetConstants.getFederationChangeAuthorizer()).getVotes();
            assertTrue(federationElectionVotes.isEmpty());
        }

        private void assertFederationChangeInfoWasSet() {
            // assert federation creation block height was set correctly
            Optional<Long> nextFederationCreationBlockHeight = storageProvider.getNextFederationCreationBlockHeight(activations);
            assertTrue(nextFederationCreationBlockHeight.isPresent());
            assertEquals(rskExecutionBlockNumber, nextFederationCreationBlockHeight.get());

            // assert last retired federation p2sh script was set correctly
            Script activeFederationMembersP2SHScript = getFederationMembersP2SHScript(activations, federationSupport.getActiveFederation());
            Optional<Script> lastRetiredFederationP2SHScript = storageProvider.getLastRetiredFederationP2SHScript(activations);
            assertTrue(lastRetiredFederationP2SHScript.isPresent());
            assertEquals(activeFederationMembersP2SHScript, lastRetiredFederationP2SHScript.get());
        }

        private Script getFederationMembersP2SHScript(ActivationConfig.ForBlock activations, Federation federation) {
            // when the federation is a standard multisig,
            // the members p2sh script is the p2sh script
            if (!activations.isActive(RSKIP377)) {
                return federation.getP2SHScript();
            }
            if (!(federation instanceof ErpFederation)) {
                return federation.getP2SHScript();
            }

            // when the federation also has erp keys,
            // the members p2sh script is the default p2sh script
            return ((ErpFederation) federation).getDefaultP2SHScript();
        }

        private void assertFederationChangeInfoWasNotSet() {
            // assert federation creation block height was not set
            Optional<Long> nextFederationCreationBlockHeight = storageProvider.getNextFederationCreationBlockHeight(activations);
            assertFalse(nextFederationCreationBlockHeight.isPresent());

            // assert last retired federation p2sh script was not set
            Optional<Script> lastRetiredFederationP2SHScript = storageProvider.getLastRetiredFederationP2SHScript(activations);
            assertFalse(lastRetiredFederationP2SHScript.isPresent());
        }

        private void assertLogCommitFederation(Federation federationToBeRetired, Federation votedFederation) {
            List<DataWord> encodedTopics = getEncodedTopics();
            byte[] encodedData = getEncodedData(federationToBeRetired, votedFederation);

            // assert the event was emitted just once with the expected topic and data
            assertEquals(1, logs.size());

            LogInfo log = logs.get(0);
            List<DataWord> topic = log.getTopics();
            assertEquals(encodedTopics.get(0), topic.get(0));
            assertArrayEquals(encodedData, log.getData());
        }

        private List<DataWord> getEncodedTopics() {
            byte[][] encodedTopicsInBytes = commitFederationEvent.encodeEventTopics();
            return LogInfo.byteArrayToList(encodedTopicsInBytes);
        }

        private byte[] getEncodedData(Federation federationToBeRetired, Federation votedFederation) {
            byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(federationToBeRetired.getBtcPublicKeys());
            String oldFederationBtcAddress = federationToBeRetired.getAddress().toBase58();
            byte[] newFederationFlatPubKeys = flatKeysAsByteArray(votedFederation.getBtcPublicKeys());
            String newFederationBtcAddress = votedFederation.getAddress().toBase58();
            long newFedActivationBlockNumber = rskExecutionBlock.getNumber() + federationMainnetConstants.getFederationActivationAge(activations);

            return commitFederationEvent.encodeEventData(
                oldFederationFlatPubKeys,
                oldFederationBtcAddress,
                newFederationFlatPubKeys,
                newFederationBtcAddress,
                newFedActivationBlockNumber
            );
        }

        @Test
        void voteFederationChange_withUnauthorizedCaller_returnsUnauthorizedResponseCode() {

            // Arrange
            Transaction firstAuthorizerTx =  TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.UNAUTHORIZED.getRskAddress()
            );
            ABICallSpec abiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});

            // Act
            int result = federationSupport.voteFederationChange(firstAuthorizerTx, abiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.UNAUTHORIZED_CALLER.getCode(), result);
        }

        @Test
        void voteFederationChange_nonExistingFunction_returnsNonExistingResponseCode() {
            // Arrange
            Transaction firstAuthorizerTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            ABICallSpec abiCallSpec = new ABICallSpec("nonExistingFunctionName", new byte[][]{});

            // Act
            int result = federationSupport.voteFederationChange(
                firstAuthorizerTx,
                abiCallSpec,
                signatureCache,
                bridgeEventLogger
            );

            // Assert
            assertEquals(FederationChangeResponseCode.NON_EXISTING_FUNCTION_CALLED.getCode(), result);
        }

        @Test
        void voteFederationChange_create_returnsSuccessfulResponseCode() {
            // Arrange
            Transaction firstAuthorizerTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            ABICallSpec abiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});

            // Act
            int result = federationSupport.voteFederationChange(
                firstAuthorizerTx,
                abiCallSpec,
                signatureCache,
                bridgeEventLogger
            );

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), result);
        }

        @Test
        void voteFederationChange_whenCreateIsCalledTwiceBySameAuthorizer_returnsGenericErrorResponseCode() {
            // Arrange
            Transaction firstAuthorizerTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            ABICallSpec abiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});

            // Act

            // First create call
            int result = federationSupport.voteFederationChange(firstAuthorizerTx, abiCallSpec, signatureCache, bridgeEventLogger);

            // Second create call
            int result2 = federationSupport.voteFederationChange(firstAuthorizerTx, abiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), result);
            assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), result2);

        }

        @Test
        void voteFederationChange_whenCreateIsCalledBy2DifferentAuthorizer_returnsSuccessfulResponseCodeAndPendingFederationCreated() {
            // Arrange
            Transaction firstAuthorizerTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            Transaction secondAuthorizerTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress()
            );
            ABICallSpec abiCallSpec = new ABICallSpec(
                FederationChangeFunction.CREATE.getKey(),
                new byte[][]{}
            );

            // Act

            // First create call
            int resultFromFirstAuthorizer = federationSupport.voteFederationChange(
                firstAuthorizerTx,
                abiCallSpec,
                signatureCache,
                bridgeEventLogger
            );

            // Second create call
            int resultFromSecondAuthorizer = federationSupport.voteFederationChange(
                secondAuthorizerTx,
                abiCallSpec,
                signatureCache,
                bridgeEventLogger
            );

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

            assertThat(federationSupport.getPendingFederationSize(), is(0));
            assertNotNull(federationSupport.getPendingFederationHash());
        }

        @Test
        void voteFederationChange_commitEmptyFederation_returnsInsufficientMembersResponseCode() {
            // Arrange
            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.THIRD_AUTHORIZED.getRskAddress()
            );

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();
            ABICallSpec commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{pendingFederationHash.getBytes()});

            // Act
            int result = federationSupport.voteFederationChange(firstAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.INSUFFICIENT_MEMBERS.getCode(), result);
        }

        @Test
        void voteFederationChange_addFederatorPublicKeysWithLessThanMofNVotes_returnsSuccessfulResponseCodeAndPendingFederationSizeZero() {
            // Arrange

            BtcECKey federatorBtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey federatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey federatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                federatorBtcKey.getPubKey(),
                federatorRskKey.getPubKey(),
                federatorMstKey.getPubKey()
            });

            // Act

            // Voting with less than m of n authorizers
            int result = federationSupport.voteFederationChange(firstAuthorizedTx, addFederatorAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), result);
            assertEquals(0, federationSupport.getPendingFederationSize());
        }

        @Test
        void voteFederationChange_addFederatorPublicKeys_returnsSuccessfulResponseCode() {
            // Arrange

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress()
            );

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            BtcECKey federatorBtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey federatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey federatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            ABICallSpec addFederationMemberAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                federatorBtcKey.getPubKey(),
                federatorRskKey.getPubKey(),
                federatorMstKey.getPubKey()
            });

            // Act

            // Voting add new fed with m of n authorizers
            int firstVoteCreateFederationResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederationMemberAbiCallSpec, signatureCache, bridgeEventLogger);
            int secondVoteCreateFederationResult = federationSupport.voteFederationChange(secondAuthorizedTx, addFederationMemberAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(
                FederationChangeResponseCode.SUCCESSFUL.getCode(),
                firstVoteCreateFederationResult
            );
            assertEquals(
                FederationChangeResponseCode.SUCCESSFUL.getCode(),
                secondVoteCreateFederationResult
            );
            assertEquals(1, federationSupport.getPendingFederationSize());

            assertArrayEquals(federatorBtcKey.getPubKey(), federationSupport.getPendingFederatorBtcPublicKey(0));
        }

        @Test
        void voteFederationChange_whenAddingInvalidFederatorPublicKeys_returnsGenericErrorResponseCode() {
            // Arrange
            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            ABICallSpec createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});

            // Voting with m of n authorizers to create the pending federation
            federationSupport.voteFederationChange(firstAuthorizedTx, createFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            federationSupport.voteFederationChange(secondAuthorizedTx, createFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            byte[] invalidPublicKey = TestUtils.generateBytes(1, 30);
            ECKey expectedFederatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedFederatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                invalidPublicKey,
                expectedFederatorRskKey.getPubKey(),
                expectedFederatorMstKey.getPubKey()
            });

            // Act

            // Voting add new fed with m of n authorizers
            int addVoteResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederatorAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), addVoteResult);
        }

        @Test
        void voteFederationChange_addFederatorMultiKey_returnsSuccessfulResponseCode() {
            // Arrange
            BtcECKey expectedFederatorBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey expectedFederatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedFederatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            // Voting with m of n authorizers to create the pending federation
            federationSupport.voteFederationChange(firstAuthorizedTx, createFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            federationSupport.voteFederationChange(secondAuthorizedTx, createFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            ABICallSpec addFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                expectedFederatorBtcECKey.getPubKey(),
                expectedFederatorRskKey.getPubKey(),
                expectedFederatorMstKey.getPubKey()
            });

            // Act

            // Voting add new fed with m of n authorizers
            int result = federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            int result2 = federationSupport.voteFederationChange(secondAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), result);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), result2);
            assertThat(federationSupport.getPendingFederationSize(), is(1));

            byte[] actualFederatorBtcECkey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC);
            byte[] actualFederatorRskKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK);
            byte[] actualFederatorMstKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST);

            assertArrayEquals(expectedFederatorBtcECKey.getPubKey(), actualFederatorBtcECkey);
            assertArrayEquals(expectedFederatorRskKey.getPubKey(true), actualFederatorRskKey);
            assertArrayEquals(expectedFederatorMstKey.getPubKey(true), actualFederatorMstKey);
        }

        @Test
        void voteFederationChange_rollback_returnsSuccessResponseCode() {
            // Arrange
            BtcECKey expectedFederatorBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey expectedFederatorRskECKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedFederatorMstECKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addFederationMemberAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                expectedFederatorBtcECKey.getPubKey(),
                expectedFederatorRskECKey.getPubKey(),
                expectedFederatorMstECKey.getPubKey()
            });

            // Voting add new fed with m of n authorizers to have at least one federation member before roll back
            federationSupport.voteFederationChange(firstAuthorizedTx, addFederationMemberAbiCallSpec, signatureCache, bridgeEventLogger);
            federationSupport.voteFederationChange(secondAuthorizedTx, addFederationMemberAbiCallSpec, signatureCache, bridgeEventLogger);

            ABICallSpec rollbackAbiCallSpec = new ABICallSpec(FederationChangeFunction.ROLLBACK.getKey(), new byte[][]{});

            // Act

            int pendingFederationSizeBeforeRollback = federationSupport.getPendingFederationSize();

            // Voting to roll back the pending federation with m of n authorizers
            int firstVoteRollbackResult = federationSupport.voteFederationChange(firstAuthorizedTx, rollbackAbiCallSpec, signatureCache, bridgeEventLogger);
            int secondVoteRollbackResult = federationSupport.voteFederationChange(secondAuthorizedTx, rollbackAbiCallSpec, signatureCache, bridgeEventLogger);

            int pendingFederationSizeAfterRollback = federationSupport.getPendingFederationSize();

            // Assert
            assertEquals(1, pendingFederationSizeBeforeRollback);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteRollbackResult);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteRollbackResult);
            assertEquals(-1, pendingFederationSizeAfterRollback);
            assertNull(federationSupport.getPendingFederationHash());
        }

        @Test
        void voteFederationChange_addFederatorPublicKeyBySameAuthorizerTwice_returnsGenericErrorResponseCode() {
            // Arrange
            BtcECKey expectedBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey expectedRskECKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedMstECKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                expectedBtcECKey.getPubKey(),
                expectedRskECKey.getPubKey(),
                expectedMstECKey.getPubKey()
            });

            // Act

            // Voting add public key twice with same authorizer
            int firstVoteAddFederationResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            int secondVoteAddFederationResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert

            // First call is successful
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteAddFederationResult);

            // Second call fails
            assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), secondVoteAddFederationResult);

            assertEquals(0, federationSupport.getPendingFederationSize());
        }

        @Test
        void voteFederationChange_addFederatorMultiKeyWithDifferentMstKey_returnsSuccessResponseCodeAndFedSizeZero() {
            // Arrange
            BtcECKey federatorBtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey federatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey federatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            ECKey differentFederatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(400));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            // Same btc and rsk keys for both feds, different mst key.
            ABICallSpec addMultiKeyAbiCallSpec1 = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                federatorBtcKey.getPubKey(),
                federatorRskKey.getPubKey(),
                federatorMstKey.getPubKey(),
            });

            ABICallSpec addMultiKeyAbiCallSpec2 = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                federatorBtcKey.getPubKey(),
                federatorRskKey.getPubKey(),
                differentFederatorMstKey.getPubKey(),
            });

            // Act

            // Voting add new fed member with m of n authorizers, but essentially for different federators due to the different mst key
            int firstVoteAddMultiFederatorKyesResult = federationSupport.voteFederationChange(firstAuthorizedTx, addMultiKeyAbiCallSpec1, signatureCache, bridgeEventLogger);
            int secondVoteAddMultiFederatorKyesResult = federationSupport.voteFederationChange(secondAuthorizedTx, addMultiKeyAbiCallSpec2, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteAddMultiFederatorKyesResult);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteAddMultiFederatorKyesResult);

            // Pending federation size is 0, because authorizers voted for different federators and there's no winner yet
            assertEquals(0, federationSupport.getPendingFederationSize());
        }

        private Stream<Arguments> federatorsWithDifferentKeysButOneParametersProvider() {

            BtcECKey federator1BtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey federator1RskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey federator1MstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            BtcECKey federator2BtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(400));
            ECKey federator2RskKey = ECKey.fromPrivate(BigInteger.valueOf(500));
            ECKey federator2MstKey = ECKey.fromPrivate(BigInteger.valueOf(600));

            FederationMember federator1 = new FederationMember(federator1BtcKey, federator1RskKey, federator1MstKey);

            FederationMember federator2WithSameMstKey = new FederationMember(federator2BtcKey, federator2RskKey, federator1MstKey);
            FederationMember federator2WithSameRskKey = new FederationMember(federator2BtcKey, federator1RskKey, federator2MstKey);
            FederationMember federator2WithSameBtcKey = new FederationMember(federator1BtcKey, federator2RskKey, federator2MstKey);

            return Stream.of(
                Arguments.of(federator1, federator2WithSameMstKey),
                Arguments.of(federator1, federator2WithSameRskKey),
                Arguments.of(federator1, federator2WithSameBtcKey)
            );
        }

        @ParameterizedTest
        @MethodSource("federatorsWithDifferentKeysButOneParametersProvider")
        void voteFederationChange_add2FederatorsWithWithDifferentKeysButOne_returnsFederatorAlreadyPresentResponseCode(
            FederationMember federator1,
            FederationMember federator2
        ) {

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addMultiKeyAbiCallSpec1 = getAddMultiKeysVote(federator1);

            ABICallSpec addMultiKeyAbiCallSpec2 = getAddMultiKeysVote(federator2);

            // Act

            // Voting add new fed member with m of n authorizers
            int firstVoteAddMultiFederator1KeysResult = federationSupport.voteFederationChange(firstAuthorizedTx, addMultiKeyAbiCallSpec1, signatureCache, bridgeEventLogger);
            int secondVoteAddMultiFederator1KeysResult = federationSupport.voteFederationChange(secondAuthorizedTx, addMultiKeyAbiCallSpec1, signatureCache, bridgeEventLogger);

            // Voting add new fed member that will be considered the same as the previous one because they share at least one of the same keys
            int firstVoteAddMultiFederator2KeysResult = federationSupport.voteFederationChange(firstAuthorizedTx, addMultiKeyAbiCallSpec2, signatureCache, bridgeEventLogger);

            // Assert

            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteAddMultiFederator1KeysResult);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteAddMultiFederator1KeysResult);

            // Federator is considered the same due to sharing at least one key with the previous federator voted
            assertEquals(FederationChangeResponseCode.FEDERATOR_ALREADY_PRESENT.getCode(), firstVoteAddMultiFederator2KeysResult);

            // Pending federation size is 1, because authorizers first voted for the same federator and the second was ignored
            assertEquals(1, federationSupport.getPendingFederationSize());

        }

        @Test
        void voteFederationChange_add100Members_returnsSuccessResponseCodeAndFedSize100() {
            // Arrange
            final int EXPECTED_COUNT_OF_MEMBERS = 100;

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            // Act

            // Voting add new fed with m of n authorizers
            for(int i = 0; i < EXPECTED_COUNT_OF_MEMBERS; i++) {
                BtcECKey federatorBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(i + 100));
                ECKey federatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(i + 101));
                ECKey federatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(i + 102));
                ABICallSpec addFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                    federatorBtcECKey.getPubKey(),
                    federatorRskKey.getPubKey(),
                    federatorMstKey.getPubKey()
                });
                int firstVoteAddResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
                int secondVoteAddResult = federationSupport.voteFederationChange(secondAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
                assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteAddResult);
                assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteAddResult);
            }

            // Assert
            assertEquals(EXPECTED_COUNT_OF_MEMBERS, federationSupport.getPendingFederationSize());
        }

        @Test
        void voteFederationChange_commit10MembersFederation_returnsSuccessResponseCodeAndPendingFedNullAndActiveFedIsTheVotedFed() {
            // Arrange
            activations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            Block executionBlock = mock(Block.class);
            long federationCreationBlockNumber = 1_000L;
            long federationActivationBlockNumber = federationMainnetConstants.getFederationActivationAge(activations) + federationCreationBlockNumber;

            when(executionBlock.getNumber())
                .thenReturn(federationCreationBlockNumber)
                .thenReturn(federationActivationBlockNumber);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            byte[][] commitFederationEventTopic = BridgeEvents.COMMIT_FEDERATION.getEvent().encodeEventTopics();
            List<DataWord> commitFederationEncodedTopics = LogInfo.byteArrayToList(commitFederationEventTopic);
            DataWord expectedCommitTopic = commitFederationEncodedTopics.get(0);

            final int EXPECTED_COUNT_OF_MEMBERS = 10;

            // Voting add new fed member with m of n authorizers
            Set<String> expectedPubKeys = new HashSet<>();

            for(int i = 0; i < EXPECTED_COUNT_OF_MEMBERS; i++) {
                BtcECKey expectedBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(i + 100));
                ECKey expectedRskECKey = ECKey.fromPrivate(BigInteger.valueOf(i + 101));
                ECKey expectedMstECKey = ECKey.fromPrivate(BigInteger.valueOf(i + 102));
                expectedPubKeys.add(HexUtils.toJsonHex(expectedBtcECKey.getPubKey()));
                ABICallSpec addFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                    expectedBtcECKey.getPubKey(),
                    expectedRskECKey.getPubKey(),
                    expectedMstECKey.getPubKey()
                });
                federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
                federationSupport.voteFederationChange(secondAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            }

            Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();

            ABICallSpec commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{pendingFederationHash.getBytes()});

            Federation activeFederationBeforeCommit = federationSupport.getActiveFederation();

            // Act

            // Voting commit new fed with m of n authorizers
            federationSupport.voteFederationChange(firstAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            federationSupport.voteFederationChange(secondAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertNull(federationSupport.getPendingFederationHash());
            // -1 because the pending fed no longer exist
            assertThat(federationSupport.getPendingFederationSize(), is(FederationChangeResponseCode.PENDING_FEDERATION_NON_EXISTENT.getCode()));

            Federation activeFederationAfterCommit = federationSupport.getActiveFederation();

            assertThat(federationSupport.getActiveFederationSize(), is(EXPECTED_COUNT_OF_MEMBERS));

            assertNotEquals(activeFederationBeforeCommit, activeFederationAfterCommit);

            Set<String> actualPubKeys = activeFederationAfterCommit.getMembers().stream()
                .map(member -> HexUtils.toJsonHex(member.getBtcPublicKey().getPubKey()))
                .collect(Collectors.toSet());

            assertEquals(expectedPubKeys, actualPubKeys);

            // asserting that 'commit_federation' event was emitted
            assertThat(logs.size(), is(1));

            LogInfo log = logs.get(0);
            DataWord actualTopic = log.getTopics().get(0);

            assertEquals(expectedCommitTopic, actualTopic);
        }

        @Test
        void voteFederationChange_commitFederationWithOver10Members_throwsException() {
            // Arrange
            Block executionBlock = mock(Block.class);

            long federationCreationBlockNumber = 1_000L;
            long federationActivationBlockNumber = federationMainnetConstants.getFederationActivationAge(activations) + federationCreationBlockNumber;

            when(executionBlock.getNumber())
                .thenReturn(federationCreationBlockNumber)
                .thenReturn(federationActivationBlockNumber);

            federationSupport = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(activations)
                .build();

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            final int EXPECTED_COUNT_OF_MEMBERS = 11;

            // Voting add new fed with m of n authorizers
            for(int i = 0; i < EXPECTED_COUNT_OF_MEMBERS; i++) {
                BtcECKey expectedBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(i + 100));
                ECKey expectedRskECKey = ECKey.fromPrivate(BigInteger.valueOf(i + 101));
                ECKey expectedMstECKey = ECKey.fromPrivate(BigInteger.valueOf(i + 102));
                ABICallSpec addFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                    expectedBtcECKey.getPubKey(),
                    expectedRskECKey.getPubKey(),
                    expectedMstECKey.getPubKey()
                });
                federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
                federationSupport.voteFederationChange(secondAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            }

            Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();

            ABICallSpec commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{pendingFederationHash.getBytes()});

            // Act

            // Voting commit new fed with m of n authorizers
            federationSupport.voteFederationChange(firstAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            Exception exception = assertThrows(Exception.class, () -> federationSupport.voteFederationChange(
                secondAuthorizedTx,
                commitFederationAbiCallSpec,
                signatureCache,
                bridgeEventLogger
            ));

            assertEquals("The script size is 525, that is above the maximum allowed.", exception.getMessage());
        }

        @Test
        void voteFederationChange_commit1MemberFederation_returnsInsufficientMembersResponseCode() {
            // Arrange
            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress()
            );

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            // Voting add new fed with m of n authorizers
            BtcECKey expectedBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey expectedRskECKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedMstECKey = ECKey.fromPrivate(BigInteger.valueOf(300));
            ABICallSpec addFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                expectedBtcECKey.getPubKey(),
                expectedRskECKey.getPubKey(),
                expectedMstECKey.getPubKey()
            });
            federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            federationSupport.voteFederationChange(secondAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();

            ABICallSpec commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{pendingFederationHash.getBytes()});

            // Act

            // Voting commit new fed with m of n authorizers
            int firstVoteCommitFederationResult = federationSupport.voteFederationChange(firstAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            int secondVoteCommitFederationResult = federationSupport.voteFederationChange(secondAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.INSUFFICIENT_MEMBERS.getCode(), firstVoteCommitFederationResult);
            assertEquals(FederationChangeResponseCode.INSUFFICIENT_MEMBERS.getCode(), secondVoteCommitFederationResult);
        }

        @Test
        void voteFederationChange_addMultiFederatorPublicKeyWithBadBtcKey_returnsGenericErrorResponseCode() {
            // Arrange
            byte[] invalidFederatorBtcPublicKey = TestUtils.generateBytes(2, 20);
            ECKey expectedFederatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedFederatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addMultiKeyAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                invalidFederatorBtcPublicKey,
                expectedFederatorRskKey.getPubKey(),
                expectedFederatorMstKey.getPubKey(),
            });

            // Act
            int voteAddMultiKeyResult = federationSupport.voteFederationChange(firstAuthorizedTx, addMultiKeyAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), voteAddMultiKeyResult);
        }

        @Test
        void voteFederationChange_addMultiFederatorPublicKeyWithBadRskKey_returnsGenericErrorResponseCode() {
            // Arrange
            BtcECKey btcFederatorECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            byte[] invalidFederatorRskPublicKey = TestUtils.generateBytes(2, 25);
            ECKey mstFederatorKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addMultiKeyAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                btcFederatorECKey.getPubKey(),
                invalidFederatorRskPublicKey,
                mstFederatorKey.getPubKey(),
            });

            // Act
            int voteAddMultiKeyResult = federationSupport.voteFederationChange(
                firstAuthorizedTx,
                addMultiKeyAbiCallSpec,
                signatureCache,
                bridgeEventLogger
            );

            // Assert
            assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), voteAddMultiKeyResult);
        }

        @Test
        void voteFederationChange_addMultiFederatorPublicKeyWithBadMstKey_returnsGenericErrorResponseCode() {
            // Arrange
            BtcECKey btcFederatorECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey rskFederatorKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            byte[] invalidFederatorMstPublicKey = TestUtils.generateBytes(2, 30);

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addMultiKeyAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                btcFederatorECKey.getPubKey(),
                rskFederatorKey.getPubKey(),
                invalidFederatorMstPublicKey,
            });

            // Act
            int voteAddMultiKeyResult = federationSupport.voteFederationChange(firstAuthorizedTx, addMultiKeyAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), voteAddMultiKeyResult);
        }

        @Test
        void voteFederationChange_callMultiKeyWithOnlyOneKey_ThrowsArrayIndexOutOfBoundsException() {
            // Arrange
            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            BtcECKey federatorBtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));

            ABICallSpec addFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{ federatorBtcKey.getPubKey() });

            // Act and assert
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger));
        }

        @Test
        void voteFederationChange_addWrongFederatorPublicKey_returnsGenericResponseCode() {
            // Arrange
            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            byte[] invalidFederatorBtcPublicKey = TestUtils.generateBytes(4, 25);
            ECKey expectedFederatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedFederatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            ABICallSpec addFederationAbiCallSpec = new ABICallSpec(
                FederationChangeFunction.ADD_MULTI.getKey(),
                new byte[][]{
                    invalidFederatorBtcPublicKey,
                    expectedFederatorRskKey.getPubKey(),
                    expectedFederatorMstKey.getPubKey()
                }
            );

            // Act

            // Voting add new fed with m of n authorizers
            int voteAddFederatorPublicKeyResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), voteAddFederatorPublicKeyResult);
        }

        @Test
        void voteFederationChange_addFederatorMultiKey_returnsExpectedPendingFederationSizeResponseCode() {
            // Arrange
            BtcECKey expectedBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
            ECKey expectedRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
            ECKey expectedMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addFederatorMultiKeyAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                expectedBtcECKey.getPubKey(),
                expectedRskKey.getPubKey(),
                expectedMstKey.getPubKey()
            });

            // Act

            // Voting add new fed with m of n authorizers
            int firstVoteAddFederatorMultiKeyResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederatorMultiKeyAbiCallSpec, signatureCache, bridgeEventLogger);
            int secondVoteAddFederatorMultiKeyResult = federationSupport.voteFederationChange(secondAuthorizedTx, addFederatorMultiKeyAbiCallSpec, signatureCache, bridgeEventLogger);

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteAddFederatorMultiKeyResult);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteAddFederatorMultiKeyResult);
            assertEquals(1, federationSupport.getPendingFederationSize());

            byte[] actualBtcECkey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC);
            byte[] actualRskKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK);
            byte[] actualMstKey = federationSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST);

            assertArrayEquals(expectedBtcECKey.getPubKey(), actualBtcECkey);
            assertArrayEquals(expectedRskKey.getPubKey(true), actualRskKey);
            assertArrayEquals(expectedMstKey.getPubKey(true), actualMstKey);
        }

        @Test
        void voteFederationChange_addFederatorPublicKeyPreWasabi_returnsSuccessfulResponseCode() {
            ActivationConfig.ForBlock activationsPreWasabi = ActivationConfigsForTest.orchid().forBlock(0L);

            FederationSupport federationSupportPreWasabi = federationSupportBuilder
                .withFederationConstants(federationMainnetConstants)
                .withFederationStorageProvider(storageProvider)
                .withActivations(activationsPreWasabi)
                .build();

            // Arrange
            BtcECKey federatorBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress()
            );

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addFederatorMultiKeyAbiCallSpec = new ABICallSpec(
                FederationChangeFunction.ADD.getKey(),
                new byte[][]{federatorBtcECKey.getPubKey()}
            );

            // Act

            // Voting add new fed with m of n authorizers
            int firstVoteAddFederatorMultiKeyResult = federationSupportPreWasabi.voteFederationChange(
                firstAuthorizedTx,
                addFederatorMultiKeyAbiCallSpec,
                signatureCache,
                bridgeEventLogger
            );
            int secondVoteAddFederatorMultiKeyResult = federationSupportPreWasabi.voteFederationChange(
                secondAuthorizedTx,
                addFederatorMultiKeyAbiCallSpec,
                signatureCache,
                bridgeEventLogger
            );

            // Assert
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteAddFederatorMultiKeyResult);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteAddFederatorMultiKeyResult);
        }

        @Test
        void voteFederationChange_addFederatorPublicKeyPostWasabi_ThrowsIllegalStateException() {
            // Arrange
            BtcECKey federatorBtcECKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));

            Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress()
            );

            voteToCreateFederation(firstAuthorizedTx, secondAuthorizedTx);

            ABICallSpec addFederatorMultiKeyAbiCallSpec = new ABICallSpec(
                FederationChangeFunction.ADD.getKey(),
                new byte[][]{federatorBtcECKey.getPubKey()}
            );

            // Act and assert
            Exception exception = assertThrows(
                IllegalStateException.class,
                () -> federationSupport.voteFederationChange(
                    firstAuthorizedTx,
                    addFederatorMultiKeyAbiCallSpec,
                    signatureCache,
                    bridgeEventLogger
                )
            );
            assertEquals("The \"add\" function is disabled.", exception.getMessage());
        }

        private void voteToCreateFederation(Transaction firstAuthorizedTx, Transaction secondAuthorizedTx) {
            // Voting with m of n authorizers to create the pending federation
            int resultFromFirstAuthorizer = federationSupport.voteFederationChange(
                firstAuthorizedTx,
                createFederationAbiCallSpec,
                signatureCache,
                bridgeEventLogger
            );
            int resultFromSecondAuthorizer = federationSupport.voteFederationChange(
                secondAuthorizedTx,
                createFederationAbiCallSpec,
                signatureCache,
                bridgeEventLogger
            );

            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
            assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

            assertEquals(0, federationSupport.getPendingFederationSize());
            assertNotNull(federationSupport.getPendingFederationHash());
        }

        private void voteToAddFederatorPublicKeysToPendingFederation() {
            List<FederationMember> federationMembersToBe = pendingFederationToBe.getMembers();

            for (FederationMember fedMemberToBe : federationMembersToBe) {
                ABICallSpec addFederationAbiCallSpec = getAddFederatorPublicKeysAbiCallSpec(fedMemberToBe);

                federationSupport.voteFederationChange(firstAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
                federationSupport.voteFederationChange(secondAuthorizedTx, addFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            }
        }

        private ABICallSpec getAddFederatorPublicKeysAbiCallSpec(FederationMember fedMemberToBe) {
            BtcECKey fedMemberToBeBtcKey = fedMemberToBe.getBtcPublicKey();
            ECKey fedMemberToBeRskKey = fedMemberToBe.getRskPublicKey();
            ECKey fedMemberToBeMstKey = fedMemberToBe.getMstPublicKey();

            return new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                fedMemberToBeBtcKey.getPubKey(),
                fedMemberToBeRskKey.getPubKey(),
                fedMemberToBeMstKey.getPubKey()
            });
        }

        private void voteToCommitPendingFederation() {
            Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();
            ABICallSpec commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{pendingFederationHash.getBytes()});

            federationSupport.voteFederationChange(firstAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);
            federationSupport.voteFederationChange(secondAuthorizedTx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);
        }

        private ABICallSpec getAddMultiKeysVote(FederationMember federationMember) {
            return new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{
                federationMember.getBtcPublicKey().getPubKey(),
                federationMember.getRskPublicKey().getPubKey(),
                federationMember.getMstPublicKey().getPubKey(),
            });
        }
    }

    @Test
    void getProposedFederation_whenStorageProviderReturnsEmpty_shouldReturnEmpty() {
        // act
        Optional<Federation> actualProposedFederation = federationSupport.getProposedFederation();

        // assert
        assertFalse(actualProposedFederation.isPresent());
    }

    @Test
    void getProposedFederation_whenStorageProviderReturnsProposedFederation_shouldReturnProposedFederation() {
        // arrange
        Federation proposedFederation = new P2shErpFederationBuilder().build();
        storageProvider.setProposedFederation(proposedFederation);

        //act
        Optional<Federation> actualProposedFederation = federationSupport.getProposedFederation();

        // assert
        assertTrue(actualProposedFederation.isPresent());
        assertEquals(proposedFederation, actualProposedFederation.get());
    }

    private List<ECKey> getRskPublicKeysFromFederationMembers(List<FederationMember> members) {
        return members.stream()
            .map(FederationMember::getRskPublicKey)
            .collect(Collectors.toList());
    }

    private List<ECKey> getMstPublicKeysFromFederationMembers(List<FederationMember> members) {
        return members.stream()
            .map(FederationMember::getMstPublicKey)
            .collect(Collectors.toList());
    }
}
