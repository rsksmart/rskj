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

package co.rsk.bridge;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.crypto.ECKey;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class FederationTest {
    private Federation federation;
    private List<BtcECKey> sortedPublicKeys;
    private List<ECKey> rskPubKeys;
    private List<byte[]> rskAddresses;

    @BeforeEach
    void createFederation() {
        federation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        sortedPublicKeys = Arrays.stream(new BtcECKey[]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)),
        }).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        rskPubKeys = Stream.of(101, 201, 301, 401, 501, 601)
                .map(i -> ECKey.fromPrivate(BigInteger.valueOf(i)))
                .collect(Collectors.toList());

        rskAddresses = rskPubKeys
                .stream()
                .map(ECKey::getAddress)
                .collect(Collectors.toList());
    }

    @Test
    void membersImmutable() {
        boolean exception = false;
        try {
            federation.getMembers().add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        } catch (Exception e) {
            exception = true;
        }
        Assertions.assertTrue(exception);

        exception = false;
        try {
            federation.getMembers().remove(0);
        } catch (Exception e) {
            exception = true;
        }
        Assertions.assertTrue(exception);
    }

    @Test
    void redeemScript() {
        final List<Integer> calls = new ArrayList<>();
        try (MockedStatic<ScriptBuilder> scriptBuilderMocked = mockStatic(ScriptBuilder.class)) {
            scriptBuilderMocked.when(() -> ScriptBuilder.createRedeemScript(any(int.class), any(List.class))).thenAnswer((invocationOnMock) -> {
                calls.add(1);
                int numberOfSignaturesRequired = invocationOnMock.<Integer>getArgument(0);
                List<BtcECKey> publicKeys = invocationOnMock.getArgument(1);
                Assertions.assertEquals(4, numberOfSignaturesRequired);
                Assertions.assertEquals(6, publicKeys.size());
                for (int i = 0; i < sortedPublicKeys.size(); i++) {
                    Assertions.assertArrayEquals(sortedPublicKeys.get(i).getPubKey(),
                            publicKeys.get(i).getPubKey());
                }
                return new Script(new byte[]{(byte) 0xaa});
            });
            Assertions.assertArrayEquals(federation.getRedeemScript().getProgram(), new byte[]{(byte) 0xaa});
            // Make sure the script creation happens only once
            Assertions.assertEquals(1, calls.size());
        }
    }

    @Test
    void P2SHScript() {
        final List<Integer> calls = new ArrayList<>();
        try (MockedStatic<ScriptBuilder> scriptBuilderMocked = mockStatic(ScriptBuilder.class)) {
            scriptBuilderMocked.when(() -> ScriptBuilder.createP2SHOutputScript(any(int.class), any(List.class))).thenAnswer((invocationOnMock) -> {
                calls.add(0);
                int numberOfSignaturesRequired = invocationOnMock.<Integer>getArgument(0);
                List<BtcECKey> publicKeys = invocationOnMock.getArgument(1);
                Assertions.assertEquals(4, numberOfSignaturesRequired);
                Assertions.assertEquals(6, publicKeys.size());
                for (int i = 0; i < sortedPublicKeys.size(); i++) {
                    Assertions.assertArrayEquals(sortedPublicKeys.get(i).getPubKey(),
                            publicKeys.get(i).getPubKey());
                }
                return new Script(new byte[]{(byte) 0xaa});
            });
            Assertions.assertArrayEquals(federation.getP2SHScript().getProgram(), new byte[]{(byte) 0xaa});
            // Make sure the script creation happens only once
            Assertions.assertEquals(1, calls.size());
        }
    }

    @Test
    void Address() {
        // Since we can't mock both Address and ScriptBuilder at the same time (due to PowerMockito limitations)
        // we use a well known P2SH and its corresponding address
        // and just mock the ScriptBuilder
        // a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87 => 2N5muMepJizJE1gR7FbHJU6CD18V3BpNF9p
        final List<Integer> calls = new ArrayList<>();
        try (MockedStatic<ScriptBuilder> scriptBuilderMocked = mockStatic(ScriptBuilder.class)) {
            scriptBuilderMocked.when(() -> ScriptBuilder.createP2SHOutputScript(any(int.class), any(List.class))).thenAnswer((invocationOnMock) -> {
                calls.add(0);
                int numberOfSignaturesRequired = invocationOnMock.<Integer>getArgument(0);
                List<BtcECKey> publicKeys = invocationOnMock.getArgument(1);
                Assertions.assertEquals(4, numberOfSignaturesRequired);
                Assertions.assertEquals(6, publicKeys.size());
                for (int i = 0; i < sortedPublicKeys.size(); i++) {
                    Assertions.assertArrayEquals(sortedPublicKeys.get(i).getPubKey(),
                            publicKeys.get(i).getPubKey());
                }
                return new Script(Hex.decode("a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87"));
            });

            Assertions.assertEquals("2N5muMepJizJE1gR7FbHJU6CD18V3BpNF9p", federation.getAddress().toBase58());
            // Make sure the address creation happens only once
            Assertions.assertEquals(1, calls.size());
        }
    }

    @Test
    void testEquals_basic() {
        Assertions.assertNotEquals(null, federation);
        Assertions.assertNotEquals(federation, new Object());
        Assertions.assertNotEquals("something else", federation);
    }

    @Test
    void testEquals_differentNumberOfMembers() {
        Federation otherFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600, 700),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentCreationTime() {
        Federation otherFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentCreationBlockNumber() {
        Federation otherFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                1L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        MatcherAssert.assertThat(federation, is(not(otherFederation)));
    }

    @Test
    void testEquals_differentNetworkParameters() {
        Federation otherFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_TESTNET)
        );
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentMembers() {
        List<FederationMember> members = FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500);

        members.add(new FederationMember(BtcECKey.fromPrivate(BigInteger.valueOf(610)), ECKey.fromPrivate(BigInteger.valueOf(600)), ECKey.fromPrivate(BigInteger.valueOf(620))));
        Federation otherFederation = new Federation(
                members,
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        members.remove(members.size()-1);
        members.add(new FederationMember(BtcECKey.fromPrivate(BigInteger.valueOf(600)), ECKey.fromPrivate(BigInteger.valueOf(610)), ECKey.fromPrivate(BigInteger.valueOf(620))));
        Federation yetOtherFederation = new Federation(
                members,
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Assertions.assertNotEquals(otherFederation, yetOtherFederation);
        Assertions.assertNotEquals(federation, otherFederation);
        Assertions.assertNotEquals(federation, yetOtherFederation);
    }

    @Test
    void testEquals_same() {
        Federation otherFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assertions.assertEquals(federation, otherFederation);
    }

    @Test
    void getBtcPublicKeyIndex() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            Assertions.assertEquals(i, federation.getBtcPublicKeyIndex(sortedPublicKeys.get(i)).intValue());
        }
        Assertions.assertNull(federation.getBtcPublicKeyIndex(BtcECKey.fromPrivate(BigInteger.valueOf(1234))));
    }

    @Test
    void hasBtcPublicKey() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            Assertions.assertTrue(federation.hasBtcPublicKey(sortedPublicKeys.get(i)));
        }
        Assertions.assertFalse(federation.hasBtcPublicKey(BtcECKey.fromPrivate(BigInteger.valueOf(1234))));
    }

    @Test
    void hasMemberWithRskAddress() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            Assertions.assertTrue(federation.hasMemberWithRskAddress(rskAddresses.get(i)));
        }

        byte[] nonFederateRskAddress = ECKey.fromPrivate(BigInteger.valueOf(1234)).getAddress();
        Assertions.assertFalse(federation.hasMemberWithRskAddress(nonFederateRskAddress));
    }

    @Test
    void testToString() {
        Assertions.assertEquals("4 of 6 signatures federation", federation.toString());
    }

    @Test
    void isMember(){
        //Both valid params
        FederationMember federationMember = federation.getMembers().get(0);
        Assertions.assertTrue(federation.isMember(federationMember));

        byte[] b = TestUtils.generateBytes("b",20);

        ECKey invalidRskKey = ECKey.fromPrivate(b);
        BtcECKey invalidBtcKey = BtcECKey.fromPrivate(b);

        // Valid PubKey, invalid rskAddress
        FederationMember invalidRskPubKey = new FederationMember(federationMember.getBtcPublicKey(), invalidRskKey, federationMember.getMstPublicKey());
        Assertions.assertFalse(federation.isMember(invalidRskPubKey));

        //Invalid PubKey, valid rskAddress
        FederationMember invalidBtcPubKey = new FederationMember(invalidBtcKey, federationMember.getRskPublicKey(), federationMember.getMstPublicKey());
        Assertions.assertFalse(federation.isMember(invalidBtcPubKey));

        //Valid btcKey & valid rskAddress, invalid mstKey
        FederationMember invalidMstPubKey = new FederationMember(federationMember.getBtcPublicKey(), federationMember.getRskPublicKey(), invalidRskKey);
        Assertions.assertFalse(federation.isMember(invalidMstPubKey));

        //All invalid params
        FederationMember invalidPubKeys = new FederationMember(invalidBtcKey, invalidRskKey, invalidRskKey);
        Assertions.assertFalse(federation.isMember(invalidPubKeys));
    }
}
