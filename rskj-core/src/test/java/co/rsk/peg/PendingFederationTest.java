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

package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.resources.TestConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(pendingFederation)).thenReturn(new byte[]{(byte) 0xaa});

            Keccak256 expectedHash = new Keccak256(HashUtil.keccak256(new byte[]{(byte) 0xaa}));

            assertEquals(expectedHash, pendingFederation.getHash());
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
        if (isRskip353Active) {
            expectedFederation = new ErpFederation(
                FederationTestUtils.getFederationMembersFromPks(privateKeys),
                creationTime,
                0L,
                bridgeConstants.getBtcParams(),
                bridgeConstants.getErpFedPubKeysList(),
                bridgeConstants.getErpFedActivationDelay(),
                activations,
                new P2shErpRedeemScriptBuilder()
            );
        } else if (isRskip201Active) {
            ErpRedeemScriptBuilder erpRedeemScriptBuilder =
                ErpRedeemScriptBuilderUtils.defineErpRedeemScriptBuilder(activations, bridgeConstants);
            expectedFederation = new ErpFederation(
                FederationTestUtils.getFederationMembersFromPks(privateKeys),
                creationTime,
                0L,
                bridgeConstants.getBtcParams(),
                bridgeConstants.getErpFedPubKeysList(),
                bridgeConstants.getErpFedActivationDelay(),
                activations,
                erpRedeemScriptBuilder
            );
        } else {
            expectedFederation = new StandardMultisigFederation(
                FederationTestUtils.getFederationMembersFromPks(privateKeys),
                creationTime,
                0L,
                bridgeConstants.getBtcParams()
            );
        }

        assertEquals(expectedFederation, builtFederation);
        if (isRskip201Active && !isRskip284Active && networkId.equals(NetworkParameters.ID_TESTNET)) {
            assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, builtFederation.getRedeemScript());
        }
    }
}
