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

import static org.ethereum.TestUtils.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardMultisigFederationTest {

    private Federation federation;
    private List<BtcECKey> sortedPublicKeys;
    private List<byte[]> rskAddresses;
    private NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

    @BeforeEach
    void setUp() {
        List<BtcECKey> btcPublicKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(
                Hex.decode("023f0283519167f1603ba92b060146baa054712b938a61f35605ba08773142f4da")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("031174d64db12dc2dcdc8064a53a4981fa60f4ee649a954e01bcae221fc60777a2")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09")
            )
        );

        federation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithKeys(btcPublicKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            networkParameters
        );

        sortedPublicKeys = btcPublicKeys.stream()
            .sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        List<ECKey> rskPubKeys = sortedPublicKeys.stream()
            .map(btcECKey -> ECKey.fromPublicOnly(btcECKey.getPubKey()))
            .collect(Collectors.toList());

        rskAddresses = rskPubKeys
            .stream()
            .map(ECKey::getAddress)
            .collect(Collectors.toList());
    }

    @Test
    void createInvalidFederation_aboveMaxScriptSigSize() {
        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10",
                "fed11", "fed12", "fed13", "fed14", "fed15", "fed16"},
            true
        );
        List<FederationMember> standardMembers = FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys);

        assertThrows(FederationCreationException.class, () -> new StandardMultisigFederation(
            standardMembers,
            federation.getCreationTime(),
            federation.creationBlockNumber,
            federation.btcParams
        ));
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
    void getRedeemScript() {
        Script expectedScript = new Script(Hex.decode("5321023f0283519167f1603ba92b060146baa054712b938a61f35605ba08773142f4da2102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da21031174d64db12dc2dcdc8064a53a4981fa60f4ee649a954e01bcae221fc60777a2210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb955ae"));
        Script redeemScript = federation.getRedeemScript();
        assertEquals(expectedScript, redeemScript);

        int expectedChunks = sortedPublicKeys.size() + 3; // + 3 opcodes (OP_M, OP_N, OP_CHECKMULTISIG)
        assertEquals(expectedChunks, redeemScript.getChunks().size());

        int opM = ScriptOpCodes.getOpCode("" + federation.getNumberOfSignaturesRequired());
        assertEquals(opM, redeemScript.getChunks().get(0).opcode);

        for (int i = 0; i < sortedPublicKeys.size(); i++) {
            assertArrayEquals(sortedPublicKeys.get(i).getPubKey(), redeemScript.getChunks().get(i+1).data);
        }

        int opN = ScriptOpCodes.getOpCode("" + federation.getSize());
        assertEquals(opN, redeemScript.getChunks().get(redeemScript.getChunks().size() - 2).opcode);
        assertEquals(ScriptOpCodes.OP_CHECKMULTISIG, redeemScript.getChunks().get(redeemScript.getChunks().size() - 1).opcode);
    }

    @Test
    void getP2SHScript() {
        ScriptBuilder p2shScriptBuilder = new ScriptBuilder();
        p2shScriptBuilder.op(ScriptOpCodes.OP_HASH160);
        p2shScriptBuilder.data(Hex.decode("57f76bf3ab818811c740929ac7a5e3ef8c7a34b9"));
        p2shScriptBuilder.op(ScriptOpCodes.OP_EQUAL);

        Script p2shScript = p2shScriptBuilder.build();

        assertEquals(federation.getP2SHScript(), p2shScript);
    }

    @Test
    void getAddress() {
        assertEquals("2N1GMB8gxHYR5HLPSRgf9CJ9Lunjb9CTnKB", federation.getAddress().toBase58());
    }

    @Test
    void testEquals_basic() {
        assertNotEquals(null, federation);
        assertNotEquals(federation, new Object());
    }

    @Test
    void testEquals_differentCreationTime() {
        Federation otherFederation = new StandardMultisigFederation(
            federation.getMembers(),
            federation.getCreationTime().plus(1, ChronoUnit.MILLIS),
            federation.getCreationBlockNumber(),
            networkParameters
        );
        assertEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentCreationBlockNumber() {
        Federation otherFederation = new StandardMultisigFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber() + 1,
            networkParameters
        );
        assertEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentNetworkParameters() {
        Federation otherFederation = new StandardMultisigFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET)
        );
        // Different network parameters will result in a different address
        assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentMembers() {
        List<FederationMember> differentMembers = FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500);
        Federation otherFederation = new StandardMultisigFederation(
            differentMembers,
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            networkParameters
        );

        differentMembers.remove(differentMembers.size()-1);
        differentMembers.add(new FederationMember(
            BtcECKey.fromPrivate(BigInteger.valueOf(600)),
            ECKey.fromPrivate(BigInteger.valueOf(610)),
            ECKey.fromPrivate(BigInteger.valueOf(620))
        ));
        Federation yetOtherFederation = new StandardMultisigFederation(
            differentMembers,
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            networkParameters
        );

        assertNotEquals(otherFederation, yetOtherFederation);
        assertNotEquals(federation, otherFederation);
        assertNotEquals(federation, yetOtherFederation);
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
            networkParameters
        );
        assertEquals(federation, otherFederation);
    }

    @Test
    void getBtcPublicKeyIndex() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            Optional<Integer> index = federation.getBtcPublicKeyIndex(sortedPublicKeys.get(i));
            assertTrue(index.isPresent());
            assertEquals(i, index.get().intValue());
        }
        assertFalse(federation.getBtcPublicKeyIndex(BtcECKey.fromPrivate(BigInteger.valueOf(1234))).isPresent());
    }

    @Test
    void hasBtcPublicKey() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            assertTrue(federation.hasBtcPublicKey(sortedPublicKeys.get(i)));
        }
        assertFalse(federation.hasBtcPublicKey(BtcECKey.fromPrivate(BigInteger.valueOf(1234))));
    }

    @Test
    void hasMemberWithRskAddress() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            assertTrue(federation.hasMemberWithRskAddress(rskAddresses.get(i)));
        }

        byte[] nonFederateRskAddress = ECKey.fromPrivate(BigInteger.valueOf(1234)).getAddress();
        assertFalse(federation.hasMemberWithRskAddress(nonFederateRskAddress));
    }

    @Test
    void testToString() {
        assertEquals(
            "Got 3 of 5 signatures federation with address 2N1GMB8gxHYR5HLPSRgf9CJ9Lunjb9CTnKB",
            federation.toString()
        );
    }

    @Test
    void isMember(){
        //Both valid params
        FederationMember federationMember = federation.getMembers().get(0);
        assertTrue(federation.isMember(federationMember));

        byte[] b = TestUtils.generateBytes("b",20);

        ECKey invalidRskKey = ECKey.fromPrivate(b);
        BtcECKey invalidBtcKey = BtcECKey.fromPrivate(b);

        // Valid PubKey, invalid rskAddress
        FederationMember invalidRskPubKey = new FederationMember(
            federationMember.getBtcPublicKey(),
            invalidRskKey,
            federationMember.getMstPublicKey()
        );
        assertFalse(federation.isMember(invalidRskPubKey));

        //Invalid PubKey, valid rskAddress
        FederationMember invalidBtcPubKey = new FederationMember(
            invalidBtcKey,
            federationMember.getRskPublicKey(),
            federationMember.getMstPublicKey()
        );
        assertFalse(federation.isMember(invalidBtcPubKey));

        //Valid btcKey & valid rskAddress, invalid mstKey
        FederationMember invalidMstPubKey = new FederationMember(
            federationMember.getBtcPublicKey(),
            federationMember.getRskPublicKey(),
            invalidRskKey
        );
        assertFalse(federation.isMember(invalidMstPubKey));

        //All invalid params
        FederationMember invalidPubKeys = new FederationMember(invalidBtcKey, invalidRskKey, invalidRskKey);
        assertFalse(federation.isMember(invalidPubKeys));
    }
}
