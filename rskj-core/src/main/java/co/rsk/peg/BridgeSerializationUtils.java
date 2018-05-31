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
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Whitelist.LockWhitelist;
import co.rsk.peg.Whitelist.LockWhitelistEntry;
import co.rsk.peg.Whitelist.OneOffWhiteListEntry;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import javax.annotation.Nullable;
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

    public static byte[] serializeMap(SortedMap<Keccak256, BtcTransaction> map) {
        int ntxs = map.size();

        byte[][] bytes = new byte[ntxs * 2][];
        int n = 0;

        for (Map.Entry<Keccak256, BtcTransaction> entry : map.entrySet()) {
            bytes[n++] = RLP.encodeElement(entry.getKey().getBytes());
            bytes[n++] = RLP.encodeElement(entry.getValue().bitcoinSerialize());
        }

        return RLP.encodeList(bytes);
    }

    public static SortedMap<Keccak256, BtcTransaction> deserializeMap(byte[] data, NetworkParameters networkParameters, boolean noInputsTxs) {
        SortedMap<Keccak256, BtcTransaction> map = new TreeMap<>();

        if (data == null || data.length == 0) {
            return map;
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        int ntxs = rlpList.size() / 2;

        for (int k = 0; k < ntxs; k++) {
            Keccak256 hash = new Keccak256(rlpList.get(k * 2).getRLPData());
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
            Long number = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 2 + 1).getRLPData()).longValue();
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

        Map<ABICallSpec, List<RskAddress>> votes = election.getVotes();
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

        Map<ABICallSpec, List<RskAddress>> votes = new HashMap<>();

        for (int k = 0; k < numEntries; k++) {
            ABICallSpec spec = deserializeABICallSpec(rlpList.get(k * 2).getRLPData());
            List<RskAddress> specVotes = deserializeVoters(rlpList.get(k * 2 + 1).getRLPData());
            votes.put(spec, specVotes);
        }

        return new ABICallElection(authorizer, votes);
    }

    public static byte[] serializeLockWhitelist(LockWhitelist whitelist) {
        List<Address> whitelistAddresses = whitelist.getAddresses();
        int serializationSize = whitelistAddresses.size() * 2 + 1;
        byte[][] serializedLockWhitelist = new byte[serializationSize][];
        for (int i = 0; i < whitelistAddresses.size(); i++) {
            Address address = whitelistAddresses.get(i);
            serializedLockWhitelist[2 * i] = RLP.encodeElement(address.getHash160());
            serializedLockWhitelist[2 * i + 1] = RLP.encodeBigInteger(BigInteger.valueOf(whitelist.getMaxTransferValue(address).longValue()));
        }
        serializedLockWhitelist[serializationSize - 1] = RLP.encodeBigInteger(BigInteger.valueOf(whitelist.getDisableBlockHeight()));
        return RLP.encodeList(serializedLockWhitelist);
    }

    public static LockWhitelist deserializeLockWhitelist(byte[] data, NetworkParameters parameters) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);
        int serializedAddressesSize = rlpList.size() - 1;

        // serialized addresses size must be even - key, value pairs expected in sequence
        if (serializedAddressesSize % 2 != 0) {
            throw new RuntimeException("deserializeLockWhitelist: expected an even number of addresses, but odd given");
        }

        Map<Address, LockWhitelistEntry> whitelist = new HashMap<>(serializedAddressesSize / 2);
        for (int i = 0; i < serializedAddressesSize; i = i + 2) {
            byte[] hash160 = rlpList.get(i).getRLPData();
            byte[] maxValueData = rlpList.get(i + 1).getRLPData();
            Address address = new Address(parameters, hash160);
            whitelist.put(
                    address,
                new OneOffWhiteListEntry(address, Coin.valueOf(safeToBigInteger(maxValueData).longValueExact()))
            );
        }
        int disableBlockHeight = safeToBigInteger(rlpList.get(serializedAddressesSize).getRLPData()).intValueExact();
        return new LockWhitelist(whitelist, disableBlockHeight);
    }

    private static BigInteger safeToBigInteger(byte[] data) {
        return data == null ? BigInteger.ZERO : BigIntegers.fromUnsignedByteArray(data);
    }

    public static byte[] serializeCoin(Coin coin) {
        return RLP.encodeBigInteger(BigInteger.valueOf(coin.getValue()));
    }

    @Nullable
    public static Coin deserializeCoin(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        return Coin.valueOf(RLP.decodeBigInteger(data, 0).longValueExact());
    }

    // A ReleaseRequestQueue is serialized as follows:
    // [address_1, amount_1, ..., address_n, amount_n]
    // with address_i being the encoded bytes of each btc address
    // and amount_i the RLP-encoded biginteger corresponding to each amount
    // Order of entries in serialized output is order of the request queue entries
    // so that we enforce a FIFO policy on release requests.
    public static byte[] serializeReleaseRequestQueue(ReleaseRequestQueue queue) {
        List<ReleaseRequestQueue.Entry> entries = queue.getEntries();

        byte[][] bytes = new byte[entries.size() * 2][];
        int n = 0;

        for (ReleaseRequestQueue.Entry entry : entries) {
            bytes[n++] = RLP.encodeElement(entry.getDestination().getHash160());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getAmount().getValue()));
        }

        return RLP.encodeList(bytes);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeReleaseRequestQueue
    public static ReleaseRequestQueue deserializeReleaseRequestQueue(byte[] data, NetworkParameters networkParameters) {
        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();

        if (data == null || data.length == 0) {
            return new ReleaseRequestQueue(entries);
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        // Must have an even number of items
        if (rlpList.size() % 2 != 0) {
            throw new RuntimeException(String.format("Invalid serialized ReleaseRequestQueue. Expected an even number of elements, but got %d", rlpList.size()));
        }

        int n = rlpList.size() / 2;

        for (int k = 0; k < n; k++) {
            byte[] addressBytes = rlpList.get(k * 2).getRLPData();
            Address address = new Address(networkParameters, addressBytes);
            Long amount = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 2 + 1).getRLPData()).longValue();

            entries.add(new ReleaseRequestQueue.Entry(address, Coin.valueOf(amount)));
        }

        return new ReleaseRequestQueue(entries);
    }

    // A ReleaseTransactionSet is serialized as follows:
    // [btctx_1, height_1, ..., btctx_n, height_n]
    // with btctx_i being the bitcoin serialization of each btc tx
    // and height_i the RLP-encoded biginteger corresponding to each height
    // To preserve order amongst different implementations of sets,
    // entries are first sorted on the lexicographical order of the
    // serialized btc transaction bytes
    // (see ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR)
    public static byte[] serializeReleaseTransactionSet(ReleaseTransactionSet set) {
        List<ReleaseTransactionSet.Entry> entries = set.getEntries().stream().collect(Collectors.toList());
        entries.sort(ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR);

        byte[][] bytes = new byte[entries.size() * 2][];
        int n = 0;

        for (ReleaseTransactionSet.Entry entry : entries) {
            bytes[n++] = RLP.encodeElement(entry.getTransaction().bitcoinSerialize());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getRskBlockNumber()));
        }

        return RLP.encodeList(bytes);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeReleaseTransactionSet
    public static ReleaseTransactionSet deserializeReleaseTransactionSet(byte[] data, NetworkParameters networkParameters) {
        Set<ReleaseTransactionSet.Entry> entries = new HashSet<>();

        if (data == null || data.length == 0) {
            return new ReleaseTransactionSet(entries);
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        // Must have an even number of items
        if (rlpList.size() % 2 != 0) {
            throw new RuntimeException(String.format("Invalid serialized ReleaseTransactionSet. Expected an even number of elements, but got %d", rlpList.size()));
        }

        int n = rlpList.size() / 2;

        for (int k = 0; k < n; k++) {
            byte[] txPayload = rlpList.get(k * 2).getRLPData();
            BtcTransaction tx =  new BtcTransaction(networkParameters, txPayload);

            Long height = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 2 + 1).getRLPData()).longValue();

            entries.add(new ReleaseTransactionSet.Entry(tx, height));
        }

        return new ReleaseTransactionSet(entries);
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

    // A list of voters is serialized as
    // [voterBytes1, voterBytes2, ..., voterBytesn], sorted
    // using the lexicographical order of the voters' unsigned bytes
    private static byte[] serializeVoters(List<RskAddress> voters) {
        List<byte[]> encodedKeys = voters.stream()
                .sorted(RskAddress.LEXICOGRAPHICAL_COMPARATOR)
                .map(key -> RLP.encodeElement(key.getBytes()))
                .collect(Collectors.toList());
        return RLP.encodeList(encodedKeys.toArray(new byte[0][]));
    }

    // For the serialization format, see BridgeSerializationUtils::serializeVoters
    private static List<RskAddress> deserializeVoters(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        return rlpList.stream()
                .map(pubKeyBytes -> new RskAddress(pubKeyBytes.getRLPData()))
                .collect(Collectors.toList());
    }
}
