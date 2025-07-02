/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.ScriptCreationException;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.federation.constants.FederationTestNetConstants;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PendingFederationTest {
    private static final long FEDERATION_CREATION_BLOCK_NUMBER = 0L;
    private final List<BtcECKey> federationMembersKeys = getFederationMembersKeys(100, 200, 300, 400, 500, 600, 700, 800, 900);
    private final PendingFederation pendingFederation = PendingFederationBuilder.builder()
        .withMembersBtcPublicKeys(federationMembersKeys)
        .build();
    private final List<FederationMember> federationMembers = pendingFederation.getMembers();

    @Test
    void addANewMember_toPendingFederationMembers_shouldThrowAnException() {
        FederationMember newMember = new FederationMember(new BtcECKey(), new ECKey(), new ECKey());
        List<FederationMember> pendingFederationMembers = pendingFederation.getMembers();
        assertThrows(
            UnsupportedOperationException.class,
            () -> pendingFederationMembers.add(newMember)
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> pendingFederationMembers.remove(0)
        );
    }

    @Test
    void isComplete_withAPendingFederationWithMoreMembersThanRequired_shouldBeTrue() {
        Assertions.assertTrue(pendingFederation.isComplete());
    }

    @Test
    void isComplete_withAPendingFederationWithLessMembersThanRequired_shouldBeFalse() {
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(200));
        Assertions.assertFalse(otherPendingFederation.isComplete());
    }

    @Test
    void equals_withTheSamePendingFederation_shouldBeTrue() {
        assertEquals(pendingFederation, pendingFederation);
    }

    @Test
    void equals_withNull_shouldBeFalse() {
        Assertions.assertNotEquals(null, pendingFederation);
    }

    @Test
    void equals_withADifferentObject_shouldBeFalse() {
        assertNotEquals(pendingFederation, new Object());
    }

    @Test
    void equals_withADifferentType_shouldBeFalse() {
        assertNotEquals(pendingFederation, "something else");
    }

    @Test
    void equals_withAPendingFederation_withDifferentNumberOfMembers_shouldBeFalse() {
        List<BtcECKey> otherFederationMembersKeys = getFederationMembersKeys(100, 200, 300, 400, 500, 600, 700);
        PendingFederation otherPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(otherFederationMembersKeys).build();
        assertNotEquals(pendingFederation, otherPendingFederation);
    }

    @Test
    void equals_withThreePendingFederations_withDifferentMembers_shouldBeFalse() {
        List<BtcECKey> newMembersKeys = getFederationMembersKeys(100, 200, 300, 400, 500, 610);
        PendingFederation otherPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(newMembersKeys).build();

        List<BtcECKey> anotherNewMembersKeys = getFederationMembersKeys(100, 200, 300, 400, 500, 620);
        PendingFederation yetOtherPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(anotherNewMembersKeys).build();

        assertNotEquals(otherPendingFederation, yetOtherPendingFederation);
        assertNotEquals(pendingFederation, otherPendingFederation);
        assertNotEquals(pendingFederation, yetOtherPendingFederation);
    }

    @Test
    void equals_withTwoPendingFederations_withTheSameMembers_shouldBeTrue() {
        PendingFederation otherPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(federationMembersKeys).build();
        assertEquals(pendingFederation, otherPendingFederation);
    }

    @Test
    void toString_withACompleteFederation_shouldPrintTheCorrectMessage() {
        assertEquals("9 signatures pending federation (complete)", pendingFederation.toString());
    }

    @Test
    void toString_withAnIncompleteFederation_shouldPrintTheCorrectMessage() {
        BtcECKey newMemberKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
        PendingFederation otherPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(List.of(newMemberKey)).build();

        assertEquals("1 signatures pending federation (incomplete)", otherPendingFederation.toString());
    }

    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with9Members_beforeRSKIP201Activation_shouldBuildMultiSigFed(FederationConstants federationConstants) {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.papyrus200().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants.getBtcParams()
        );
        Federation expectedFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        // Act
        Federation builtFederation = pendingFederation.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        );

        // Assert
        assertEquals(expectedFederation, builtFederation);
    }

    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with15Members_beforeRSKIP201Activation_shouldBuildMultiSigFed(FederationConstants federationConstants) {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.papyrus200().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        List<BtcECKey> federationWith15MembersKeys = getFederationMembersKeys(
            100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500
        );
        PendingFederation pendingFederationWith15Members = PendingFederationBuilder.builder()
            .withMembersBtcPublicKeys(federationWith15MembersKeys)
            .build();
        FederationArgs federationArgs = new FederationArgs(
            pendingFederationWith15Members.getMembers(),
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants.getBtcParams()
        );
        Federation expectedFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        // Act
        Federation builtFederation = pendingFederationWith15Members.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        );

        // Assert
        assertEquals(expectedFederation, builtFederation);
    }

    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with9Members_afterRSKIP201Activation_beforeRSKIP353_shouldBuildNonStandardERPFed(FederationConstants federationConstants) {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants.getBtcParams()
        );
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        Federation expectedFederation = FederationFactory.buildNonStandardErpFederation(
            federationArgs,
            erpPubKeys,
            activationDelay,
            activations
        );

        // Act
        Federation builtFederation = pendingFederation.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        );

        // Assert
        assertEquals(expectedFederation, builtFederation);
    }

    @Test
    void buildFederation_with10Members_inMainnet_afterRSKIP201Activation_beforeRSKIP293_shouldThrowScriptCreationException() {
        // Arrange
        BridgeMainNetConstants mainnetConstants = BridgeMainNetConstants.getInstance();
        FederationConstants federationConstants = mainnetConstants.getFederationConstants();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.iris300().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        List<BtcECKey> federationWith10MembersKeys = getFederationMembersKeys(
            100, 200, 300, 400, 500, 600, 700, 800, 900, 1000
        );
        PendingFederation pendingFederationWith10Members = PendingFederationBuilder.builder()
            .withMembersBtcPublicKeys(federationWith10MembersKeys)
            .build();

        // Act & Assert
        assertThrows(ScriptCreationException.class, () -> pendingFederationWith10Members.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        ));
    }

    @Test
    void buildFederation_with11Members_inTestnet_afterRSKIP201Activation_beforeRSKIP353_shouldThrowScriptCreationException() {
        // Arrange
        FederationConstants federationConstants = FederationTestNetConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        // Max size in testnet is 10 members, since ERP has 3 members
        List<BtcECKey> federationWith11MembersKeys = getFederationMembersKeys(
            100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100
        );
        PendingFederation pendingFederationWith11Members = PendingFederationBuilder.builder()
            .withMembersBtcPublicKeys(federationWith11MembersKeys)
            .build();

        // Act & assert
        assertThrows(ScriptCreationException.class, () -> pendingFederationWith11Members.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        ));
    }

    @Test
    void buildFederation_with10Members_inMainnet_afterRSKIP201Activation_beforeRSKIP353_shouldThrowScriptCreationException() {
        // Arrange
        FederationConstants federationConstants = FederationMainNetConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        List<BtcECKey> federationWith10MembersKeys = getFederationMembersKeys(
            100, 200, 300, 400, 500, 600, 700, 800, 900, 1000
        );
        PendingFederation pendingFederationWith10Members = PendingFederationBuilder.builder()
            .withMembersBtcPublicKeys(federationWith10MembersKeys)
            .build();

        // Act & assert
        assertThrows(ScriptCreationException.class, () -> pendingFederationWith10Members.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        ));
    }

    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with9Members_afterRSKIP353Activation_beforeRSKIP305_shouldBuildP2SHERPFed(FederationConstants federationConstants) {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.lovell700().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants.getBtcParams()
        );
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        Federation expectedFederation = FederationFactory.buildP2shErpFederation(
            federationArgs,
            erpPubKeys,
            activationDelay
        );

        // Act
        Federation builtFederation = pendingFederation.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        );

        // Assert
        assertEquals(expectedFederation, builtFederation);
    }

    @Test
    void buildFederation_with11Members_inTestnet_afterRSKIP353Activation_beforeRSKIP305_shouldThrowScriptCreationException() {
        // Arrange
        FederationConstants federationConstants = FederationTestNetConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.lovell700().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        // 11 federation members keys + 3 emergency keys
        List<BtcECKey> federationWith11MembersKeys = getFederationMembersKeys(
            100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100
        );
        PendingFederation pendingFederationWith11Members = PendingFederationBuilder.builder()
            .withMembersBtcPublicKeys(federationWith11MembersKeys)
            .build();

        // Act & assert
        assertThrows(ScriptCreationException.class, () -> pendingFederationWith11Members.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        ));
    }

    @Test
    void buildFederation_with10Members_inMainnet_afterRSKIP353Activation_beforeRSKIP305_shouldThrowScriptCreationException() {
        // Arrange
        FederationConstants federationConstants = FederationMainNetConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.lovell700().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        // 11 federation members keys + 3 emergency keys
        List<BtcECKey> federationWith10MembersKeys = getFederationMembersKeys(
            100, 200, 300, 400, 500, 600, 700, 800, 900, 1000
        );
        PendingFederation pendingFederationWith10Members = PendingFederationBuilder.builder()
            .withMembersBtcPublicKeys(federationWith10MembersKeys)
            .build();

        // Act & assert
        assertThrows(ScriptCreationException.class, () -> pendingFederationWith10Members.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        ));
    }

    /**
     * It's worth to clarify that 102 federation members is just for testing purposes. The redeemScript uses
     * OP_CHECKMULTISIG which has a limit of 20 public keys.
     */
    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with102Members_afterRSKIP305_shouldThrowScriptCreationException(FederationConstants federationConstants) {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        int numberOfMembers = 102;
        Integer[] federationMembersValues = new Integer[numberOfMembers];
        for(int i = 1; i <= numberOfMembers; i++) {
            federationMembersValues[i-1] = i * 100;
        }
        List<BtcECKey> federationWith102MembersKeys = getFederationMembersKeys(federationMembersValues);
        PendingFederation pendingFederationWith102Members = PendingFederationBuilder.builder()
            .withMembersBtcPublicKeys(federationWith102MembersKeys)
            .build();

        // Act & assert
        assertThrows(ErpFederationCreationException.class, () -> pendingFederationWith102Members.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        ));
    }

    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with9Members_afterRSKIP305Activation_shouldBuildP2SHP2WSHERPFed(FederationConstants federationConstants) {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants.getBtcParams()
        );
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        Federation expectedFederation = FederationFactory.buildP2shP2wshErpFederation(federationArgs, erpPubKeys, activationDelay);

        // Act
        Federation builtFederation = pendingFederation.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        );

        // Assert
        assertEquals(expectedFederation, builtFederation);
    }

    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with20Members_afterRSKIP305Activation_shouldBuildP2SHP2WSHErpFed(FederationConstants federationConstants) {
        // Arrange
        List<BtcECKey> federationMembersKeys = getFederationMembersKeys(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
            1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000
        );
        PendingFederation pendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(federationMembersKeys).build();
        List<FederationMember> federationMembers = pendingFederation.getMembers();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();
        FederationArgs federationArgs = new FederationArgs(federationMembers, federationCreationTime, FEDERATION_CREATION_BLOCK_NUMBER, federationConstants.getBtcParams());
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        Federation expectedFederation = FederationFactory.buildP2shP2wshErpFederation(federationArgs, erpPubKeys, activationDelay);

        // Act
        Federation builtFederation = pendingFederation.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        );

        // Assert
        assertEquals(expectedFederation, builtFederation);
    }

    @ParameterizedTest
    @MethodSource("federationConstants")
    void buildFederation_with21Members_afterRSKIP305Activation_shouldThrowErpFederationCreationException(FederationConstants federationConstants) {
        // Arrange
        List<BtcECKey> federationMembersKeys = getFederationMembersKeys(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
            1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000, 2100
        );
        PendingFederation pendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(federationMembersKeys).build();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);
        Instant federationCreationTime = federationConstants.getGenesisFederationCreationTime();

        // Act & assert
        assertThrows(ErpFederationCreationException.class, () -> pendingFederation.buildFederation(
            federationCreationTime,
            FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants,
            activations
        ));
    }

    @Test
    void buildFederation_withLessMembersThanRequired_shouldFailWithIncompleteFederationLog() {
        BtcECKey otherFederationMemberKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
        PendingFederation otherPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(List.of(otherFederationMemberKey)).build();

        Instant creationTime = Instant.ofEpochMilli(12L);
        assertThrows(IllegalStateException.class, () -> otherPendingFederation.buildFederation(
            creationTime,
            0L,
            null,
            null
        ));
    }

    @Test
    void getHash() {
        Keccak256 expectedHash = new Keccak256("49ca57b7a262f0f10de592eede9f2b10315734308584782ca1d4c13a57d5767e");
        assertEquals(expectedHash, pendingFederation.getHash());
    }

    @Test
    void serializeAndDeserialize_withPendingFederation_shouldGiveTheSameFederation() {
        // we want serialization from members
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.wasabi100().forBlock(0);

        final int NUM_CASES = 20;

        for (int i = 0; i < NUM_CASES; i++) {
            int numMembers = randomInRange(2, 14);
            List<BtcECKey> membersBtcPubKeys = new ArrayList<>();
            for (int j = 0; j < numMembers; j++) {
                membersBtcPubKeys.add(new BtcECKey());
            }
            PendingFederation testPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(membersBtcPubKeys).build();

            byte[] serializedTestPendingFederation = testPendingFederation.serialize(activations);
            PendingFederation deserializedTestPendingFederation = PendingFederation.deserialize(serializedTestPendingFederation);

            assertEquals(testPendingFederation, deserializedTestPendingFederation);
        }
    }

    @Test
    void serializePendingFederation_serializedKeysAreCompressedAndThree() {
        // we want serialization from members
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.wasabi100().forBlock(0);

        final int NUM_MEMBERS = 10;
        final int EXPECTED_NUM_KEYS = 3;
        final int EXPECTED_PUBLICKEY_SIZE = 33;

        List<BtcECKey> membersBtcPubKeys = new ArrayList<>();
        for (int j = 0; j < NUM_MEMBERS; j++) {
            membersBtcPubKeys.add(new BtcECKey());
        }

        PendingFederation testPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(membersBtcPubKeys).build();

        byte[] serializedPendingFederation = testPendingFederation.serialize(activations);

        RLPList memberList = (RLPList) RLP.decode2(serializedPendingFederation).get(0);
        assertEquals(NUM_MEMBERS, memberList.size());

        for (int i = 0; i < NUM_MEMBERS; i++) {
            RLPList memberKeys = (RLPList) RLP.decode2(memberList.get(i).getRLPData()).get(0);
            assertEquals(EXPECTED_NUM_KEYS, memberKeys.size());
            for (int j = 0; j < EXPECTED_NUM_KEYS; j++) {
                assertEquals(EXPECTED_PUBLICKEY_SIZE, memberKeys.get(j).getRLPData().length);
            }

        }
    }

    @Test
    void deserializePendingFederation_withInvalidFederationMember_shouldThrowARunTimeException() {
        byte[] serialized = RLP.encodeList(
            RLP.encodeList(RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0]))
        );

        assertThrows(RuntimeException.class, () -> PendingFederation.deserialize(serialized));
    }

    @Test
    void serializePendingFederationOnlyBtcKeys() {
        // we want serialization from pub keys
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.orchid().forBlock(0);

        byte[][] publicKeyBytes = new byte[][]{
            BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        // Only actual keys serialized are BTC keys, so we don't really care about RSK or MST keys
        List<BtcECKey> keys = Arrays.asList(BtcECKey.fromPublicOnly(publicKeyBytes[0]),
            BtcECKey.fromPublicOnly(publicKeyBytes[1]),
            BtcECKey.fromPublicOnly(publicKeyBytes[2]),
            BtcECKey.fromPublicOnly(publicKeyBytes[3]),
            BtcECKey.fromPublicOnly(publicKeyBytes[4]),
            BtcECKey.fromPublicOnly(publicKeyBytes[5]));

        PendingFederation anotherPendingFederation = PendingFederationBuilder.builder().withMembersBtcPublicKeys(keys).build();

        byte[] result = anotherPendingFederation.serialize(activations);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("f8cc");
        anotherPendingFederation.getBtcPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("a1");
            expectedBuilder.append(ByteUtil.toHexString(key.getPubKey()));
        });

        String expected = expectedBuilder.toString();
        assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    void deserializePendingFederationOnlyBtcKeys() {
        byte[][] publicKeyBytes = Stream.of(100, 200, 300, 400, 500, 600)
            .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .map(BtcECKey::getPubKey)
            .toArray(byte[][]::new);

        byte[][] rlpBytes = new byte[publicKeyBytes.length][];

        for (int k = 0; k < publicKeyBytes.length; k++) {
            rlpBytes[k] = RLP.encodeElement(publicKeyBytes[k]);
        }

        byte[] data = RLP.encodeList(rlpBytes);

        PendingFederation deserializedPendingFederation = PendingFederation.deserializeFromBtcKeysOnly(data);

        assertEquals(6, deserializedPendingFederation.getBtcPublicKeys().size());
        for (int i = 0; i < 6; i++) {
            assertArrayEquals(publicKeyBytes[i], deserializedPendingFederation.getBtcPublicKeys().get(i).getPubKey());
        }
    }

    private static Stream<Arguments> federationConstants() {
        return Stream.of(
            Arguments.of(FederationTestNetConstants.getInstance()),
            Arguments.of(FederationMainNetConstants.getInstance())
        );
    }

    private int randomInRange(int min, int max) {
        return TestUtils.generateInt(PendingFederationTest.class.toString(),max - min + 1) + min;
    }

    private List<BtcECKey> getFederationMembersKeys(Integer... values) {
        List<BtcECKey> keys = new ArrayList<>();
        for (Integer v : values) {
            keys.add(BtcECKey.fromPrivate(BigInteger.valueOf(v)));
        }
        return keys.stream().toList();
    }
}
