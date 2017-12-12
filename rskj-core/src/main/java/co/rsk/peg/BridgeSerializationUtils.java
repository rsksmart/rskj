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
import co.rsk.crypto.Sha3Hash;
import com.google.common.primitives.UnsignedBytes;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by mario on 20/04/17.
 */
public class BridgeSerializationUtils {

    private static final int FEDERATION_RLP_LIST_SIZE = 3;
    private static final int FEDERATION_CREATION_TIME_INDEX = 0;
    private static final int FEDERATION_CREATION_BLOCK_NUMBER_INDEX = 1;
    private static final int FEDERATION_PUB_KEYS_INDEX = 2;

    private BridgeSerializationUtils(){}

    public static byte[] serializeMap(SortedMap<Sha3Hash, BtcTransaction> map) {
        int ntxs = map.size();

        byte[][] bytes = new byte[ntxs * 2][];
        int n = 0;

        for (Map.Entry<Sha3Hash, BtcTransaction> entry : map.entrySet()) {
            bytes[n++] = RLP.encodeElement(entry.getKey().getBytes());
            bytes[n++] = RLP.encodeElement(entry.getValue().bitcoinSerialize());
        }

        return RLP.encodeList(bytes);
    }

    public static byte[] serializePairMap(SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> map) {
        int ntxs = map.size();

        byte[][] bytes = new byte[ntxs * 3][];
        int n = 0;

        for (Map.Entry<Sha3Hash, Pair<BtcTransaction, Long>> entry : map.entrySet()) {
            bytes[n++] = RLP.encodeElement(entry.getKey().getBytes());
            bytes[n++] = RLP.encodeElement(entry.getValue().getLeft().bitcoinSerialize());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getValue().getRight()));
        }

        return RLP.encodeList(bytes);
    }

    public static SortedMap<Sha3Hash, BtcTransaction> deserializeMap(byte[] data, NetworkParameters networkParameters, boolean noInputsTxs) {
        SortedMap<Sha3Hash, BtcTransaction> map = new TreeMap<>();

        if (data == null || data.length == 0) {
            return map;
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        int ntxs = rlpList.size() / 2;

        for (int k = 0; k < ntxs; k++) {
            Sha3Hash hash = new Sha3Hash(rlpList.get(k * 2).getRLPData());
            byte[] payload = rlpList.get(k * 2 + 1).getRLPData();
            BtcTransaction tx;
            if (!noInputsTxs) {
                tx = new BtcTransaction(networkParameters, payload);
            } else {
                tx = new BtcTransaction(networkParameters);
                tx.parseNoInputs(payload);
            }
            map.put(hash, tx);
        }

        return map;
    }

    public static SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> deserializePairMap(byte[] data, NetworkParameters networkParameters) {
        SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> map = new TreeMap<>();

        if (data == null || data.length == 0) {
            return map;
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        int ntxs = rlpList.size() / 3;

        for (int k = 0; k < ntxs; k++) {
            Sha3Hash hash = new Sha3Hash(rlpList.get(k * 3).getRLPData());
            BtcTransaction tx = new BtcTransaction(networkParameters, rlpList.get(k * 3 + 1).getRLPData());
            byte[] lkeyBytes = rlpList.get(k * 3 + 2).getRLPData();
            Long lkey = lkeyBytes == null ? 0 : (new BigInteger(1, lkeyBytes)).longValue();
            map.put(hash, Pair.of(tx, lkey));
        }

        return map;
    }

    public static byte[] serializeUTXOList(List<UTXO> list) throws IOException {
        int nutxos = list.size();

        byte[][] bytes = new byte[nutxos][];
        int n = 0;

        for (UTXO utxo : list) {
            try (ByteArrayOutputStream ostream = new ByteArrayOutputStream()) {
                utxo.serializeToStream(ostream);
                bytes[n++] = RLP.encodeElement(ostream.toByteArray());
            }
        }

        return RLP.encodeList(bytes);
    }

    public static List<UTXO> deserializeUTXOList(byte[] data) throws IOException {
        List<UTXO> list = new ArrayList<>();

        if (data == null || data.length == 0) {
            return list;
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        int nutxos = rlpList.size();

        for (int k = 0; k < nutxos; k++) {
            byte[] utxoBytes = rlpList.get(k).getRLPData();
            InputStream istream = new ByteArrayInputStream(utxoBytes);
            UTXO utxo = new UTXO(istream);
            list.add(utxo);
        }

        return list;
    }

    public static byte[] serializeSet(SortedSet<Sha256Hash> set) {
        int nhashes = set.size();

        byte[][] bytes = new byte[nhashes][];
        int n = 0;

        for (Sha256Hash hash : set) {
            bytes[n++] = RLP.encodeElement(hash.getBytes());
        }

        return RLP.encodeList(bytes);
    }

    public static SortedSet<Sha256Hash> deserializeSet(byte[] data) {
        SortedSet<Sha256Hash> set = new TreeSet<>();

        if (data == null || data.length == 0) {
            return set;
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        int nhashes = rlpList.size();

        for (int k = 0; k < nhashes; k++) {
            set.add(Sha256Hash.wrap(rlpList.get(k).getRLPData()));
        }

        return set;
    }

    public static byte[] serializeMapOfHashesToLong(Map<Sha256Hash, Long> map) {
        byte[][] bytes = new byte[map.size() * 2][];
        int n = 0;

        List<Sha256Hash> sortedHashes = new ArrayList<>(map.keySet());
        Collections.sort(sortedHashes);

        for (Sha256Hash hash : sortedHashes) {
            Long value = map.get(hash);
            bytes[n++] = RLP.encodeElement(hash.getBytes());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(value));
        }

        return RLP.encodeList(bytes);
    }

    public static Map<Sha256Hash, Long> deserializeMapOfHashesToLong(byte[] data) {
        Map<Sha256Hash, Long> map = new HashMap<>();

        if (data == null || data.length == 0) {
            return map;
        }

        RLPList rlpList = (RLPList) RLP.decode2(data).get(0);

        // List size must be even - key, value pairs expected in sequence
        if (rlpList.size() % 2 != 0) {
            throw new RuntimeException("deserializeMapOfHashesToLong: expected an even number of entries, but odd given");
        }

        int numEntries = rlpList.size() / 2;

        for (int k = 0; k < numEntries; k++) {
            Sha256Hash hash = Sha256Hash.wrap(rlpList.get(k * 2).getRLPData());
            Long number = new BigInteger(rlpList.get(k * 2 + 1).getRLPData()).longValue();
            map.put(hash, number);
        }

        return map;
    }

    // A federation is serialized as a list in the following order:
    // creation time
    // list of public keys -> [pubkey1, pubkey2, ..., pubkeyn], sorted
    // using the lexicographical order of the public keys (see BtcECKey.PUBKEY_COMPARATOR).
    public static byte[] serializeFederation(Federation federation) {
        List<byte[]> publicKeys = federation.getPublicKeys().stream()
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(key -> RLP.encodeElement(key.getPubKey()))
                .collect(Collectors.toList());
        byte[][] rlpElements = new byte[FEDERATION_RLP_LIST_SIZE][];
        rlpElements[FEDERATION_CREATION_TIME_INDEX] = RLP.encodeBigInteger(BigInteger.valueOf(federation.getCreationTime().toEpochMilli()));
        rlpElements[FEDERATION_CREATION_BLOCK_NUMBER_INDEX] = RLP.encodeBigInteger(BigInteger.valueOf(federation.getCreationBlockNumber()));
        rlpElements[FEDERATION_PUB_KEYS_INDEX] = RLP.encodeList((byte[][])publicKeys.toArray(new byte[publicKeys.size()][]));
        return RLP.encodeList(rlpElements);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeFederation
    public static Federation deserializeFederation(byte[] data, Context btcContext) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        if (rlpList.size() != FEDERATION_RLP_LIST_SIZE) {
            throw new RuntimeException(String.format("Invalid serialized Federation. Expected %d elements but got %d", FEDERATION_RLP_LIST_SIZE, rlpList.size()));
        }

        byte[] creationTimeBytes = rlpList.get(FEDERATION_CREATION_TIME_INDEX).getRLPData();
        Instant creationTime = Instant.ofEpochMilli(BigIntegers.fromUnsignedByteArray(creationTimeBytes).longValue());

        List<BtcECKey> pubKeys = ((RLPList) rlpList.get(FEDERATION_PUB_KEYS_INDEX)).stream()
                .map(pubKeyBytes -> BtcECKey.fromPublicOnly(pubKeyBytes.getRLPData()))
                .collect(Collectors.toList());

        byte[] creationBlockNumberBytes = rlpList.get(FEDERATION_CREATION_BLOCK_NUMBER_INDEX).getRLPData();
        long creationBlockNumber = BigIntegers.fromUnsignedByteArray(creationBlockNumberBytes).longValue();

        return new Federation(pubKeys, creationTime, creationBlockNumber, btcContext.getParams());
    }

    // A pending federation is serialized as the
    // public keys conforming it.
    // See BridgeSerializationUtils::serializePublicKeys
    public static byte[] serializePendingFederation(PendingFederation pendingFederation) {
        return serializeBtcPublicKeys(pendingFederation.getPublicKeys());
    }

    // For the serialization format, see BridgeSerializationUtils::serializePendingFederation
    // and serializePublicKeys::deserializePublicKeys
    public static PendingFederation deserializePendingFederation(byte[] data) {
        return new PendingFederation(deserializeBtcPublicKeys(data));
    }

    // An ABI call election is serialized as a list of the votes, like so:
    // spec_1, voters_1, ..., spec_n, voters_n
    // Specs are sorted by their signed byte encoding lexicographically.
    public static byte[] serializeElection(ABICallElection election) {
        byte[][] bytes = new byte[election.getVotes().size() * 2][];
        int n = 0;

        Map<ABICallSpec, List<TxSender>> votes = election.getVotes();
        ABICallSpec[] specs = votes.keySet().toArray(new ABICallSpec[0]);
        Arrays.sort(specs, ABICallSpec.byBytesComparator);

        for (ABICallSpec spec : specs) {
            bytes[n++] = serializeABICallSpec(spec);
            bytes[n++] = serializeVoters(votes.get(spec));
        }

        return RLP.encodeList(bytes);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeElection
    public static ABICallElection deserializeElection(byte[] data, AddressBasedAuthorizer authorizer) {
        if (data == null || data.length == 0) {
            return new ABICallElection(authorizer);
        }

        RLPList rlpList = (RLPList) RLP.decode2(data).get(0);

        // List size must be even - key, value pairs expected in sequence
        if (rlpList.size() % 2 != 0) {
            throw new RuntimeException("deserializeElection: expected an even number of entries, but odd given");
        }

        int numEntries = rlpList.size() / 2;

        Map<ABICallSpec, List<TxSender>> votes = new HashMap<>();

        for (int k = 0; k < numEntries; k++) {
            ABICallSpec spec = deserializeABICallSpec(rlpList.get(k * 2).getRLPData());
            List<TxSender> specVotes = deserializeVoters(rlpList.get(k * 2 + 1).getRLPData());
            votes.put(spec, specVotes);
        }

        return new ABICallElection(authorizer, votes);
    }

    // A lock whitelist is just the serialization of
    // the underlying btc public keys
    // See BridgeSerializationUtils::serializeBtcAddresses for details
    public static byte[] serializeLockWhitelist(LockWhitelist whitelist) {
        return serializeBtcAddresses(whitelist.getAddresses());
    }

    // For the serialization format, see BridgeSerializationUtils::serializeLockWhitelist
    public static LockWhitelist deserializeLockWhitelist(byte[] data, NetworkParameters parameters) {
        return new LockWhitelist(deserializeBtcAddresses(data, parameters));
    }

    // An ABI call spec is serialized as:
    // function name encoded in UTF-8
    // arg_1, ..., arg_n
    private static byte[] serializeABICallSpec(ABICallSpec spec) {
        byte[][] encodedArguments = Arrays.stream(spec.getArguments())
                .map(arg -> RLP.encodeElement(arg))
                .toArray(byte[][]::new);
        return RLP.encodeList(
                RLP.encodeElement(spec.getFunction().getBytes(StandardCharsets.UTF_8)),
                RLP.encodeList(encodedArguments)
        );
    }

    // For the serialization format, see BridgeSerializationUtils::serializeABICallSpec
    private static ABICallSpec deserializeABICallSpec(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        if (rlpList.size() != 2) {
            throw new RuntimeException(String.format("Invalid serialized ABICallSpec. Expected 2 elements, but got %d", rlpList.size()));
        }

        String function = new String(rlpList.get(0).getRLPData(), StandardCharsets.UTF_8);
        byte[][] arguments = ((RLPList)rlpList.get(1)).stream().map(rlpElement -> rlpElement.getRLPData()).toArray(byte[][]::new);

        return new ABICallSpec(function, arguments);
    }

    // A list of btc public keys is serialized as
    // [pubkey1, pubkey2, ..., pubkeyn], sorted
    // using the lexicographical order of the public keys
    // (see BtcECKey.PUBKEY_COMPARATOR).
    private static byte[] serializeBtcPublicKeys(List<BtcECKey> keys) {
        List<byte[]> encodedKeys = keys.stream()
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(key -> RLP.encodeElement(key.getPubKey()))
                .collect(Collectors.toList());
        return RLP.encodeList(encodedKeys.toArray(new byte[0][]));
    }

    // For the serialization format, see BridgeSerializationUtils::serializePublicKeys
    private static List<BtcECKey> deserializeBtcPublicKeys(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        return rlpList.stream()
                .map(pubKeyBytes -> BtcECKey.fromPublicOnly(pubKeyBytes.getRLPData()))
                .collect(Collectors.toList());
    }

    // A list of btc addresses is serialized as
    // [addr1, addr2, ..., addrn], sorted
    // using the lexicographical order of the addresses
    // interpreting the bytes as unsigned.
    private static byte[] serializeBtcAddresses(List<Address> addresses) {
        List<byte[]> encodedAddresses = addresses.stream()
                .sorted((Address a1, Address a2) ->
                        UnsignedBytes.lexicographicalComparator().compare(a1.getHash160(), a2.getHash160())
                )
                .map(key -> RLP.encodeElement(key.getHash160()))
                .collect(Collectors.toList());
        return RLP.encodeList(encodedAddresses.toArray(new byte[0][]));
    }

    // For the serialization format, see BridgeSerializationUtils::serializeBtcAddresses
    private static List<Address> deserializeBtcAddresses(byte[] data, NetworkParameters parameters) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        return rlpList.stream()
                .map(addressBytes -> new Address(parameters, addressBytes.getRLPData()))
                .collect(Collectors.toList());
    }

    // A list of voters is serialized as
    // [voterBytes1, voterBytes2, ..., voterBytesn], sorted
    // using the lexicographical order of the voters' unsigned bytes
    private static byte[] serializeVoters(List<TxSender> voters) {
        List<byte[]> encodedKeys = voters.stream()
                .sorted((TxSender v1, TxSender v2) ->
                        UnsignedBytes.lexicographicalComparator().compare(v1.getBytes(), v2.getBytes())
                )
                .map(key -> RLP.encodeElement(key.getBytes()))
                .collect(Collectors.toList());
        return RLP.encodeList(encodedKeys.toArray(new byte[0][]));
    }

    // For the serialization format, see BridgeSerializationUtils::serializeVoters
    private static List<TxSender> deserializeVoters(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        return rlpList.stream()
                .map(pubKeyBytes -> new TxSender(pubKeyBytes.getRLPData()))
                .collect(Collectors.toList());
    }
}
