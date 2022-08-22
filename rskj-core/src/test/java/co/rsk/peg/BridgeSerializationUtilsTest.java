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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.resources.TestConstants;
import co.rsk.peg.utils.MerkleTreeUtils;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedBytes;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeSerializationUtilsTest {

    @Test
    public void serializeMapOfHashesToLong() throws Exception {
        Map<Sha256Hash, Long> sample = new HashMap<>();
        sample.put(Sha256Hash.wrap(charNTimes('b', 64)), 1L);
        sample.put(Sha256Hash.wrap(charNTimes('d', 64)), 2L);
        sample.put(Sha256Hash.wrap(charNTimes('a', 64)), 3L);
        sample.put(Sha256Hash.wrap(charNTimes('c', 64)), 4L);

        byte[] result = BridgeSerializationUtils.serializeMapOfHashesToLong(sample);

        String hexResult = ByteUtil.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();

        expectedBuilder.append("f888");

        char[] sorted = new char[]{'a','b','c','d'};

        for (char c : sorted) {
            String key = charNTimes(c, 64);
            expectedBuilder.append("a0");
            expectedBuilder.append(key);
            expectedBuilder.append("0" + String.valueOf(sample.get(Sha256Hash.wrap(key)).longValue()));
        }

        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void deserializeMapOfHashesToLong_emptyOrNull() throws Exception {
        assertEquals(BridgeSerializationUtils.deserializeMapOfHashesToLong(null), new HashMap<>());
        assertEquals(BridgeSerializationUtils.deserializeMapOfHashesToLong(new byte[]{}), new HashMap<>());
    }

    @Test
    public void deserializeMapOfHashesToLong_nonEmpty() throws Exception {
        byte[] rlpFirstKey = RLP.encodeElement(Hex.decode(charNTimes('b', 64)));
        byte[] rlpSecondKey = RLP.encodeElement(Hex.decode(charNTimes('d', 64)));
        byte[] rlpThirdKey = RLP.encodeElement(Hex.decode(charNTimes('a', 64)));

        byte[] rlpFirstValue = RLP.encodeBigInteger(BigInteger.valueOf(7));
        byte[] rlpSecondValue = RLP.encodeBigInteger(BigInteger.valueOf(76));
        byte[] rlpThirdValue = RLP.encodeBigInteger(BigInteger.valueOf(123));

        byte[] data = RLP.encodeList(rlpFirstKey, rlpFirstValue, rlpSecondKey, rlpSecondValue, rlpThirdKey, rlpThirdValue);

        Map<Sha256Hash, Long> result = BridgeSerializationUtils.deserializeMapOfHashesToLong(data);
        assertEquals(3, result.size());
        assertEquals(7L, result.get(Sha256Hash.wrap(charNTimes('b', 64))).longValue());
        assertEquals(76L, result.get(Sha256Hash.wrap(charNTimes('d', 64))).longValue());
        assertEquals(123L, result.get(Sha256Hash.wrap(charNTimes('a', 64))).longValue());
    }

    @Test
    public void deserializeMapOfHashesToLong_nonEmptyOddSize() throws Exception {
        byte[] rlpFirstKey = RLP.encodeElement(Hex.decode(charNTimes('b', 64)));
        byte[] rlpSecondKey = RLP.encodeElement(Hex.decode(charNTimes('d', 64)));
        byte[] rlpThirdKey = RLP.encodeElement(Hex.decode(charNTimes('a', 64)));
        byte[] rlpFourthKey = RLP.encodeElement(Hex.decode(charNTimes('e', 64)));

        byte[] rlpFirstValue = RLP.encodeBigInteger(BigInteger.valueOf(7));
        byte[] rlpSecondValue = RLP.encodeBigInteger(BigInteger.valueOf(76));
        byte[] rlpThirdValue = RLP.encodeBigInteger(BigInteger.valueOf(123));

        byte[] data = RLP.encodeList(rlpFirstKey, rlpFirstValue, rlpSecondKey, rlpSecondValue, rlpThirdKey, rlpThirdValue, rlpFourthKey);

        boolean thrown = false;

        try {
            BridgeSerializationUtils.deserializeMapOfHashesToLong(data);
        } catch (RuntimeException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @Test
    public void serializeFederationOnlyBtcKeys() throws Exception {
        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        // Only actual keys serialized are BTC keys, so we don't really care about RSK or MST keys
        Federation federation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(new BtcECKey[]{
                    BtcECKey.fromPublicOnly(publicKeyBytes[0]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[1]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[2]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[3]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[4]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[5]),
            })),
            Instant.ofEpochMilli(0xabcdef), //
            42L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        byte[] result = BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federation);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("f8d3"); // Outer list
        expectedBuilder.append("83abcdef"); // Creation time
        expectedBuilder.append("2a"); // Creation block number
        expectedBuilder.append("f8cc"); // Inner list

        federation.getBtcPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("a1");
            expectedBuilder.append(ByteUtil.toHexString(key.getPubKey()));
        });

        String expected = expectedBuilder.toString();

        Assert.assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    public void deserializeFederationOnlyBtcKeys_ok() throws Exception {
        byte[][] publicKeyBytes = Arrays.asList(100, 200, 300, 400, 500, 600).stream()
                .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(k -> k.getPubKey())
                .toArray(byte[][]::new);

        byte[][] rlpKeys = new byte[publicKeyBytes.length][];

        for (int k = 0; k < publicKeyBytes.length; k++) {
            rlpKeys[k] = RLP.encodeElement(publicKeyBytes[k]);
        }

        byte[] rlpFirstElement = RLP.encodeElement(Hex.decode("1388")); // First element (creation date -> 5000 milliseconds from epoch)
        byte[] rlpSecondElement = RLP.encodeElement(Hex.decode("002a")); // Second element block number 42
        byte[] rlpKeyList = RLP.encodeList(rlpKeys);

        byte[] data = RLP.encodeList(rlpFirstElement, rlpSecondElement, rlpKeyList);

        Federation deserializedFederation = BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(data, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        Assert.assertEquals(5000, deserializedFederation.getCreationTime().toEpochMilli());
        Assert.assertEquals(4, deserializedFederation.getNumberOfSignaturesRequired());
        Assert.assertEquals(6, deserializedFederation.getBtcPublicKeys().size());
        Assert.assertThat(deserializedFederation.getCreationBlockNumber(), is(42L));

        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeyBytes[i], deserializedFederation.getBtcPublicKeys().get(i).getPubKey()));
        }

        Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), deserializedFederation.getBtcParams());
    }

    @Test
    public void deserializeFederationOnlyBtcKeys_wrongListSize() throws Exception {
        byte[] rlpFirstElement = RLP.encodeElement(Hex.decode("1388")); // First element (creation date -> 5000 milliseconds from epoch)
        byte[] rlpSecondElement = RLP.encodeElement(Hex.decode("03")); // Second element (# of signatures required - 3)
        byte[] rlpThirdElement = RLP.encodeElement(Hex.decode("03"));
        byte[] rlpFourthElement = RLP.encodeElement(Hex.decode("aabbccdd"));

        byte[] data = RLP.encodeList(rlpFirstElement, rlpSecondElement, rlpThirdElement, rlpFourthElement);

        boolean thrown = false;

        try {
            BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(data, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Expected 3 elements"));
            thrown = true;
        }

        Assert.assertTrue(thrown);
    }

    @Test
    public void serializeAndDeserializeFederation_beforeRskip284_testnet() {
        testSerializeAndDeserializeFederation(false, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void serializeAndDeserializeFederation_beforeRskip284_mainnet() {
        testSerializeAndDeserializeFederation(false, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void serializeAndDeserializeFederation_afterRskip284_testnet() {
        testSerializeAndDeserializeFederation(true, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void serializeAndDeserializeFederation_afterRskip284_mainnet() {
        testSerializeAndDeserializeFederation(true, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void serializeFederation_serializedKeysAreCompressedAndThree() {
        final int NUM_MEMBERS = 10;
        final int EXPECTED_NUM_KEYS = 3;
        final int EXPECTED_PUBLICKEY_SIZE = 33;

        List<FederationMember> members = new ArrayList<>();
        for (int j = 0; j < NUM_MEMBERS; j++) {
            members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        }

        Federation testFederation = new Federation(
                members, Instant.now(), 123, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        byte[] serializedFederation = BridgeSerializationUtils.serializeFederation(testFederation);

        RLPList serializedList = (RLPList) RLP.decode2(serializedFederation).get(0);

        Assert.assertEquals(3, serializedList.size());

        RLPList memberList = (RLPList) serializedList.get(2);

        Assert.assertEquals(NUM_MEMBERS, memberList.size());

        for (int i = 0; i < NUM_MEMBERS; i++) {
            RLPList memberKeys = (RLPList) RLP.decode2(memberList.get(i).getRLPData()).get(0);
            Assert.assertEquals(EXPECTED_NUM_KEYS, memberKeys.size());
            for (int j = 0; j < EXPECTED_NUM_KEYS; j++) {
                Assert.assertEquals(EXPECTED_PUBLICKEY_SIZE, memberKeys.get(j).getRLPData().length);
            }

        }
    }

    @Test
    public void deserializeFederation_wrongListSize() {
        byte[] serialized = RLP.encodeList(RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0]));

        try {
            BridgeSerializationUtils.deserializeFederation(serialized, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized Federation"));
        }
    }

    @Test
    public void deserializeFederation_invalidFederationMember() {
        byte[] serialized = RLP.encodeList(
                RLP.encodeElement(BigInteger.valueOf(1).toByteArray()),
                RLP.encodeElement(BigInteger.valueOf(1).toByteArray()),
                RLP.encodeList(RLP.encodeList(RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0])))
        );

        try {
            BridgeSerializationUtils.deserializeFederation(serialized, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized FederationMember"));
        }
    }

    @Test
    public void serializeAndDeserializePendingFederation() {
        final int NUM_CASES = 20;

        for (int i = 0; i < NUM_CASES; i++) {
            int numMembers = randomInRange(2, 14);
            List<FederationMember> members = new ArrayList<>();
            for (int j = 0; j < numMembers; j++) {
                members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
            }
            PendingFederation testPendingFederation = new PendingFederation(members);

            byte[] serializedTestPendingFederation = BridgeSerializationUtils.serializePendingFederation(testPendingFederation);

            PendingFederation deserializedTestPendingFederation = BridgeSerializationUtils.deserializePendingFederation(
                    serializedTestPendingFederation);

            Assert.assertEquals(testPendingFederation, deserializedTestPendingFederation);
        }
    }

    @Test
    public void serializePendingFederation_serializedKeysAreCompressedAndThree() {
        final int NUM_MEMBERS = 10;
        final int EXPECTED_NUM_KEYS = 3;
        final int EXPECTED_PUBLICKEY_SIZE = 33;

        List<FederationMember> members = new ArrayList<>();
        for (int j = 0; j < NUM_MEMBERS; j++) {
            members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        }

        PendingFederation testPendingFederation = new PendingFederation(members);

        byte[] serializedPendingFederation = BridgeSerializationUtils.serializePendingFederation(testPendingFederation);

        RLPList memberList = (RLPList) RLP.decode2(serializedPendingFederation).get(0);

        Assert.assertEquals(NUM_MEMBERS, memberList.size());

        for (int i = 0; i < NUM_MEMBERS; i++) {
            RLPList memberKeys = (RLPList) RLP.decode2(memberList.get(i).getRLPData()).get(0);
            Assert.assertEquals(EXPECTED_NUM_KEYS, memberKeys.size());
            for (int j = 0; j < EXPECTED_NUM_KEYS; j++) {
                Assert.assertEquals(EXPECTED_PUBLICKEY_SIZE, memberKeys.get(j).getRLPData().length);
            }

        }
    }

    @Test
    public void deserializePendingFederation_invalidFederationMember() {
        byte[] serialized = RLP.encodeList(
                RLP.encodeList(RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0]))
        );

        try {
            BridgeSerializationUtils.deserializePendingFederation(serialized);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized FederationMember"));
        }
    }

    @Test
    public void serializePendingFederationOnlyBtcKeys() throws Exception {
        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        // Only actual keys serialized are BTC keys, so we don't really care about RSK or MST keys
        PendingFederation pendingFederation = new PendingFederation(
                FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPublicOnly(publicKeyBytes[0]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[1]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[2]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[3]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[4]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[5]),
                }))
        );

        byte[] result = BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(pendingFederation);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("f8cc");
        pendingFederation.getBtcPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("a1");
            expectedBuilder.append(ByteUtil.toHexString(key.getPubKey()));
        });

        String expected = expectedBuilder.toString();
        Assert.assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    public void deserializePendingFederationOnlyBtcKeys() throws Exception {
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

        PendingFederation deserializedPendingFederation = BridgeSerializationUtils.deserializePendingFederationOnlyBtcKeys(data);

        Assert.assertEquals(6, deserializedPendingFederation.getBtcPublicKeys().size());
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeyBytes[i], deserializedPendingFederation.getBtcPublicKeys().get(i).getPubKey()));
        }
    }

    @Test
    public void serializeElection() throws Exception {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        Map<ABICallSpec, List<RskAddress>> sampleVotes = new HashMap<>();
        sampleVotes.put(
                new ABICallSpec("one-function", new byte[][]{}),
                Arrays.asList(createAddress("8899"), createAddress("aabb"))
        );
        sampleVotes.put(
                new ABICallSpec("another-function", new byte[][]{ Hex.decode("01"), Hex.decode("0203") }),
                Arrays.asList(createAddress("ccdd"), createAddress("eeff"), createAddress("0011"))
        );
        sampleVotes.put(
                new ABICallSpec("yet-another-function", new byte[][]{ Hex.decode("0405") }),
                Arrays.asList(createAddress("fa"), createAddress("ca"))
        );

        ABICallElection sample = new ABICallElection(authorizer, sampleVotes);

        byte[] result = BridgeSerializationUtils.serializeElection(sample);
        String hexResult = ByteUtil.toHexString(result);

        StringBuilder expectedBuilder = new StringBuilder();

        expectedBuilder.append("f8d7d6");

        expectedBuilder.append("90");
        expectedBuilder.append(ByteUtil.toHexString("another-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("c4");
        expectedBuilder.append("01");
        expectedBuilder.append("820203");
        expectedBuilder.append("f83f");
        expectedBuilder.append("94" + createAddress("0011").toString());
        expectedBuilder.append("94" + createAddress("ccdd").toString());
        expectedBuilder.append("94" + createAddress("eeff").toString());

        expectedBuilder.append("ce");
        expectedBuilder.append("8c");
        expectedBuilder.append(ByteUtil.toHexString("one-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("c0");

        expectedBuilder.append("ea");
        expectedBuilder.append("94" + createAddress("8899").toString());
        expectedBuilder.append("94" + createAddress("aabb").toString());

        expectedBuilder.append("d9");
        expectedBuilder.append("94");
        expectedBuilder.append(ByteUtil.toHexString("yet-another-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("c3");
        expectedBuilder.append("820405");
        expectedBuilder.append("ea");
        expectedBuilder.append("94" + createAddress("ca").toString() + "94" + createAddress("fa").toString());

        Assert.assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void deserializeElection_emptyOrNull() throws Exception {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();
        ABICallElection election;
        election = BridgeSerializationUtils.deserializeElection(null, authorizer);
        Assert.assertEquals(0, election.getVotes().size());
        election = BridgeSerializationUtils.deserializeElection(new byte[]{}, authorizer);
        Assert.assertEquals(0, election.getVotes().size());
    }

    @Test
    public void deserializeElection_nonEmpty() throws Exception {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        ABICallSpec firstSpec = new ABICallSpec("funct", new byte[][]{});
        List<RskAddress> firstVoters = Arrays.asList(
                createAddress("aa"),
                createAddress("bbccdd")
        );

        ABICallSpec secondSpec = new ABICallSpec("other-funct", new byte[][]{
                Hex.decode("1122"),
                Hex.decode("334455")
        });;
        List<RskAddress> secondVoters = Arrays.asList(
                createAddress("55"),
                createAddress("66"),
                createAddress("77")
        );

        ABICallSpec thirdSpec = new ABICallSpec("random-funct", new byte[][]{
                Hex.decode("aabb")
        });

        List<RskAddress> thirdVoters = Arrays.asList(
                createAddress("1111"),
                createAddress("3333"),
                createAddress("5555"),
                createAddress("77")
        );

        Map<ABICallSpec, List<RskAddress>> specsVotersToProcess = new HashMap<>();

        specsVotersToProcess.put(firstSpec, firstVoters);
        specsVotersToProcess.put(secondSpec, secondVoters);
        specsVotersToProcess.put(thirdSpec, thirdVoters);

        Assert.assertNotEquals(0, thirdVoters.get(0).getBytes().length);

        ABICallElection electionToProcess = new ABICallElection(authorizer, specsVotersToProcess);

        byte[] data = BridgeSerializationUtils.serializeElection(electionToProcess);

        ABICallElection election = BridgeSerializationUtils.deserializeElection(data, authorizer);

        Assert.assertEquals(3, election.getVotes().size());
        List<RskAddress> voters;
        ABICallSpec spec;

        spec = new ABICallSpec("funct", new byte[][]{});
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                createAddress("aa"),
                createAddress("bbccdd")
        );

        Assert.assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        Assert.assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());

        spec = new ABICallSpec("other-funct", new byte[][]{
                Hex.decode("1122"),
                Hex.decode("334455")
        });
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                createAddress("55"),
                createAddress("66"),
                createAddress("77")
        );

        Assert.assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        Assert.assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());
        Assert.assertArrayEquals(voters.get(2).getBytes(), election.getVotes().get(spec).get(2).getBytes());

        spec = new ABICallSpec("random-funct", new byte[][]{
                Hex.decode("aabb")
        });
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                createAddress("1111"),
                createAddress("3333"),
                createAddress("5555"),
                createAddress("77")
        );

        Assert.assertEquals(4, election.getVotes().get(spec).size());
        Assert.assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        Assert.assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());
        Assert.assertArrayEquals(voters.get(2).getBytes(), election.getVotes().get(spec).get(2).getBytes());
        Assert.assertArrayEquals(voters.get(3).getBytes(), election.getVotes().get(spec).get(3).getBytes());
    }

    @Test
    public void deserializeElection_unevenOuterList() throws Exception {
        AddressBasedAuthorizer mockedAuthorizer = mock(AddressBasedAuthorizer.class);

        byte[] rlpFirstElement = RLP.encodeElement(Hex.decode("010203"));
        byte[] data = RLP.encodeList(rlpFirstElement);

        try {
            BridgeSerializationUtils.deserializeElection(data, mockedAuthorizer);
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("expected an even number of entries, but odd given"));
            return;
        }

        Assert.fail();
    }

    @Test
    public void deserializeElection_invalidCallSpec() throws Exception {
        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        byte[] rlpFirstSpec = RLP.encodeList(RLP.encode(Hex.decode("010203"))); // invalid spec
        byte[] rlpFirstVoters = RLP.encodeList(RLP.encodeElement(Hex.decode("03"))); // doesn't matter

        byte[] data = RLP.encodeList(rlpFirstSpec, rlpFirstVoters);

        try {
            BridgeSerializationUtils.deserializeElection(data, authorizer);
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized ABICallSpec"));
            return;
        }

        Assert.fail();
    }

    @Test
    public void serializeLockWhitelist() throws Exception {
        byte[][] addressesBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKeyHash(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKeyHash(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKeyHash(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKeyHash(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKeyHash(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKeyHash(),
        };
        Coin maxToTransfer = Coin.CENT;

        LockWhitelist lockWhitelist = new LockWhitelist(
            Arrays.stream(addressesBytes)
                .map(bytes -> new Address(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), bytes))
                .collect(Collectors.toMap(Function.identity(), k -> new OneOffWhiteListEntry(k, maxToTransfer))),
                0);

        byte[] result = BridgeSerializationUtils.serializeOneOffLockWhitelist(Pair.of(
                lockWhitelist.getAll(OneOffWhiteListEntry.class),
                lockWhitelist.getDisableBlockHeight()
        ));
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("f897");
        Arrays.stream(addressesBytes).sorted(UnsignedBytes.lexicographicalComparator()).forEach(bytes -> {
            expectedBuilder.append("94");
            expectedBuilder.append(ByteUtil.toHexString(bytes));
            expectedBuilder.append("83");
            expectedBuilder.append(ByteUtil.toHexString(BigInteger.valueOf(maxToTransfer.value).toByteArray()));
        });
        expectedBuilder.append("80");
        String expected = expectedBuilder.toString();
        Assert.assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    public void deserializeOneOffLockWhitelistAndDisableBlockHeight() throws Exception {
        byte[][] addressesBytes = Arrays.asList(100, 200, 300, 400).stream()
                .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(k -> k.getPubKeyHash())
                .toArray(byte[][]::new);

        byte[][] rlpBytes = new byte[9][0];

        for (int k = 0; k < addressesBytes.length; k++) {
            rlpBytes[k * 2] = RLP.encodeElement(addressesBytes[k]);
            rlpBytes[k * 2 + 1] = RLP.encodeElement(Hex.decode("0186a0")); // Coin.MILLICOIN
        }

        rlpBytes[8] = RLP.encodeElement(Hex.decode("002a"));

        byte[] data = RLP.encodeList(rlpBytes);

        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(
                data,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Assert.assertThat(deserializedLockWhitelist.getLeft().size(), is(addressesBytes.length));
        Assert.assertThat(deserializedLockWhitelist.getLeft().keySet().stream().map(Address::getHash160).collect(Collectors.toList()), containsInAnyOrder(addressesBytes));
        Set<Coin> deserializedCoins = deserializedLockWhitelist.getLeft().values().stream().map(entry -> ((OneOffWhiteListEntry)entry).maxTransferValue()).collect(Collectors.toSet());
        Assert.assertThat(deserializedCoins, hasSize(1));
        Assert.assertThat(deserializedCoins, hasItem(Coin.MILLICOIN));
        Assert.assertThat(deserializedLockWhitelist.getRight(), is(42));
    }

    @Test
    public void deserializeOneOffLockWhitelistAndDisableBlockHeight_null() throws Exception {
        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(
                null,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Assert.assertNull(deserializedLockWhitelist);

        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist2 = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(
                new byte[]{},
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Assert.assertNull(deserializedLockWhitelist2);
    }

    @Test
    public void serializeDeserializeOneOffLockWhitelistAndDisableBlockHeight() {
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        Map<Address, LockWhitelistEntry> whitelist = new HashMap<>();
        Address address = BtcECKey.fromPrivate(BigInteger.valueOf(100L)).toAddress(btcParams);
        whitelist.put(address, new OneOffWhiteListEntry(address, Coin.COIN));

        LockWhitelist originalLockWhitelist = new LockWhitelist(whitelist, 0);
        byte[] serializedLockWhitelist = BridgeSerializationUtils.serializeOneOffLockWhitelist(Pair.of(
                originalLockWhitelist.getAll(OneOffWhiteListEntry.class),
                originalLockWhitelist.getDisableBlockHeight()
        ));
        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(serializedLockWhitelist, btcParams);

        List<Address> originalAddresses = originalLockWhitelist.getAddresses();
        List<Address> deserializedAddresses = new ArrayList(deserializedLockWhitelist.getLeft().keySet());
        Assert.assertThat(originalAddresses, hasSize(1));
        Assert.assertThat(deserializedAddresses, hasSize(1));
        Assert.assertThat(originalAddresses, is(deserializedAddresses));
        Assert.assertThat(
                ((OneOffWhiteListEntry)originalLockWhitelist.get(originalAddresses.get(0))).maxTransferValue(),
                is((deserializedLockWhitelist.getLeft().get(deserializedAddresses.get(0))).maxTransferValue()));
    }

    @Test
    public void serializeAndDeserializeFederationOnlyBtcKeysWithRealRLP() {
        NetworkParameters networkParms = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        // Only actual keys serialized are BTC keys, so deserialization will fill RSK and MST keys with those
        Federation federation = new Federation(
                FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
                        BtcECKey.fromPublicOnly(publicKeyBytes[0]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[1]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[2]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[3]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[4]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[5])
                )),
                Instant.ofEpochMilli(0xabcdef),
                42L,
                networkParms
        );

        byte[] result = BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federation);
        Federation deserializedFederation = BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(result, networkParms);
        Assert.assertThat(federation, is(deserializedFederation));
    }

    @Test
    public void serializeRequestQueue() throws Exception {
        List<ReleaseRequestQueue.Entry> sampleEntries = Arrays.asList(
                new ReleaseRequestQueue.Entry(mockAddressHash160("ccdd"), Coin.valueOf(10)),
                new ReleaseRequestQueue.Entry(mockAddressHash160("bb"), Coin.valueOf(50)),
                new ReleaseRequestQueue.Entry(mockAddressHash160("bb"), Coin.valueOf(20)),
                new ReleaseRequestQueue.Entry(mockAddressHash160("aa"), Coin.valueOf(30))
        );
        ReleaseRequestQueue sample = new ReleaseRequestQueue(sampleEntries);

        byte[] result = BridgeSerializationUtils.serializeReleaseRequestQueue(sample);
        String hexResult = ByteUtil.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("cd");
        expectedBuilder.append("82ccdd");
        expectedBuilder.append("0a");
        expectedBuilder.append("81bb");
        expectedBuilder.append("32");
        expectedBuilder.append("81bb");
        expectedBuilder.append("14");
        expectedBuilder.append("81aa");
        expectedBuilder.append("1e");
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void deserializeRequestQueue_emptyOrNull() throws Exception {
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseRequestQueue(null, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).size());
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseRequestQueue(new byte[]{}, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).size());
    }

    @Test
    public void deserializeRequestQueue_nonEmpty() throws Exception {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        Address a1 = Address.fromBase58(params, "mynmcQfJnVjheAqh9XL6htnxPZnaDFbqkB");
        Address a2 = Address.fromBase58(params, "mfrfxeo5L2f5NDURS6YTtCNfVw2t5HAfty");
        Address a3 = Address.fromBase58(params, "myw7AMh5mpKHao6MArhn7EvkeASGsGJzrZ");

        List<ReleaseRequestQueue.Entry> expectedEntries = Arrays.asList(
                new ReleaseRequestQueue.Entry(a1, Coin.valueOf(10)),
                new ReleaseRequestQueue.Entry(a2, Coin.valueOf(7)),
                new ReleaseRequestQueue.Entry(a3, Coin.valueOf(8))
        );

        byte[][] rlpItems = new byte[6][];

        rlpItems[0] = RLP.encodeElement(a1.getHash160());
        rlpItems[1] = RLP.encodeBigInteger(BigInteger.valueOf(10));
        rlpItems[2] = RLP.encodeElement(a2.getHash160());
        rlpItems[3] = RLP.encodeBigInteger(BigInteger.valueOf(7));
        rlpItems[4] = RLP.encodeElement(a3.getHash160());
        rlpItems[5] = RLP.encodeBigInteger(BigInteger.valueOf(8));

        byte[] data = RLP.encodeList(rlpItems);

        ReleaseRequestQueue result = new ReleaseRequestQueue(BridgeSerializationUtils.deserializeReleaseRequestQueue(data, params));

        List<ReleaseRequestQueue.Entry> entries = result.getEntries();
        assertEquals(expectedEntries, entries);
    }

    @Test
    public void deserializeRequestQueue_nonEmptyOddSize() throws Exception {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        Address a1 = Address.fromBase58(params, "mynmcQfJnVjheAqh9XL6htnxPZnaDFbqkB");
        Address a2 = Address.fromBase58(params, "mfrfxeo5L2f5NDURS6YTtCNfVw2t5HAfty");
        Address a3 = Address.fromBase58(params, "myw7AMh5mpKHao6MArhn7EvkeASGsGJzrZ");

        byte[][] rlpItems = new byte[7][];

        rlpItems[0] = RLP.encodeElement(a1.getHash160());
        rlpItems[1] = RLP.encodeBigInteger(BigInteger.valueOf(10));
        rlpItems[2] = RLP.encodeElement(a2.getHash160());
        rlpItems[3] = RLP.encodeBigInteger(BigInteger.valueOf(7));
        rlpItems[4] = RLP.encodeElement(a3.getHash160());
        rlpItems[5] = RLP.encodeBigInteger(BigInteger.valueOf(8));
        rlpItems[6] = RLP.encodeBigInteger(BigInteger.valueOf(8));

        byte[] data = RLP.encodeList(rlpItems);

        try {
            BridgeSerializationUtils.deserializeReleaseRequestQueue(data, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        } catch (RuntimeException e) {
            return;
        }
        Assert.fail();
    }

    @Test
    public void serializeTransactionSet() throws Exception {
        Set<ReleaseTransactionSet.Entry> sampleEntries = new HashSet<>(Arrays.asList(
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("ccdd"), 10L),
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("bb"), 20L),
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("ba"), 30L),
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("aa"), 40L)
        ));
        ReleaseTransactionSet sample = new ReleaseTransactionSet(sampleEntries);

        byte[] result = BridgeSerializationUtils.serializeReleaseTransactionSet(sample);
        String hexResult = ByteUtil.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("cd");
        expectedBuilder.append("81aa");
        expectedBuilder.append("28");
        expectedBuilder.append("81ba");
        expectedBuilder.append("1e");
        expectedBuilder.append("81bb");
        expectedBuilder.append("14");
        expectedBuilder.append("82ccdd");
        expectedBuilder.append("0a");
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void deserializeTransactionSet_emptyOrNull() throws Exception {
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseTransactionSet(null, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).getEntries().size());
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseTransactionSet(new byte[]{}, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).getEntries().size());
    }

    @Test
    public void deserializeTransactionSet_nonEmpty() throws Exception {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        BtcTransaction input = new BtcTransaction(params);
        input.addOutput(Coin.FIFTY_COINS, Address.fromBase58(params, "mvc8mwDcdLEq2jGqrL43Ub3sxTR13tB8LL"));

        BtcTransaction t1 = new BtcTransaction(params);
        t1.addInput(input.getOutput(0));
        t1.addOutput(Coin.COIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));
        BtcTransaction t2 = new BtcTransaction(params);
        t2.addInput(input.getOutput(0));
        t2.addOutput(Coin.COIN.multiply(10), Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));
        BtcTransaction t3 = new BtcTransaction(params);
        t3.addInput(input.getOutput(0));
        t3.addOutput(Coin.valueOf(15), Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));
        BtcTransaction t4 = new BtcTransaction(params);
        t4.addInput(input.getOutput(0));
        t4.addOutput(Coin.MILLICOIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));

        Set<ReleaseTransactionSet.Entry> expectedEntries = new HashSet<>(Arrays.asList(
                new ReleaseTransactionSet.Entry(t1, 32L),
                new ReleaseTransactionSet.Entry(t2, 14L),
                new ReleaseTransactionSet.Entry(t3, 102L),
                new ReleaseTransactionSet.Entry(t4, 20L)
        ));

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(expectedEntries);

        byte[] data = BridgeSerializationUtils.serializeReleaseTransactionSet(releaseTransactionSet);

        ReleaseTransactionSet result = BridgeSerializationUtils.deserializeReleaseTransactionSet(data, params);

        Set<ReleaseTransactionSet.Entry> entries = result.getEntries();

        assertEquals(expectedEntries, entries);
    }

    @Test(expected = RuntimeException.class)
    public void deserializeTransactionSet_nonEmpty_withTxHash_fails() throws Exception {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        BtcTransaction input = new BtcTransaction(params);
        input.addOutput(Coin.FIFTY_COINS, Address.fromBase58(params, "mvc8mwDcdLEq2jGqrL43Ub3sxTR13tB8LL"));

        BtcTransaction t1 = new BtcTransaction(params);
        t1.addInput(input.getOutput(0));
        t1.addOutput(Coin.COIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));

        Set<ReleaseTransactionSet.Entry> expectedEntries = new HashSet<>(Arrays.asList(
                new ReleaseTransactionSet.Entry(t1, 32L, PegTestUtils.createHash3(0))
        ));

        ReleaseTransactionSet rtc = new ReleaseTransactionSet(expectedEntries);
        byte[] serializedEntries = BridgeSerializationUtils.serializeReleaseTransactionSetWithTxHash(rtc);

        BridgeSerializationUtils.deserializeReleaseTransactionSet(serializedEntries, params);

    }

    @Test(expected = RuntimeException.class)
    public void deserializeTransactionSet_nonEmpty_withoutTxHash_fails() throws Exception {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        BtcTransaction input = new BtcTransaction(params);
        input.addOutput(Coin.FIFTY_COINS, Address.fromBase58(params, "mvc8mwDcdLEq2jGqrL43Ub3sxTR13tB8LL"));

        BtcTransaction t1 = new BtcTransaction(params);
        t1.addInput(input.getOutput(0));
        t1.addOutput(Coin.COIN, Address.fromBase58(params, "n3CaAPu2PR7FDdGK8tFwe8thr7hV7zz599"));

        Set<ReleaseTransactionSet.Entry> expectedEntries = new HashSet<>(Arrays.asList(
                new ReleaseTransactionSet.Entry(t1, 32L)
        ));

        ReleaseTransactionSet rtc = new ReleaseTransactionSet(expectedEntries);
        byte[] serializedEntries = BridgeSerializationUtils.serializeReleaseTransactionSet(rtc);

        BridgeSerializationUtils.deserializeReleaseTransactionSet(serializedEntries, params, true);
    }

    @Test
    public void deserializeTransactionSet_nonEmptyOddSize() throws Exception {
        byte[] firstItem = RLP.encodeElement(Hex.decode("010203"));
        byte[] data = RLP.encodeList(firstItem);

        try {
            BridgeSerializationUtils.deserializeReleaseTransactionSet(data, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        } catch (RuntimeException e) {
            return;
        }
        Assert.fail();
    }

    @Test
    public void serializeDeserializeCoin() {
        byte[] serialized1 = BridgeSerializationUtils.serializeCoin(Coin.COIN);
        Assert.assertThat(BridgeSerializationUtils.deserializeCoin(serialized1),
                is(Coin.COIN));
        byte[] serialized2 = BridgeSerializationUtils.serializeCoin(Coin.valueOf(Long.MAX_VALUE));
        Assert.assertThat(BridgeSerializationUtils.deserializeCoin(serialized2),
                is(Coin.valueOf(Long.MAX_VALUE)));
        byte[] serialized3 = BridgeSerializationUtils.serializeCoin(Coin.ZERO);
        Assert.assertThat(BridgeSerializationUtils.deserializeCoin(serialized3),
                is(Coin.ZERO));
        Assert.assertThat(BridgeSerializationUtils.deserializeCoin(null),
                nullValue());
        Assert.assertThat(BridgeSerializationUtils.deserializeCoin(new byte[0]),
                nullValue());
    }

    @Test
    public void serializeInteger() {
        Assert.assertEquals(BigInteger.valueOf(123), RLP.decodeBigInteger(BridgeSerializationUtils.serializeInteger(123), 0));
        Assert.assertEquals(BigInteger.valueOf(1200), RLP.decodeBigInteger(BridgeSerializationUtils.serializeInteger(1200), 0));
    }

    @Test
    public void deserializeInteger() {
        Assert.assertEquals(123, BridgeSerializationUtils.deserializeInteger(RLP.encodeBigInteger(BigInteger.valueOf(123))).intValue());
        Assert.assertEquals(1200, BridgeSerializationUtils.deserializeInteger(RLP.encodeBigInteger(BigInteger.valueOf(1200))).intValue());
    }

    @Test
    public void serializeSha256Hash() {
        Sha256Hash originalHash = PegTestUtils.createHash(2);
        byte[] encodedHash = RLP.encodeElement(originalHash.getBytes());

        byte[] result = BridgeSerializationUtils.serializeSha256Hash(originalHash);

        Assert.assertArrayEquals(encodedHash, result);
    }

    @Test
    public void deserializeSha256Hash() {
        Sha256Hash originalHash = PegTestUtils.createHash(2);
        byte[] encodedHash = RLP.encodeElement(originalHash.getBytes());

        Sha256Hash result = BridgeSerializationUtils.deserializeSha256Hash(encodedHash);
        Assert.assertEquals(originalHash, result);
    }

    @Test
    public void deserializeSha256Hash_nullValue() {
        Sha256Hash result = BridgeSerializationUtils.deserializeSha256Hash(null);
        Assert.assertNull(result);
    }

    @Test
    public void deserializeSha256Hash_hashWithLeadingZero() {
        Sha256Hash originalHash = PegTestUtils.createHash(0);
        byte[] encodedHash = RLP.encodeElement(originalHash.getBytes());

        Sha256Hash result = BridgeSerializationUtils.deserializeSha256Hash(encodedHash);
        Assert.assertEquals(originalHash, result);
    }

    @Test
    public void serializeScript() {
        Script expectedScript = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey()));

        byte[] actualData = BridgeSerializationUtils.serializeScript(expectedScript);

        Assert.assertEquals(expectedScript, new Script(((RLPList) RLP.decode2(actualData).get(0)).get(0).getRLPData()));
    }

    @Test
    public void deserializeScript() {
        Script expectedScript = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey()));
        byte[] data = RLP.encodeList(RLP.encodeElement(expectedScript.getProgram()));

        Script actualScript = BridgeSerializationUtils.deserializeScript(data);

        Assert.assertEquals(expectedScript, actualScript);
    }

    @Test
    public void deserializeFlyoverFederationInformation_no_data() {
        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            new byte[]{},
            new byte[]{}
        );

        Assert.assertNull(result);
    }

    @Test
    public void deserializeFlyoverFederationInformation_null_data() {
        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            null,
            null
        );

        Assert.assertNull(result);
    }

    @Test(expected = RuntimeException.class)
    public void deserializeFlyoverFederationInformation_one_data() {
        byte[][] rlpElements = new byte[1][];
        rlpElements[0] = RLP.encodeElement(new byte[]{(byte)0x11});

        BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            RLP.encodeList(rlpElements),
            new byte[]{(byte)0x23}
        );
    }

    @Test
    public void deserializeFlyoverFederationInformation_ok() {
        byte[][] rlpElements = new byte[2][];
        rlpElements[0] = RLP.encodeElement(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000002").getBytes());
        rlpElements[1] = RLP.encodeElement(new byte[]{(byte)0x22});

        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            RLP.encodeList(rlpElements),
            new byte[]{(byte)0x23}
        );

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(
            Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000002").getBytes(),
            result.getDerivationHash().getBytes()
        );
        Assert.assertArrayEquals(new byte[]{(byte)0x22}, result.getFederationRedeemScriptHash());
        Assert.assertArrayEquals(new byte[]{(byte)0x23}, result.getFlyoverFederationRedeemScriptHash());
    }

    @Test
    public void serializeFlyoverFederationInformation_no_data() {
        byte[] result = BridgeSerializationUtils.serializeFlyoverFederationInformation(null);

        Assert.assertEquals(0, result.length);
    }

    @Test
    public void serializeFlyoverFederationInformation_Ok() {
        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte)0x23};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            PegTestUtils.createHash3(2),
            new byte[]{(byte)0x22},
            flyoverFederationRedeemScriptHash
        );

        FlyoverFederationInformation result = BridgeSerializationUtils.deserializeFlyoverFederationInformation(
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation),
            flyoverFederationRedeemScriptHash
        );

        Assert.assertArrayEquals(
            flyoverFederationInformation.getDerivationHash().getBytes(),
            result.getDerivationHash().getBytes()
        );
        Assert.assertArrayEquals(
            flyoverFederationInformation.getFederationRedeemScriptHash(),
            result.getFederationRedeemScriptHash()
        );
        Assert.assertArrayEquals(
            flyoverFederationInformation.getFlyoverFederationRedeemScriptHash(),
            result.getFlyoverFederationRedeemScriptHash()
        );
    }

    @Test
    public void deserializeCoinbaseInformation_dataIsNull_returnsNull() {
        Assert.assertNull(BridgeSerializationUtils.deserializeCoinbaseInformation(null));
    }

    @Test
    public void deserializeCoinbaseInformation_dataContainsInvalidList_throwsRuntimeException() {
        byte[] firstItem = RLP.encodeElement(Hex.decode("010101"));
        byte[] secondItem = RLP.encodeElement(Hex.decode("010102"));
        byte[] thirdItem = RLP.encodeElement(Hex.decode("010103"));
        byte[] data = RLP.encodeList(firstItem, secondItem, thirdItem);

        try {
            BridgeSerializationUtils.deserializeCoinbaseInformation(data);
            Assert.fail("Runtime exception should be thrown!");
        } catch (RuntimeException e) {
            Assert.assertEquals("Invalid serialized coinbase information, expected 1 value but got 3", e.getMessage());
        }
    }

    @Test
    public void deserializeCoinbaseInformation_dataIsValid_returnsValidCoinbaseInformation() {
        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        Sha256Hash witnessRoot = MerkleTreeUtils.combineLeftRight(Sha256Hash.ZERO_HASH, secondHashTx);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessRoot);
        byte[] serializedCoinbaseInformation = BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation);

        Assert.assertEquals(witnessRoot, BridgeSerializationUtils.deserializeCoinbaseInformation(serializedCoinbaseInformation).getWitnessMerkleRoot());
    }

    private void testSerializeAndDeserializeFederation(boolean isRskip284Active, String networkId) {
        final int NUM_CASES = 20;

        BridgeConstants bridgeConstants;
        if (networkId.equals(NetworkParameters.ID_MAINNET)) {
            bridgeConstants = BridgeMainNetConstants.getInstance();
        } else {
            bridgeConstants = BridgeTestNetConstants.getInstance();
        }

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);

        for (int i = 0; i < NUM_CASES; i++) {
            int numMembers = randomInRange(2, 14);
            List<FederationMember> members = new ArrayList<>();

            for (int j = 0; j < numMembers; j++) {
                members.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
            }

            Federation testFederation = new Federation(
                members,
                Instant.now(),
                123,
                bridgeConstants.getBtcParams()
            );
            byte[] serializedTestFederation = BridgeSerializationUtils.serializeFederation(testFederation);

            Federation deserializedTestFederation = BridgeSerializationUtils.deserializeFederation(
                serializedTestFederation,
                bridgeConstants.getBtcParams()
            );

            Federation testErpFederation = new ErpFederation(
                members,
                Instant.now(),
                123,
                bridgeConstants.getBtcParams(),
                bridgeConstants.getErpFedPubKeysList(),
                bridgeConstants.getErpFedActivationDelay(),
                activations
            );
            byte[] serializedTestErpFederation = BridgeSerializationUtils.serializeFederation(testErpFederation);

            Federation deserializedTestErpFederation = BridgeSerializationUtils.deserializeErpFederation(
                serializedTestErpFederation,
                bridgeConstants,
                activations
            );

            Assert.assertEquals(testFederation, deserializedTestFederation);
            Assert.assertEquals(testErpFederation, deserializedTestErpFederation);
            Assert.assertNotEquals(testFederation, deserializedTestErpFederation);
            Assert.assertNotEquals(testErpFederation, deserializedTestFederation);

            if (!isRskip284Active && networkId.equals(NetworkParameters.ID_TESTNET)) {
                Assert.assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, testErpFederation.getRedeemScript());
            }
        }
    }

    private Address mockAddressHash160(String hash160) {
        Address result = mock(Address.class);
        when(result.getHash160()).thenReturn(Hex.decode(hash160));
        return result;
    }

    private BtcTransaction mockBtcTransactionSerialize(String serialized) {
        BtcTransaction result = mock(BtcTransaction.class);
        when(result.bitcoinSerialize()).thenReturn(Hex.decode(serialized));
        return result;
    }

    private String charNTimes(char c, int n) {
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            sb.append(c);
        }

        return sb.toString();
    }

    private RskAddress createAddress(String addr) {
        String address;

        if (addr.length() < RskAddress.LENGTH_IN_BYTES * 2) {
            address = addr + charNTimes('0', RskAddress.LENGTH_IN_BYTES * 2 - addr.length());
        }
        else {
            address = addr;
        }

        return new RskAddress(Hex.decode(address));
    }

    private int randomInRange(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

    private static AddressBasedAuthorizer getTestingAddressBasedAuthorizer() {
        return new AddressBasedAuthorizer(Collections.EMPTY_LIST, null) {
            public boolean isAuthorized(RskAddress addess) {
                return true;
            }
        };
    }
}
