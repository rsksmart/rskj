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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.resources.TestConstants;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) //TODO Work on removing this setting by using only the needed stubs
class PendingFederationTest {
    private PendingFederation pendingFederation;

    @BeforeEach
    void createPendingFederation() {
        pendingFederation = new PendingFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600)
        );
    }

    @Test
    void membersImmutable() {
        boolean exception = false;
        try {
            pendingFederation.getMembers().add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        } catch (Exception e) {
            exception = true;
        }
        Assertions.assertTrue(exception);

        exception = false;
        try {
            pendingFederation.getMembers().remove(0);
        } catch (Exception e) {
            exception = true;
        }
        Assertions.assertTrue(exception);
    }

    @Test
    void isComplete() {
        Assertions.assertTrue(pendingFederation.isComplete());
    }

    @Test
    void isComplete_not() {
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(200));
        Assertions.assertFalse(otherPendingFederation.isComplete());
    }

    @Test
    void testEquals_basic() {
        assertEquals(pendingFederation, pendingFederation);

        Assertions.assertNotEquals(null, pendingFederation);
        Assertions.assertNotEquals(pendingFederation, new Object());
        Assertions.assertNotEquals("something else", pendingFederation);
    }

    @Test
    void testEquals_differentNumberOfMembers() {
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600, 700));
        Assertions.assertNotEquals(pendingFederation, otherPendingFederation);
    }

    @Test
    void testEquals_differentMembers() {
        List<FederationMember> members = FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500);

        members.add(new FederationMember(BtcECKey.fromPrivate(BigInteger.valueOf(610)), ECKey.fromPrivate(BigInteger.valueOf(600)), ECKey.fromPrivate(BigInteger.valueOf(620))));
        PendingFederation otherPendingFederation = new PendingFederation(members);

        members.remove(members.size()-1);
        members.add(new FederationMember(BtcECKey.fromPrivate(BigInteger.valueOf(600)), ECKey.fromPrivate(BigInteger.valueOf(610)), ECKey.fromPrivate(BigInteger.valueOf(630))));
        PendingFederation yetOtherPendingFederation = new PendingFederation(members);

        Assertions.assertNotEquals(otherPendingFederation, yetOtherPendingFederation);
        Assertions.assertNotEquals(pendingFederation, otherPendingFederation);
        Assertions.assertNotEquals(pendingFederation, yetOtherPendingFederation);
    }

    @Test
    void testEquals_same() {
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600));
        assertEquals(pendingFederation, otherPendingFederation);
    }

    @Test
    void testToString() {
        assertEquals("6 signatures pending federation (complete)", pendingFederation.toString());
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100));
        assertEquals("1 signatures pending federation (incomplete)", otherPendingFederation.toString());
    }

    @Test
    void buildFederation_ok_6_members_before_RSKIP_201_activation() {
        testBuildFederation(
            false,
            false,
            false,
            NetworkParameters.ID_TESTNET,
            6
        );
    }

    @Test
    void buildFederation_ok_9_members_before_RSKIP_201_activation() {
        testBuildFederation(
            false,
            false,
            false,
            NetworkParameters.ID_TESTNET,
            9
        );
    }

    @Test
    void buildFederation_erp_ok_after_RSKIP_201_activation_before_RSKIP284_testnet() {
        testBuildFederation(
            true,
            false,
            false,
            NetworkParameters.ID_TESTNET,
            6
        );
    }

    @Test
    void buildFederation_erp_ok_after_RSKIP_201_activation_before_RSKIP284_mainnet() {
        testBuildFederation(
            true,
            false,
            false,
            NetworkParameters.ID_MAINNET,
            6
        );
    }

    @Test
    void buildFederation_erp_ok_after_RSKIP_201_activation_after_RSKIP284_testnet() {
        testBuildFederation(
            true,
            true,
            false,
            NetworkParameters.ID_TESTNET,
            6
        );
    }

    @Test
    void buildFederation_erp_ok_after_RSKIP_201_activation_after_RSKIP284_mainnet() {
        testBuildFederation(
            true,
            true,
            false,
            NetworkParameters.ID_MAINNET,
            6
        );
    }

    @Test
    void buildFederation_after_RSKIP_353_activation_testnet() {
        testBuildFederation(
            true,
            true,
            true,
            NetworkParameters.ID_TESTNET,
            6
        );
    }

    @Test
    void buildFederation_after_RSKIP_353_activation_mainnet() {
        testBuildFederation(
            true,
            true,
            true,
            NetworkParameters.ID_MAINNET,
            6
        );
    }

    @Test
    void buildFederation_incomplete() {
        PendingFederation otherPendingFederation = new PendingFederation(
            FederationTestUtils.getFederationMembersFromPks(100)
        );

        try {
            otherPendingFederation.buildFederation(
                Instant.ofEpochMilli(12L),
                0L,
                null,
                null
            );
        } catch (Exception e) {
            assertEquals("PendingFederation is incomplete", e.getMessage());
            return;
        }
        fail();
    }

    @Test
    void getHash() {
        Keccak256 expectedHash = new Keccak256("277f35b1c3b742f15eeabb243967794d90a3926d4a4a91cbf9d7d9eceac54a56");
        assertEquals(expectedHash, pendingFederation.getHash());
    }

    @Test
    void serializeAndDeserializePendingFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        // we want serialization from members
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(true);

        final int NUM_CASES = 20;

        for (int i = 0; i < NUM_CASES; i++) {
            int numMembers = randomInRange(2, 14);
            List<FederationMember> members = new ArrayList<>();
            for (int j = 0; j < numMembers; j++) {
                members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
            }
            PendingFederation testPendingFederation = new PendingFederation(members);

            byte[] serializedTestPendingFederation = testPendingFederation.serialize(activations);
            PendingFederation deserializedTestPendingFederation = PendingFederation.deserialize(serializedTestPendingFederation);

            Assertions.assertEquals(testPendingFederation, deserializedTestPendingFederation);
        }
    }

    @Test
    void serializePendingFederation_serializedKeysAreCompressedAndThree() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        // we want serialization from members
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(true);

        final int NUM_MEMBERS = 10;
        final int EXPECTED_NUM_KEYS = 3;
        final int EXPECTED_PUBLICKEY_SIZE = 33;

        List<FederationMember> members = new ArrayList<>();
        for (int j = 0; j < NUM_MEMBERS; j++) {
            members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        }

        PendingFederation testPendingFederation = new PendingFederation(members);
        byte[] serializedPendingFederation = testPendingFederation.serialize(activations);

        RLPList memberList = (RLPList) RLP.decode2(serializedPendingFederation).get(0);
        Assertions.assertEquals(NUM_MEMBERS, memberList.size());

        for (int i = 0; i < NUM_MEMBERS; i++) {
            RLPList memberKeys = (RLPList) RLP.decode2(memberList.get(i).getRLPData()).get(0);
            Assertions.assertEquals(EXPECTED_NUM_KEYS, memberKeys.size());
            for (int j = 0; j < EXPECTED_NUM_KEYS; j++) {
                Assertions.assertEquals(EXPECTED_PUBLICKEY_SIZE, memberKeys.get(j).getRLPData().length);
            }

        }
    }

    @Test
    void deserializePendingFederation_invalidFederationMember() {
        byte[] serialized = RLP.encodeList(
            RLP.encodeList(RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0]))
        );

        try {
            PendingFederation.deserialize(serialized);
            Assertions.fail();
        } catch (RuntimeException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid serialized FederationMember"));
        }
    }

    @Test
    void serializePendingFederationOnlyBtcKeys() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        // we want serialization from pub keys
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(false);

        byte[][] publicKeyBytes = new byte[][]{
            BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
            BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        // Only actual keys serialized are BTC keys, so we don't really care about RSK or MST keys
        List<BtcECKey> keys = Arrays.asList(new BtcECKey[]{
            BtcECKey.fromPublicOnly(publicKeyBytes[0]),
            BtcECKey.fromPublicOnly(publicKeyBytes[1]),
            BtcECKey.fromPublicOnly(publicKeyBytes[2]),
            BtcECKey.fromPublicOnly(publicKeyBytes[3]),
            BtcECKey.fromPublicOnly(publicKeyBytes[4]),
            BtcECKey.fromPublicOnly(publicKeyBytes[5]),
        });
        List<FederationMember> members = FederationTestUtils.getFederationMembersWithBtcKeys(keys);
        PendingFederation pendingFederation = new PendingFederation(members);

        byte[] result = pendingFederation.serialize(activations);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("f8cc");
        pendingFederation.getBtcPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("a1");
            expectedBuilder.append(ByteUtil.toHexString(key.getPubKey()));
        });

        String expected = expectedBuilder.toString();
        Assertions.assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    void deserializePendingFederationOnlyBtcKeys() throws Exception {
        byte[][] publicKeyBytes = Arrays.asList(100, 200, 300, 400, 500, 600).stream()
            .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .map(k -> k.getPubKey())
            .toArray(byte[][]::new);

        byte[][] rlpBytes = new byte[publicKeyBytes.length][];

        for (int k = 0; k < publicKeyBytes.length; k++) {
            rlpBytes[k] = RLP.encodeElement(publicKeyBytes[k]);
        }

        byte[] data = RLP.encodeList(rlpBytes);

        PendingFederation deserializedPendingFederation = PendingFederation.deserializeFromBtcKeysOnly(data);

        Assertions.assertEquals(6, deserializedPendingFederation.getBtcPublicKeys().size());
        for (int i = 0; i < 6; i++) {
            Assertions.assertTrue(Arrays.equals(publicKeyBytes[i], deserializedPendingFederation.getBtcPublicKeys().get(i).getPubKey()));
        }
    }

    private void testBuildFederation(
        boolean isRskip201Active,
        boolean isRskip284Active,
        boolean isRskip353Active,
        String networkId,
        int federationMembersCount) {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(isRskip201Active);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);
        when(activations.isActive(ConsensusRule.RSKIP353)).thenReturn(isRskip353Active);

        BridgeConstants bridgeConstants;
        if (networkId.equals(NetworkParameters.ID_MAINNET)) {
            bridgeConstants = BridgeMainNetConstants.getInstance();
        } else {
            bridgeConstants = BridgeTestNetConstants.getInstance();
        }

        Integer[] privateKeys = new Integer[federationMembersCount];
        for (int i = 0; i < federationMembersCount; i++) {
            privateKeys[i] = new Integer((i+1) * 100);
        }
        Instant creationTime = Instant.ofEpochMilli(1234L);

        PendingFederation otherPendingFederation = new PendingFederation(
            FederationTestUtils.getFederationMembersFromPks(privateKeys)
        );
        Federation builtFederation = otherPendingFederation.buildFederation(
            creationTime,
            0L,
            bridgeConstants,
            activations
        );

        Federation expectedFederation;
        List<FederationMember> fedMembers = FederationTestUtils.getFederationMembersFromPks(privateKeys);
        NetworkParameters btcParams = bridgeConstants.getBtcParams();
        FederationArgs federationArgs = new FederationArgs(fedMembers, creationTime, 0L, btcParams);

        List<BtcECKey> erpPubKeys = bridgeConstants.getErpFedPubKeysList();
        long activationDelay = bridgeConstants.getErpFedActivationDelay();

        if (expectedFederationShouldBeStandardMultisig(isRskip201Active)) {
            expectedFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);
        }
        else if (expectedFederationShouldBeNonStandardErp(isRskip353Active)) {
            expectedFederation = FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);
        }
        else {
            expectedFederation = FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
        }

        assertEquals(expectedFederation, builtFederation);
        if (isRskip201Active && !isRskip284Active && networkId.equals(NetworkParameters.ID_TESTNET)) {
            assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, builtFederation.getRedeemScript());
        }
    }

    private boolean expectedFederationShouldBeStandardMultisig(boolean isRskip201Active) {
        return !isRskip201Active;
    }

    private boolean expectedFederationShouldBeNonStandardErp(boolean isRskip353Active) {
        return !isRskip353Active;
    }

    private int randomInRange(int min, int max) {
        return TestUtils.generateInt(PendingFederationTest.class.toString(),max - min + 1) + min;
    }
}
