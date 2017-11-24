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
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.spongycastle.util.encoders.Hex;
import sun.nio.ch.Net;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;

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
            3,
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
        expectedBuilder.append("ff03"); // Number of sinatures required
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
        mock_RLP_decode2_forFederation();

        byte[][] publicKeyBytes = Arrays.asList(100, 200, 300, 400, 500, 600).stream()
            .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .map(k -> k.getPubKey())
            .toArray(byte[][]::new);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("03"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("03"); // Second element (# of signatures required - 3)
        sampleBuilder.append("06212121212121"); // Third element (inner list, public keys). 6 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        Federation deserializedFederation = BridgeSerializationUtils.deserializeFederation(sample, new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

        Assert.assertEquals(5000, deserializedFederation.getCreationTime().toEpochMilli());
        Assert.assertEquals(3, deserializedFederation.getNumberOfSignaturesRequired());
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
        mock_RLP_decode2_forFederation();

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("02"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("03"); // Second element (# of signatures required - 3)
        byte[] sample = Hex.decode(sampleBuilder.toString());

        boolean thrown = false;
        try {
            BridgeSerializationUtils.deserializeFederation(sample, new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Expected 3 elements"));
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializeFederation_wrongNumberOfSignatures() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2_forFederation();

        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("03"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("00"); // Second element (# of signatures required -> zero, WRONG value, should throw exception)
        sampleBuilder.append("06212121212121"); // Third element (inner list, public keys). 6 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        boolean thrown = false;
        try {
            BridgeSerializationUtils.deserializeFederation(sample, new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized Federation # of signatures required"));
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializeFederation_wrongNumberOfPublicKeys() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2_forFederation();

        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
        };

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("03"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("03"); // Second element (# of signatures required -> 3)
        sampleBuilder.append("022121"); // Third element (inner list, public keys). 2 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        boolean thrown = false;
        try {
            BridgeSerializationUtils.deserializeFederation(sample, new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized Federation # of public keys"));
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void serializePendingFederation() throws Exception {
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

        PendingFederation pendingFederation = new PendingFederation(
                12,
                3,
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
        expectedBuilder.append("ff0c"); // Id
        expectedBuilder.append("ff03"); // Number of sinatures required
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
        mock_RLP_decode2_forFederation();

        byte[][] publicKeyBytes = Arrays.asList(100, 200, 300, 400, 500, 600).stream()
                .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(k -> k.getPubKey())
                .toArray(byte[][]::new);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("03"); // Length of outer list
        sampleBuilder.append("01"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("0a"); // First element (id -> 10)
        sampleBuilder.append("03"); // Second element (# of signatures required - 3)
        sampleBuilder.append("06212121212121"); // Third element (inner list, public keys). 6 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        PendingFederation deserializedPendingFederation = BridgeSerializationUtils.deserializePendingFederation(sample);

        Assert.assertEquals(10, deserializedPendingFederation.getId());
        Assert.assertEquals(3, deserializedPendingFederation.getNumberOfSignaturesRequired());
        Assert.assertEquals(6, deserializedPendingFederation.getPublicKeys().size());
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeyBytes[i], deserializedPendingFederation.getPublicKeys().get(i).getPubKey()));
        }
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializePendingFederation_wrongListSize() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2_forFederation();

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("02"); // Length of outer list
        sampleBuilder.append("01"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("0b"); // First element (id -> 11)
        sampleBuilder.append("03"); // Second element (# of signatures required - 3)
        byte[] sample = Hex.decode(sampleBuilder.toString());

        boolean thrown = false;
        try {
            BridgeSerializationUtils.deserializePendingFederation(sample);
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Expected 3 elements"));
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void desserializePendingFederation_wrongNumberOfSignatures() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2_forFederation();

        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("03"); // Length of outer list
        sampleBuilder.append("01"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("0d"); // First element (id -> 13)
        sampleBuilder.append("00"); // Second element (# of signatures required -> zero, WRONG value, should throw exception)
        sampleBuilder.append("06212121212121"); // Third element (inner list, public keys). 6 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        boolean thrown = false;
        try {
            BridgeSerializationUtils.deserializePendingFederation(sample);
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid serialized PendingFederation # of signatures required"));
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    private void mock_RLP_encodeElement() {
        // Identity prepending byte '0xdd'
        PowerMockito.when(RLP.encodeElement(any(byte[].class))).then((InvocationOnMock invocation) -> {
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
        PowerMockito.when(RLP.encodeBigInteger(any(BigInteger.class))).then((InvocationOnMock invocation) -> {
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
        PowerMockito.when(RLP.encodeList(anyVararg())).then((InvocationOnMock invocation) -> {
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
        PowerMockito.when(RLP.decode2(any(byte[].class))).then((InvocationOnMock invocation) -> {
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

    private void mock_RLP_decode2_forFederation() {
        PowerMockito.when(RLP.decode2(any(byte[].class))).then((InvocationOnMock invocation) -> {
            byte[] bytes = invocation.getArgumentAt(0, byte[].class);
            // Two decodes: "outer" list and "inner" list (last element of the "outer" list)
            RLPList outerList = decodeListForFederation(bytes);
            if (outerList.size() > 2) {
                byte[] lastElementBytes = outerList.get(outerList.size() - 1).getRLPData();
                RLPList innerList = decodeListForFederation(lastElementBytes);
                outerList.set(outerList.size() - 1, innerList);
            }
            return new ArrayList<>(Arrays.asList(outerList));
        });
    }

    private RLPList decodeListForFederation(byte[] bytes) {
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
