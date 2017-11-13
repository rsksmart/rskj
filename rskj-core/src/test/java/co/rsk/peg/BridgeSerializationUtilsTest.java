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
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import javassist.runtime.Inner;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class BridgeSerializationUtilsTest {
    @PrepareForTest({ RLP.class })
    @Test
    public void serializeMapOfHashesToLong() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeElement();
        mock_RLP_encodeBigInteger();
        mock_RLP_encodeList();

        Map<Sha256Hash, Long> sample = new HashMap<>();
        sample.put(Sha256Hash.wrap(charNTimes('b', 64)), 1L);
        sample.put(Sha256Hash.wrap(charNTimes('d', 64)), 2L);
        sample.put(Sha256Hash.wrap(charNTimes('a', 64)), 3L);
        sample.put(Sha256Hash.wrap(charNTimes('c', 64)), 4L);

        byte[] result = BridgeSerializationUtils.serializeMapOfHashesToLong(sample);
        String hexResult = Hex.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        char[] sorted = new char[]{'a','b','c','d'};
        for (char c : sorted) {
            String key = charNTimes(c, 64);
            expectedBuilder.append("dd");
            expectedBuilder.append(key);
            expectedBuilder.append("ff");
            expectedBuilder.append(Hex.toHexString(BigInteger.valueOf(sample.get(Sha256Hash.wrap(key))).toByteArray()));
        }
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void desserializeMapOfHashesToLong_emptyOrNull() throws Exception {
        assertEquals(BridgeSerializationUtils.deserializeMapOfHashesToLong(null), new HashMap<>());
        assertEquals(BridgeSerializationUtils.deserializeMapOfHashesToLong(new byte[]{}), new HashMap<>());
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializeMapOfHashesToLong_nonEmpty() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2_forMapOfHashesToLong();

        byte[] sample = new byte[]{(byte)'b', 7, (byte)'d', 76, (byte) 'a', 123};
        Map<Sha256Hash, Long> result = BridgeSerializationUtils.deserializeMapOfHashesToLong(sample);
        assertEquals(3, result.size());
        assertEquals(7L, result.get(Sha256Hash.wrap(charNTimes('b', 64))).longValue());
        assertEquals(76L, result.get(Sha256Hash.wrap(charNTimes('d', 64))).longValue());
        assertEquals(123L, result.get(Sha256Hash.wrap(charNTimes('a', 64))).longValue());
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializeMapOfHashesToLong_nonEmptyOddSize() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2_forMapOfHashesToLong();

        boolean thrown = false;
        try {
            byte[] sample = new byte[]{(byte)'b', 7, (byte)'d', 76, (byte) 'a'};
            BridgeSerializationUtils.deserializeMapOfHashesToLong(sample);
        } catch (RuntimeException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void serializeFederation() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeBigInteger();
        mock_RLP_encodeList();
        mock_RLP_encodeElement();

        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        Federation federation = new Federation(
            Arrays.asList(new BtcECKey[]{
                    BtcECKey.fromPublicOnly(publicKeyBytes[0]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[1]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[2]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[3]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[4]),
                    BtcECKey.fromPublicOnly(publicKeyBytes[5]),
            }),
            Instant.ofEpochMilli(0xabcdef), //
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        byte[] result = BridgeSerializationUtils.serializeFederation(federation);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("ff00abcdef"); // Creation time
        federation.getPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("dd");
            expectedBuilder.append(Hex.toHexString(key.getPubKey()));
        });
        byte[] expected = Hex.decode(expectedBuilder.toString());
        Assert.assertTrue(Arrays.equals(expected, result));
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializeFederation_ok() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.LAST_ELEMENT);

        byte[][] publicKeyBytes = Arrays.asList(100, 200, 300, 400, 500, 600).stream()
            .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .map(k -> k.getPubKey())
            .toArray(byte[][]::new);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("02"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("cd"); // Length of second element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("06212121212121"); // Second element (inner list, public keys). 6 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        Federation deserializedFederation = BridgeSerializationUtils.deserializeFederation(sample, new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

        Assert.assertEquals(5000, deserializedFederation.getCreationTime().toEpochMilli());
        Assert.assertEquals(4, deserializedFederation.getNumberOfSignaturesRequired());
        Assert.assertEquals(6, deserializedFederation.getPublicKeys().size());
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeyBytes[i], deserializedFederation.getPublicKeys().get(i).getPubKey()));
        }
        Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), deserializedFederation.getBtcParams());
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializeFederation_wrongListSize() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("03"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("04"); // Length of third element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("03"); // Second element (# of signatures required - 3)
        sampleBuilder.append("aabbccdd"); // Third element
        byte[] sample = Hex.decode(sampleBuilder.toString());

        boolean thrown = false;
        try {
            BridgeSerializationUtils.deserializeFederation(sample, new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Expected 2 elements"));
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void serializePendingFederation() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeList();
        mock_RLP_encodeElement();

        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        PendingFederation pendingFederation = new PendingFederation(
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPublicOnly(publicKeyBytes[0]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[1]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[2]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[3]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[4]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[5]),
                })
        );

        byte[] result = BridgeSerializationUtils.serializePendingFederation(pendingFederation);
        StringBuilder expectedBuilder = new StringBuilder();
        pendingFederation.getPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("dd");
            expectedBuilder.append(Hex.toHexString(key.getPubKey()));
        });
        byte[] expected = Hex.decode(expectedBuilder.toString());
        Assert.assertTrue(Arrays.equals(expected, result));
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializePendingFederation_ok() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

        byte[][] publicKeyBytes = Arrays.asList(100, 200, 300, 400, 500, 600).stream()
                .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(k -> k.getPubKey())
                .toArray(byte[][]::new);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("06212121212121"); // 6 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        PendingFederation deserializedPendingFederation = BridgeSerializationUtils.deserializePendingFederation(sample);

        Assert.assertEquals(6, deserializedPendingFederation.getPublicKeys().size());
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeyBytes[i], deserializedPendingFederation.getPublicKeys().get(i).getPubKey()));
        }
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void serializeElection() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeElement();
        mock_RLP_encodeList();

        ABICallAuthorizer mockedAuthorizer = mock(ABICallAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(ABICallVoter.class))).thenReturn(true);

        Map<ABICallSpec, List<ABICallVoter>> sampleVotes = new HashMap<>();
        sampleVotes.put(
                new ABICallSpec("one-function", new byte[][]{}),
                Arrays.asList(new ABICallVoter(Hex.decode("8899")), new ABICallVoter(Hex.decode("aabb")))
        );
        sampleVotes.put(
                new ABICallSpec("another-function", new byte[][]{ Hex.decode("01"), Hex.decode("0203") }),
                Arrays.asList(new ABICallVoter(Hex.decode("ccdd")), new ABICallVoter(Hex.decode("eeff")), new ABICallVoter(Hex.decode("0011")))
        );
        sampleVotes.put(
                new ABICallSpec("yet-another-function", new byte[][]{ Hex.decode("0405") }),
                Arrays.asList(new ABICallVoter(Hex.decode("fa")), new ABICallVoter(Hex.decode("ca")))
        );

        ABICallElection sample = new ABICallElection(mockedAuthorizer, sampleVotes);

        byte[] result = BridgeSerializationUtils.serializeElection(sample);
        String hexResult = Hex.toHexString(result);

        StringBuilder expectedBuilder = new StringBuilder();

        expectedBuilder.append("dd");
        expectedBuilder.append(Hex.toHexString("another-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("dd01dd0203");
        expectedBuilder.append("dd0011ddccddddeeff");

        expectedBuilder.append("dd");
        expectedBuilder.append(Hex.toHexString("one-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("dd8899ddaabb");

        expectedBuilder.append("dd");
        expectedBuilder.append(Hex.toHexString("yet-another-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("dd0405");
        expectedBuilder.append("ddcaddfa");

        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void deserializeElection_emptyOrNull() throws Exception {
        ABICallAuthorizer mockAuthorizer = mock(ABICallAuthorizer.class);
        ABICallElection election;
        election = BridgeSerializationUtils.deserializeElection(null, mockAuthorizer);
        Assert.assertEquals(0, election.getVotes().size());
        election = BridgeSerializationUtils.deserializeElection(new byte[]{}, mockAuthorizer);
        Assert.assertEquals(0, election.getVotes().size());
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void deserializeElection_nonEmpty() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.STARTING_WITH_FF_RECURSIVE);

        ABICallAuthorizer mockedAuthorizer = mock(ABICallAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(ABICallVoter.class))).thenReturn(true);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("06"); // Total of three specs, two entries for each
        sampleBuilder.append("0a");
        sampleBuilder.append("07");
        sampleBuilder.append("17");
        sampleBuilder.append("07");
        sampleBuilder.append("14");
        sampleBuilder.append("0c");

        // First spec + votes
        sampleBuilder.append("020502");
        sampleBuilder.append(Hex.toHexString("funct".getBytes(StandardCharsets.UTF_8)));
        sampleBuilder.append("ff00");

        sampleBuilder.append("020103");
        sampleBuilder.append("aabbccdd");

        // Second spec + votes
        sampleBuilder.append("020b09");
        sampleBuilder.append(Hex.toHexString("other-funct".getBytes(StandardCharsets.UTF_8)));
        sampleBuilder.append("ff020203");
        sampleBuilder.append("1122");
        sampleBuilder.append("334455");

        sampleBuilder.append("03010101");
        sampleBuilder.append("556677");

        // Third spec + votes
        sampleBuilder.append("020c05");
        sampleBuilder.append(Hex.toHexString("random-funct".getBytes(StandardCharsets.UTF_8)));
        sampleBuilder.append("ff0102");
        sampleBuilder.append("aabb");

        sampleBuilder.append("0402020201");
        sampleBuilder.append("11113333555577");

        byte[] sample = Hex.decode(sampleBuilder.toString());

        ABICallElection election = BridgeSerializationUtils.deserializeElection(sample, mockedAuthorizer);

        Assert.assertEquals(3, election.getVotes().size());
        List<ABICallVoter> voters;
        ABICallSpec spec;

        spec = new ABICallSpec("funct", new byte[][]{});
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                new ABICallVoter(Hex.decode("aa")),
                new ABICallVoter(Hex.decode("bbccdd"))
        );
        Assert.assertEquals(voters, election.getVotes().get(spec));

        spec = new ABICallSpec("other-funct", new byte[][]{
                Hex.decode("1122"),
                Hex.decode("334455")
        });
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                new ABICallVoter(Hex.decode("55")),
                new ABICallVoter(Hex.decode("66")),
                new ABICallVoter(Hex.decode("77"))
        );
        Assert.assertEquals(voters, election.getVotes().get(spec));

        spec = new ABICallSpec("random-funct", new byte[][]{
                Hex.decode("aabb")
        });
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                new ABICallVoter(Hex.decode("1111")),
                new ABICallVoter(Hex.decode("3333")),
                new ABICallVoter(Hex.decode("5555")),
                new ABICallVoter(Hex.decode("77"))
        );
        Assert.assertEquals(voters, election.getVotes().get(spec));
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void deserializeElection_unevenOuterList() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.STARTING_WITH_FF_RECURSIVE);

        ABICallAuthorizer mockedAuthorizer = mock(ABICallAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(ABICallVoter.class))).thenReturn(true);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("05"); // Five elements, uneven
        sampleBuilder.append("0101010101");
        sampleBuilder.append("1122334455");

        byte[] sample = Hex.decode(sampleBuilder.toString());

        try {
            BridgeSerializationUtils.deserializeElection(sample, mockedAuthorizer);
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("expected an even number of entries, but odd given"));
            return;
        }

        Assert.fail();
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void deserializeElection_invalidCallSpec() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.STARTING_WITH_FF_RECURSIVE);

        ABICallAuthorizer mockedAuthorizer = mock(ABICallAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(ABICallVoter.class))).thenReturn(true);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("02");
        sampleBuilder.append("07");
        sampleBuilder.append("01");

        sampleBuilder.append("03010101aabbcc"); // Invalid call spec, should have exactly two elements
        sampleBuilder.append("aa"); // Doesn't matter

        byte[] sample = Hex.decode(sampleBuilder.toString());

        try {
            BridgeSerializationUtils.deserializeElection(sample, mockedAuthorizer);
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized ABICallSpec"));
            return;
        }

        Assert.fail();
    }

    private void mock_RLP_encodeElement() {
        // Identity prepending byte '0xdd'
        when(RLP.encodeElement(any(byte[].class))).then((InvocationOnMock invocation) -> {
            byte[] arg = invocation.getArgumentAt(0, byte[].class);
            byte[] result = new byte[arg.length+1];
            result[0] = (byte) 0xdd;
            for (int i = 0; i < arg.length; i++)
                result[i+1] = arg[i];
            return result;
        });
    }

    private void mock_RLP_encodeBigInteger() {
        // To byte array prepending byte '0xff'
        when(RLP.encodeBigInteger(any(BigInteger.class))).then((InvocationOnMock invocation) -> {
            byte[] arg = (invocation.getArgumentAt(0, BigInteger.class)).toByteArray();
            byte[] result = new byte[arg.length+1];
            result[0] = (byte) 0xff;
            for (int i = 0; i < arg.length; i++)
                result[i+1] = arg[i];
            return result;
        });
    }

    private void mock_RLP_encodeList() {
        // To flat byte array
        when(RLP.encodeList(anyVararg())).then((InvocationOnMock invocation) -> {
            Object[] args = invocation.getArguments();
            byte[][] bytes = new byte[args.length][];
            for (int i = 0; i < args.length; i++)
                bytes[i] = (byte[])args[i];
            return flatten(bytes);
        });
    }

    private void mock_RLP_decode2_forMapOfHashesToLong() {
        // Plain list with first elements being the size
        // Sizes are 1 byte long
        // e.g., for list [a,b,c] and a.size = 5, b.size = 7, c.size = 4, then:
        // 03050704[a bytes][b bytes][c bytes]
        when(RLP.decode2(any(byte[].class))).then((InvocationOnMock invocation) -> {
            RLPList result = new RLPList();
            byte[] arg = invocation.getArgumentAt(0, byte[].class);
            // Even byte -> hash of 64 bytes with same char from byte
            // Odd byte -> long from byte
            for (int i = 0; i < arg.length; i++) {
                byte[] element;
                if (i%2 == 0) {
                    element = Hex.decode(charNTimes((char) arg[i], 64));
                } else {
                    element = new byte[]{arg[i]};
                }
                result.add(() -> element);
            }
            return new ArrayList<>(Arrays.asList(result));
        });
    }

    private enum InnerListMode { NONE, LAST_ELEMENT, STARTING_WITH_FF_RECURSIVE };

    private void mock_RLP_decode2(InnerListMode mode) {
        when(RLP.decode2(any(byte[].class))).then((InvocationOnMock invocation) -> {
            byte[] bytes = invocation.getArgumentAt(0, byte[].class);
            return new ArrayList<>(Arrays.asList(decodeTwoMock(bytes, mode)));
        });
    }

    private RLPList decodeTwoMock(byte[] bytes, InnerListMode mode) {
        RLPList list = decodeList(bytes);
        if (mode == InnerListMode.LAST_ELEMENT) {
            byte[] lastElementBytes = list.get(list.size() - 1).getRLPData();
            RLPList innerList = decodeList(lastElementBytes);
            list.set(list.size() - 1, innerList);
        } else if (mode == InnerListMode.STARTING_WITH_FF_RECURSIVE) {
            for (int i = 0; i < list.size(); i++) {
                byte[] elementBytes = list.get(i).getRLPData();
                if (elementBytes.length > 0 && elementBytes[0] == -1) {
                    RLPList innerList = decodeTwoMock(Arrays.copyOfRange(elementBytes, 1, elementBytes.length), mode);
                    list.set(i, innerList);
                }
            }
        }
        return list;
    }

    private RLPList decodeList(byte[] bytes) {
        // First byte => length of list (n)
        // Subsequent n bytes => length of each of the n elements
        // Subsequent bytes => elements
        RLPList decoded = new RLPList();
        int size = Byte.toUnsignedInt(bytes[0]);
        int offset = size+1;
        for (int i = 1; i <= size; i++) {
            int elementSize = Byte.toUnsignedInt(bytes[i]);
            byte[] element = Arrays.copyOfRange(bytes, offset, offset+elementSize);
            decoded.add(() -> element);
            offset += elementSize;
        }
        return decoded;
    }

    private String charNTimes(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++)
            sb.append(c);
        return sb.toString();
    }

    private byte[] flatten(byte[][] bytes) {
        int totalLength = 0;
        for (int i = 0; i < bytes.length; i++)
            totalLength += bytes[i].length;

        byte[] result = new byte[totalLength];
        int offset=0;
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < bytes[i].length; j++)
                result[offset+j] = bytes[i][j];
            offset += bytes[i].length;
        }
        return result;
    }
}
