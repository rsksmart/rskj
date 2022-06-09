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
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.resources.TestConstants;
import co.rsk.peg.utils.PegUtils;
import co.rsk.peg.utils.ScriptBuilderWrapper;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PendingFederationTest {
    private PendingFederation pendingFederation;

    private final BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
    private final PegUtils pegUtils = PegUtils.getInstance();

    @Before
    public void createPendingFederation() {
        pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600), pegUtils.getBridgeSerializationUtils());
    }

    @Test
    public void membersImmutable() {
        boolean exception = false;
        try {
            pendingFederation.getMembers().add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        } catch (Exception e) {
            exception = true;
        }
        Assert.assertTrue(exception);

        exception = false;
        try {
            pendingFederation.getMembers().remove(0);
        } catch (Exception e) {
            exception = true;
        }
        Assert.assertTrue(exception);
    }

    @Test
    public void isComplete() {
        Assert.assertTrue(pendingFederation.isComplete());
    }

    @Test
    public void isComplete_not() {
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(200), pegUtils.getBridgeSerializationUtils());
        Assert.assertFalse(otherPendingFederation.isComplete());
    }

    @Test
    public void testEquals_basic() {
        Assert.assertEquals(pendingFederation, pendingFederation);

        Assert.assertNotEquals(null, pendingFederation);
        Assert.assertNotEquals(pendingFederation, new Object());
        Assert.assertNotEquals("something else", pendingFederation);
    }

    @Test
    public void testEquals_differentNumberOfMembers() {
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600, 700), pegUtils.getBridgeSerializationUtils());
        Assert.assertNotEquals(pendingFederation, otherPendingFederation);
    }

    @Test
    public void testEquals_differentMembers() {
        List<FederationMember> members = FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500);

        members.add(new FederationMember(BtcECKey.fromPrivate(BigInteger.valueOf(610)), ECKey.fromPrivate(BigInteger.valueOf(600)), ECKey.fromPrivate(BigInteger.valueOf(620))));
        PendingFederation otherPendingFederation = new PendingFederation(members, pegUtils.getBridgeSerializationUtils());

        members.remove(members.size()-1);
        members.add(new FederationMember(BtcECKey.fromPrivate(BigInteger.valueOf(600)), ECKey.fromPrivate(BigInteger.valueOf(610)), ECKey.fromPrivate(BigInteger.valueOf(630))));
        PendingFederation yetOtherPendingFederation = new PendingFederation(members, pegUtils.getBridgeSerializationUtils());

        Assert.assertNotEquals(otherPendingFederation, yetOtherPendingFederation);
        Assert.assertNotEquals(pendingFederation, otherPendingFederation);
        Assert.assertNotEquals(pendingFederation, yetOtherPendingFederation);
    }

    @Test
    public void testEquals_same() {
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600), pegUtils.getBridgeSerializationUtils());
        Assert.assertEquals(pendingFederation, otherPendingFederation);
    }

    @Test
    public void testToString() {
        Assert.assertEquals("6 signatures pending federation (complete)", pendingFederation.toString());
        PendingFederation otherPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100), pegUtils.getBridgeSerializationUtils());
        Assert.assertEquals("1 signatures pending federation (incomplete)", otherPendingFederation.toString());
    }

    @Test
    public void buildFederation_ok_6_members_before_RSKIP_201_activation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Integer[] privateKeys = new Integer[]{100, 200, 300, 400, 500, 600};
        Instant creationTime = Instant.ofEpochMilli(1234L);

        PendingFederation otherPendingFederation = new PendingFederation(
            FederationTestUtils.getFederationMembersFromPks(privateKeys), pegUtils.getBridgeSerializationUtils()
        );
        Federation builtFederation = otherPendingFederation.buildFederation(
            creationTime,
            0L,
            bridgeConstants,
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        Federation expectedFederation = new Federation(
            FederationTestUtils.getFederationMembersFromPks(privateKeys),
            creationTime,
            0L,
            bridgeConstants.getBtcParams(),
            pegUtils.getScriptBuilderWrapper()
        );

        Assert.assertEquals(expectedFederation, builtFederation);
    }

    @Test
    public void buildFederation_ok_9_members_before_RSKIP_201_activation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Integer[] privateKeys = new Integer[]{100, 200, 300, 400, 500, 600, 700, 800, 900};
        Instant creationTime = Instant.ofEpochMilli(1234L);

        PendingFederation otherPendingFederation = new PendingFederation(
            FederationTestUtils.getFederationMembersFromPks(privateKeys), pegUtils.getBridgeSerializationUtils()
        );
        Federation builtFederation = otherPendingFederation.buildFederation(
            creationTime,
            0L,
            bridgeConstants,
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        Federation expectedFederation = new Federation(
            FederationTestUtils.getFederationMembersFromPks(privateKeys),
            creationTime,
        0L,
            bridgeConstants.getBtcParams(),
            pegUtils.getScriptBuilderWrapper()
        );

        Assert.assertEquals(expectedFederation, builtFederation);
    }

    @Test
    public void buildFederation_erp_ok_after_RSKIP_201_activation_before_RSKIP284_testnet() {
        testBuildErpFederationConsideringRskip284ActivationAndNetworkId(false, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void buildFederation_erp_ok_after_RSKIP_201_activation_before_RSKIP284_mainnet() {
        testBuildErpFederationConsideringRskip284ActivationAndNetworkId(false, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void buildFederation_erp_ok_after_RSKIP_201_activation_after_RSKIP284_testnet() {
        testBuildErpFederationConsideringRskip284ActivationAndNetworkId(true, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void buildFederation_erp_ok_after_RSKIP_201_activation_after_RSKIP284_mainnet() {
        testBuildErpFederationConsideringRskip284ActivationAndNetworkId(true, NetworkParameters.ID_MAINNET);
    }

    private void testBuildErpFederationConsideringRskip284ActivationAndNetworkId(boolean isRskip284Active, String networkId) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);

        BridgeConstants bridgeConstants;
        if (networkId.equals(NetworkParameters.ID_MAINNET)) {
            bridgeConstants = BridgeMainNetConstants.getInstance();
        } else {
            bridgeConstants = BridgeTestNetConstants.getInstance();
        }

        Integer[] privateKeys = new Integer[]{100, 200, 300, 400, 500, 600};
        Instant creationTime = Instant.ofEpochMilli(1234L);

        PendingFederation otherPendingFederation = new PendingFederation(
            FederationTestUtils.getFederationMembersFromPks(privateKeys), pegUtils.getBridgeSerializationUtils()
        );
        Federation builtFederation = otherPendingFederation.buildFederation(
            creationTime,
            0L,
            bridgeConstants,
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        ErpFederation expectedFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(privateKeys),
            creationTime,
            0L,
            bridgeConstants.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        Assert.assertEquals(expectedFederation, builtFederation);
        if (!isRskip284Active && networkId.equals(NetworkParameters.ID_TESTNET)) {
            Assert.assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, builtFederation.getRedeemScript());
        }
    }

    @Test
    public void buildFederation_incomplete() {
        PendingFederation otherPendingFederation = new PendingFederation(
            FederationTestUtils.getFederationMembersFromPks(100), pegUtils.getBridgeSerializationUtils()
        );

        try {
            otherPendingFederation.buildFederation(
                Instant.ofEpochMilli(12L),
                0L,
                null,
                null,
                pegUtils.getScriptBuilderWrapper()
            );
        } catch (Exception e) {
            Assert.assertEquals("PendingFederation is incomplete", e.getMessage());
            return;
        }
        Assert.fail();
    }

    @Test
    public void getHash() {
        Assert.assertEquals("277f35b1c3b742f15eeabb243967794d90a3926d4a4a91cbf9d7d9eceac54a56", pendingFederation.getHash().toHexString());
    }
}
