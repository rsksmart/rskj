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
import co.rsk.core.RskAddress;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import com.google.common.primitives.UnsignedBytes;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ RLP.class, BridgeSerializationUtils.class, RskAddress.class })
public class BridgeSerializationUtilsTest {
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
            42L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        byte[] result = BridgeSerializationUtils.serializeFederation(federation);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("ff00abcdef"); // Creation time
        expectedBuilder.append("ff2a"); // Creation block number
        federation.getPublicKeys().stream().sorted(BtcECKey.PUBKEY_COMPARATOR).forEach(key -> {
            expectedBuilder.append("dd");
            expectedBuilder.append(Hex.toHexString(key.getPubKey()));
        });
        byte[] expected = Hex.decode(expectedBuilder.toString());
        Assert.assertTrue(Arrays.equals(expected, result));
    }

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
        sampleBuilder.append("03"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("02"); // Length of second element
        sampleBuilder.append("cd"); // Length of third element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("002a"); // Second element block number 42
        sampleBuilder.append("06212121212121"); // third element (inner list, public keys). 6 elements of 33 bytes (0x21 bytes) each.
        for (int i = 0; i < publicKeyBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(publicKeyBytes[i]));
        }
        byte[] sample = Hex.decode(sampleBuilder.toString());

        Federation deserializedFederation = BridgeSerializationUtils.deserializeFederation(sample, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        Assert.assertEquals(5000, deserializedFederation.getCreationTime().toEpochMilli());
        Assert.assertEquals(4, deserializedFederation.getNumberOfSignaturesRequired());
        Assert.assertEquals(6, deserializedFederation.getPublicKeys().size());
        Assert.assertThat(deserializedFederation.getCreationBlockNumber(), is(42L));
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeyBytes[i], deserializedFederation.getPublicKeys().get(i).getPubKey()));
        }
        Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), deserializedFederation.getBtcParams());
    }

    @Test
    public void desserializeFederation_wrongListSize() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("04"); // Length of outer list
        sampleBuilder.append("02"); // Length of first element
        sampleBuilder.append("01"); // Length of second element
        sampleBuilder.append("01"); // Length of third element
        sampleBuilder.append("04"); // Length of fourth element
        sampleBuilder.append("1388"); // First element (creation date -> 5000 milliseconds from epoch)
        sampleBuilder.append("03"); // Second element (# of signatures required - 3)
        sampleBuilder.append("03"); // Third element
        sampleBuilder.append("aabbccdd"); // Fourth element
        byte[] sample = Hex.decode(sampleBuilder.toString());

        boolean thrown = false;
        try {
            BridgeSerializationUtils.deserializeFederation(sample, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Expected 3 elements"));
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

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

    @Test
    public void deserializePendingFederation() throws Exception {
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

    @Test
    public void serializeElection() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeElement();
        mock_RLP_encodeList();

        AddressBasedAuthorizer mockedAuthorizer = mock(AddressBasedAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(RskAddress.class))).thenReturn(true);

        Map<ABICallSpec, List<RskAddress>> sampleVotes = new HashMap<>();
        sampleVotes.put(
                new ABICallSpec("one-function", new byte[][]{}),
                Arrays.asList(mockAddress("8899"), mockAddress("aabb"))
        );
        sampleVotes.put(
                new ABICallSpec("another-function", new byte[][]{ Hex.decode("01"), Hex.decode("0203") }),
                Arrays.asList(mockAddress("ccdd"), mockAddress("eeff"), mockAddress("0011"))
        );
        sampleVotes.put(
                new ABICallSpec("yet-another-function", new byte[][]{ Hex.decode("0405") }),
                Arrays.asList(mockAddress("fa"), mockAddress("ca"))
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
        AddressBasedAuthorizer mockAuthorizer = mock(AddressBasedAuthorizer.class);
        ABICallElection election;
        election = BridgeSerializationUtils.deserializeElection(null, mockAuthorizer);
        Assert.assertEquals(0, election.getVotes().size());
        election = BridgeSerializationUtils.deserializeElection(new byte[]{}, mockAuthorizer);
        Assert.assertEquals(0, election.getVotes().size());
    }

    @Test
    public void deserializeElection_nonEmpty() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.STARTING_WITH_FF_RECURSIVE);

        mockAddressInstantiation("aa");
        mockAddressInstantiation("bbccdd");
        mockAddressInstantiation("55");
        mockAddressInstantiation("66");
        mockAddressInstantiation("77");
        mockAddressInstantiation("1111");
        mockAddressInstantiation("3333");
        mockAddressInstantiation("5555");

        AddressBasedAuthorizer mockedAuthorizer = mock(AddressBasedAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(RskAddress.class))).thenReturn(true);

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
        List<RskAddress> voters;
        ABICallSpec spec;

        spec = new ABICallSpec("funct", new byte[][]{});
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                mockAddress("aa"),
                mockAddress("bbccdd")
        );

        Assert.assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        Assert.assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());

        spec = new ABICallSpec("other-funct", new byte[][]{
                Hex.decode("1122"),
                Hex.decode("334455")
        });
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                mockAddress("55"),
                mockAddress("66"),
                mockAddress("77")
        );

        Assert.assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        Assert.assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());
        Assert.assertArrayEquals(voters.get(2).getBytes(), election.getVotes().get(spec).get(2).getBytes());

        spec = new ABICallSpec("random-funct", new byte[][]{
                Hex.decode("aabb")
        });
        Assert.assertTrue(election.getVotes().containsKey(spec));
        voters = Arrays.asList(
                mockAddress("1111"),
                mockAddress("3333"),
                mockAddress("5555"),
                mockAddress("77")
        );

        Assert.assertArrayEquals(voters.get(0).getBytes(), election.getVotes().get(spec).get(0).getBytes());
        Assert.assertArrayEquals(voters.get(1).getBytes(), election.getVotes().get(spec).get(1).getBytes());
        Assert.assertArrayEquals(voters.get(2).getBytes(), election.getVotes().get(spec).get(2).getBytes());
        Assert.assertArrayEquals(voters.get(3).getBytes(), election.getVotes().get(spec).get(3).getBytes());
    }

    @Test
    public void deserializeElection_unevenOuterList() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.STARTING_WITH_FF_RECURSIVE);

        AddressBasedAuthorizer mockedAuthorizer = mock(AddressBasedAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(RskAddress.class))).thenReturn(true);

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

    @Test
    public void deserializeElection_invalidCallSpec() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.STARTING_WITH_FF_RECURSIVE);

        AddressBasedAuthorizer mockedAuthorizer = mock(AddressBasedAuthorizer.class);
        when(mockedAuthorizer.isAuthorized(any(RskAddress.class))).thenReturn(true);

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

    @Test
    public void serializeLockWhitelist() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeBigInteger();
        mock_RLP_encodeList();
        mock_RLP_encodeElement();

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
        Arrays.stream(addressesBytes).sorted(UnsignedBytes.lexicographicalComparator()).forEach(bytes -> {
            expectedBuilder.append("dd");
            expectedBuilder.append(Hex.toHexString(bytes));
            expectedBuilder.append("ff");
            expectedBuilder.append(Hex.toHexString(BigInteger.valueOf(maxToTransfer.value).toByteArray()));
        });
        expectedBuilder.append("ff00");
        byte[] expected = Hex.decode(expectedBuilder.toString());
        Assert.assertThat(result, is(expected));
    }

    @Test
    public void deserializeOneOffLockWhitelistAndDisableBlockHeight() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

        byte[][] addressesBytes = Arrays.asList(100, 200, 300, 400).stream()
                .map(k -> BtcECKey.fromPrivate(BigInteger.valueOf(k)))
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(k -> k.getPubKeyHash())
                .toArray(byte[][]::new);

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("09140314031403140302"); // 9 elements: 8 of 20 bytes (0x14 bytes) and 3 bytes interleaved plus one more element of 2 bytes
        for (int i = 0; i < addressesBytes.length; i++) {
            sampleBuilder.append(Hex.toHexString(addressesBytes[i]));
            sampleBuilder.append("0186a0"); // Coin.MILLICOIN
        }
        sampleBuilder.append("002a");
        byte[] sample = Hex.decode(sampleBuilder.toString());

        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializedLockWhitelist = BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(
                sample,
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
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

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
    public void serializeAndDeserializeFederationWithRealRLP() {
        NetworkParameters networkParms = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        byte[][] publicKeyBytes = new byte[][]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)).getPubKey(),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)).getPubKey(),
        };

        Federation federation = new Federation(
                Arrays.asList(
                        BtcECKey.fromPublicOnly(publicKeyBytes[0]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[1]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[2]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[3]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[4]),
                        BtcECKey.fromPublicOnly(publicKeyBytes[5])
                ),
                Instant.ofEpochMilli(0xabcdef),
                42L,
                networkParms
        );

        byte[] result = BridgeSerializationUtils.serializeFederation(federation);
        Federation deserializedFederation = BridgeSerializationUtils.deserializeFederation(result, networkParms);
        Assert.assertThat(federation, is(deserializedFederation));
    }

    @Test
    public void serializeRequestQueue() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeElement();
        mock_RLP_encodeBigInteger();
        mock_RLP_encodeList();

        List<ReleaseRequestQueue.Entry> sampleEntries = Arrays.asList(
                new ReleaseRequestQueue.Entry(mockAddressHash160("ccdd"), Coin.valueOf(10)),
                new ReleaseRequestQueue.Entry(mockAddressHash160("bb"), Coin.valueOf(50)),
                new ReleaseRequestQueue.Entry(mockAddressHash160("bb"), Coin.valueOf(20)),
                new ReleaseRequestQueue.Entry(mockAddressHash160("aa"), Coin.valueOf(30))
        );
        ReleaseRequestQueue sample = new ReleaseRequestQueue(sampleEntries);

        byte[] result = BridgeSerializationUtils.serializeReleaseRequestQueue(sample);
        String hexResult = Hex.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("ddccdd");
        expectedBuilder.append("ff0a");
        expectedBuilder.append("ddbb");
        expectedBuilder.append("ff32");
        expectedBuilder.append("ddbb");
        expectedBuilder.append("ff14");
        expectedBuilder.append("ddaa");
        expectedBuilder.append("ff1e");
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void deserializeRequestQueue_emptyOrNull() throws Exception {
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseRequestQueue(null, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).getEntries().size());
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseRequestQueue(new byte[]{}, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).getEntries().size());
    }

    @Test
    public void deserializeRequestQueue_nonEmpty() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        Address a1 = Address.fromBase58(params, "mynmcQfJnVjheAqh9XL6htnxPZnaDFbqkB");
        Address a2 = Address.fromBase58(params, "mfrfxeo5L2f5NDURS6YTtCNfVw2t5HAfty");
        Address a3 = Address.fromBase58(params, "myw7AMh5mpKHao6MArhn7EvkeASGsGJzrZ");
        List<ReleaseRequestQueue.Entry> expectedEntries = Arrays.asList(
                new ReleaseRequestQueue.Entry(a1, Coin.valueOf(10)),
                new ReleaseRequestQueue.Entry(a2, Coin.valueOf(7)),
                new ReleaseRequestQueue.Entry(a3, Coin.valueOf(8))
        );

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("06140114011401");
        sampleBuilder.append(Hex.toHexString(a1.getHash160()));
        sampleBuilder.append("0a");
        sampleBuilder.append(Hex.toHexString(a2.getHash160()));
        sampleBuilder.append("07");
        sampleBuilder.append(Hex.toHexString(a3.getHash160()));
        sampleBuilder.append("08");
        byte[] sample = Hex.decode(sampleBuilder.toString());
        ReleaseRequestQueue result = BridgeSerializationUtils.deserializeReleaseRequestQueue(sample, params);
        List<ReleaseRequestQueue.Entry> entries = result.getEntries();
        assertEquals(expectedEntries, entries);
    }

    @Test
    public void deserializeRequestQueue_nonEmptyOddSize() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

        try {
            byte[] sample = new byte[]{(byte)'b', 7, (byte)'d', 76, (byte) 'a'};
            BridgeSerializationUtils.deserializeReleaseRequestQueue(sample, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        } catch (RuntimeException e) {
            return;
        }
        Assert.fail();
    }

    @Test
    public void serializeTransactionSet() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_encodeElement();
        mock_RLP_encodeBigInteger();
        mock_RLP_encodeList();

        Set<ReleaseTransactionSet.Entry> sampleEntries = new HashSet<>(Arrays.asList(
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("ccdd"), 10L),
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("bb"), 20L),
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("ba"), 30L),
                new ReleaseTransactionSet.Entry(mockBtcTransactionSerialize("aa"), 40L)
        ));
        ReleaseTransactionSet sample = new ReleaseTransactionSet(sampleEntries);

        byte[] result = BridgeSerializationUtils.serializeReleaseTransactionSet(sample);
        String hexResult = Hex.toHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append("ddaa");
        expectedBuilder.append("ff28");
        expectedBuilder.append("ddba");
        expectedBuilder.append("ff1e");
        expectedBuilder.append("ddbb");
        expectedBuilder.append("ff14");
        expectedBuilder.append("ddccdd");
        expectedBuilder.append("ff0a");
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    @Test
    public void deserializeTransactionSet_emptyOrNull() throws Exception {
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseTransactionSet(null, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).getEntries().size());
        assertEquals(0, BridgeSerializationUtils.deserializeReleaseTransactionSet(new byte[]{}, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)).getEntries().size());
    }

    @Test
    public void deserializeTransactionSet_nonEmpty() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

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

        StringBuilder sampleBuilder = new StringBuilder();
        sampleBuilder.append("08");
        sampleBuilder.append(Integer.toHexString(t1.bitcoinSerialize().length));
        sampleBuilder.append("01");
        sampleBuilder.append(Integer.toHexString(t2.bitcoinSerialize().length));
        sampleBuilder.append("01");
        sampleBuilder.append(Integer.toHexString(t3.bitcoinSerialize().length));
        sampleBuilder.append("01");
        sampleBuilder.append(Integer.toHexString(t4.bitcoinSerialize().length));
        sampleBuilder.append("01");
        sampleBuilder.append(Hex.toHexString(t1.bitcoinSerialize()));
        sampleBuilder.append("20");
        sampleBuilder.append(Hex.toHexString(t2.bitcoinSerialize()));
        sampleBuilder.append("0e");
        sampleBuilder.append(Hex.toHexString(t3.bitcoinSerialize()));
        sampleBuilder.append("66");
        sampleBuilder.append(Hex.toHexString(t4.bitcoinSerialize()));
        sampleBuilder.append("14");
        byte[] sample = Hex.decode(sampleBuilder.toString());
        ReleaseTransactionSet result = BridgeSerializationUtils.deserializeReleaseTransactionSet(sample, params);
        Set<ReleaseTransactionSet.Entry> entries = result.getEntries();
        assertEquals(expectedEntries, entries);
    }

    @Test
    public void deserializeTransactionSet_nonEmptyOddSize() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        mock_RLP_decode2(InnerListMode.NONE);

        try {
            byte[] sample = new byte[]{(byte)'b', 7, (byte)'d', 76, (byte) 'a'};
            BridgeSerializationUtils.deserializeReleaseTransactionSet(sample, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
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
                result.add(new RLPItem(element));
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
            decoded.add(new RLPItem(element));
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

    private void mockAddressInstantiation(String addr) throws Exception {
        PowerMockito.whenNew(RskAddress.class)
                .withArguments(Hex.decode(addr))
                .then((InvocationOnMock invocation) -> mockAddress(addr));
    }

    private RskAddress mockAddress(String addr) {
        RskAddress mock = PowerMockito.mock(RskAddress.class);
        Mockito.when(mock.getBytes()).thenReturn(Hex.decode(addr));
        return mock;
    }
}
