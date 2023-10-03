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
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.crypto.ECKey;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;

class StandardMultisigFederationTest {

    private List<BtcECKey> btcPublicKeys;
    private Federation federation;
    private List<BtcECKey> sortedPublicKeys;
    private List<ECKey> rskPubKeys;
    private List<byte[]> rskAddresses;

    @BeforeEach
    void createFederation() {
        btcPublicKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("023f0283519167f1603ba92b060146baa054712b938a61f35605ba08773142f4da")),
            BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")),
            BtcECKey.fromPublicOnly(Hex.decode("031174d64db12dc2dcdc8064a53a4981fa60f4ee649a954e01bcae221fc60777a2")),
            BtcECKey.fromPublicOnly(Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09")),
            BtcECKey.fromPublicOnly(Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9")));

        federation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithKeys(btcPublicKeys),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        sortedPublicKeys = btcPublicKeys.stream()
            .sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        rskPubKeys = sortedPublicKeys.stream()
                .map(btcECKey -> ECKey.fromPublicOnly(btcECKey.getPubKey()))
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
                Assertions.assertEquals(3, numberOfSignaturesRequired);
                Assertions.assertEquals(5, publicKeys.size());
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
    void testArrayEquals_P2SHScript() {
        ScriptBuilder p2shScriptBuilder = new ScriptBuilder();
        p2shScriptBuilder.op(ScriptOpCodes.OP_HASH160);
        p2shScriptBuilder.data(Hex.decode("57f76bf3ab818811c740929ac7a5e3ef8c7a34b9"));
        p2shScriptBuilder.op(ScriptOpCodes.OP_EQUAL);

        Script p2shScript = p2shScriptBuilder.build();

        Assertions.assertArrayEquals(federation.getP2SHScript().getProgram(), p2shScript.getProgram());
    }

    @Test
    void testEquals_Address() {
        Assertions.assertEquals("2N1GMB8gxHYR5HLPSRgf9CJ9Lunjb9CTnKB", federation.getAddress().toBase58());
    }

    @Test
    void testEquals_basic() {
        Assertions.assertNotEquals(null, federation);
        Assertions.assertNotEquals(federation, new Object());
        Assertions.assertNotEquals("something else", federation);
    }

    @Test
    void testEquals_differentNumberOfMembers() {
        Federation otherFederation = new StandardMultisigFederation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600, 700),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentCreationTime() {
        Federation otherFederation = new StandardMultisigFederation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentCreationBlockNumber() {
        Federation otherFederation = new StandardMultisigFederation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                1L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        MatcherAssert.assertThat(federation, is(not(otherFederation)));
    }

    @Test
    void testEquals_differentNetworkParameters() {
        Federation otherFederation = new StandardMultisigFederation(
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
        Federation otherFederation = new StandardMultisigFederation(
                members,
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        members.remove(members.size()-1);
        members.add(new FederationMember(BtcECKey.fromPrivate(BigInteger.valueOf(600)), ECKey.fromPrivate(BigInteger.valueOf(610)), ECKey.fromPrivate(BigInteger.valueOf(620))));
        Federation yetOtherFederation = new StandardMultisigFederation(
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
        Federation otherFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("023f0283519167f1603ba92b060146baa054712b938a61f35605ba08773142f4da")),
                BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")),
                BtcECKey.fromPublicOnly(Hex.decode("031174d64db12dc2dcdc8064a53a4981fa60f4ee649a954e01bcae221fc60777a2")),
                BtcECKey.fromPublicOnly(Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09")),
                BtcECKey.fromPublicOnly(Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9")))),
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
        Assertions.assertEquals("Got 3 of 5 signatures federation with address 2N1GMB8gxHYR5HLPSRgf9CJ9Lunjb9CTnKB", federation.toString());
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
