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
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

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
    private static final int FEDERATION_MEMBERS_INDEX = 2;

    private static final int FEDERATION_MEMBER_LIST_SIZE = 3;
    private static final int FEDERATION_MEMBER_BTC_KEY_INDEX = 0;
    private static final int FEDERATION_MEMBER_RSK_KEY_INDEX = 1;
    private static final int FEDERATION_MEMBER_MST_KEY_INDEX = 2;


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
            long number = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 2 + 1).getRLPData()).longValue();
            map.put(hash, number);
        }

        return map;
    }

    private interface FederationMemberSerializer {
        byte[] serialize(FederationMember federationMember);
    }

    private interface FederationMemberDesserializer {
        FederationMember deserialize(byte[] data);
    }

    /**
     * A federation is serialized as a list in the following order:
     * - creation time
     * - creation block number
     * - list of federation members -> [member1, member2, ..., membern], sorted
     * using the lexicographical order of the public keys of the members
     * (see FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR).
     * Each federation member is in turn serialized using the provided FederationMemberSerializer.
     */
    private static byte[] serializeFederationWithSerializer(Federation federation, FederationMemberSerializer federationMemberSerializer) {
        List<byte[]> federationMembers = federation.getMembers().stream()
                .sorted(FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR)
                .map(member -> RLP.encodeElement(federationMemberSerializer.serialize(member)))
                .collect(Collectors.toList());

        byte[][] rlpElements = new byte[FEDERATION_RLP_LIST_SIZE][];
        rlpElements[FEDERATION_CREATION_TIME_INDEX] = RLP.encodeBigInteger(BigInteger.valueOf(federation.getCreationTime().toEpochMilli()));
        rlpElements[FEDERATION_CREATION_BLOCK_NUMBER_INDEX] = RLP.encodeBigInteger(BigInteger.valueOf(federation.getCreationBlockNumber()));
        rlpElements[FEDERATION_MEMBERS_INDEX] = RLP.encodeList((byte[][])federationMembers.toArray(new byte[federationMembers.size()][]));
        return RLP.encodeList(rlpElements);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeFederationWithSerializer
    private static Federation deserializeFederationWithDesserializer(
        byte[] data,
        NetworkParameters networkParameters,
        FederationMemberDesserializer federationMemberDesserializer) {

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        if (rlpList.size() != FEDERATION_RLP_LIST_SIZE) {
            throw new RuntimeException(String.format("Invalid serialized Federation. Expected %d elements but got %d", FEDERATION_RLP_LIST_SIZE, rlpList.size()));
        }

        byte[] creationTimeBytes = rlpList.get(FEDERATION_CREATION_TIME_INDEX).getRLPData();
        Instant creationTime = Instant.ofEpochMilli(BigIntegers.fromUnsignedByteArray(creationTimeBytes).longValue());

        byte[] creationBlockNumberBytes = rlpList.get(FEDERATION_CREATION_BLOCK_NUMBER_INDEX).getRLPData();
        long creationBlockNumber = BigIntegers.fromUnsignedByteArray(creationBlockNumberBytes).longValue();

        RLPList rlpMembers = (RLPList) rlpList.get(FEDERATION_MEMBERS_INDEX);

        List<FederationMember> federationMembers = new ArrayList();

        for (int k = 0; k < rlpMembers.size(); k++) {
            RLPElement element = rlpMembers.get(k);
            FederationMember member = federationMemberDesserializer.deserialize(element.getRLPData());
            federationMembers.add(member);
        }

        return new Federation(federationMembers, creationTime, creationBlockNumber, networkParameters);
    }

    /**
     * For the federation serialization format, see serializeFederationWithSerializer.
     * For compatibility with blocks before the Wasabi network upgrade,
     * each federation member is serialized only as its compressed BTC public key.
     */
    public static byte[] serializeFederationOnlyBtcKeys(Federation federation) {
        return serializeFederationWithSerializer(federation,
                federationMember -> federationMember.getBtcPublicKey().getPubKeyPoint().getEncoded(true));
    }

    // For the serialization format, see BridgeSerializationUtils::serializeFederationOnlyBtcKeys
    public static Federation deserializeFederationOnlyBtcKeys(byte[] data, NetworkParameters networkParameters) {
        return deserializeFederationWithDesserializer(data, networkParameters,
                (pubKeyBytes -> FederationMember.getFederationMemberFromKey(BtcECKey.fromPublicOnly(pubKeyBytes))));
    }

    /**
     * For the federation serialization format, see serializeFederationWithSerializer.
     * For the federation member serialization format, see serializeFederationMember.
     */
    public static byte[] serializeFederation(Federation federation) {
        return serializeFederationWithSerializer(federation,
                BridgeSerializationUtils::serializeFederationMember);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeFederation
    public static Federation deserializeFederation(byte[] data, NetworkParameters networkParameters) {
        return deserializeFederationWithDesserializer(data, networkParameters,
                BridgeSerializationUtils::deserializeFederationMember);
    }

    /**
     * A FederationMember is serialized as a list in the following order:
     * - BTC public key
     * - RSK public key
     * - MST public key
     * All keys are stored in their COMPRESSED versions.
     */
    public static byte[] serializeFederationMember(FederationMember federationMember) {
        byte[][] rlpElements = new byte[FEDERATION_MEMBER_LIST_SIZE][];
        rlpElements[FEDERATION_MEMBER_BTC_KEY_INDEX] = RLP.encodeElement(
                federationMember.getBtcPublicKey().getPubKeyPoint().getEncoded(true)
        );
        rlpElements[FEDERATION_MEMBER_RSK_KEY_INDEX] = RLP.encodeElement(federationMember.getRskPublicKey().getPubKey(true));
        rlpElements[FEDERATION_MEMBER_MST_KEY_INDEX] = RLP.encodeElement(federationMember.getMstPublicKey().getPubKey(true));
        return RLP.encodeList(rlpElements);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeFederationMember
    private static FederationMember deserializeFederationMember(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        if (rlpList.size() != FEDERATION_RLP_LIST_SIZE) {
            throw new RuntimeException(String.format("Invalid serialized FederationMember. Expected %d elements but got %d", FEDERATION_MEMBER_LIST_SIZE, rlpList.size()));
        }

        BtcECKey btcKey = BtcECKey.fromPublicOnly(rlpList.get(FEDERATION_MEMBER_BTC_KEY_INDEX).getRLPData());
        ECKey rskKey = ECKey.fromPublicOnly(rlpList.get(FEDERATION_MEMBER_RSK_KEY_INDEX).getRLPData());
        ECKey mstKey = ECKey.fromPublicOnly(rlpList.get(FEDERATION_MEMBER_MST_KEY_INDEX).getRLPData());

        return new FederationMember(btcKey, rskKey, mstKey);
    }

    /**
     * A pending federation is serialized as the
     * public keys conforming it.
     * This is a legacy format for blocks before the Wasabi
     * network upgrade.
     * See BridgeSerializationUtils::serializeBtcPublicKeys
     */
    public static byte[] serializePendingFederationOnlyBtcKeys(PendingFederation pendingFederation) {
        return serializeBtcPublicKeys(pendingFederation.getBtcPublicKeys());
    }

    // For the serialization format, see BridgeSerializationUtils::serializePendingFederationOnlyBtcKeys
    // and serializePublicKeys::deserializeBtcPublicKeys
    public static PendingFederation deserializePendingFederationOnlyBtcKeys(byte[] data) {
        // BTC, RSK and MST keys are the same
        List<FederationMember> members = deserializeBtcPublicKeys(data).stream().map(pk ->
            FederationMember.getFederationMemberFromKey(pk)
        ).collect(Collectors.toList());

        return new PendingFederation(members);
    }

    /**
     * A pending federation is serialized as the
     * list of its sorted members serialized.
     * For the member serialization format, see BridgeSerializationUtils::serializeFederationMember
     */
    public static byte[] serializePendingFederation(PendingFederation pendingFederation) {
        List<byte[]> encodedMembers = pendingFederation.getMembers().stream()
                .sorted(FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR)
                .map(BridgeSerializationUtils::serializeFederationMember)
                .collect(Collectors.toList());
        return RLP.encodeList(encodedMembers.toArray(new byte[0][]));
    }

    // For the serialization format, see BridgeSerializationUtils::serializePendingFederation
    public static PendingFederation deserializePendingFederation(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        List<FederationMember> members = new ArrayList<>();

        for (int k = 0; k < rlpList.size(); k++) {
            RLPElement element = rlpList.get(k);
            FederationMember member = deserializeFederationMember(element.getRLPData());
            members.add(member);
        }

        return new PendingFederation(members);
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

    /**
     * Serializes the data stored in the Tuple.
     * @param data data MUST be composed of a list of {@link co.rsk.peg.whitelist.OneOffWhiteListEntry} and the value of disableBlockHeight obtained from {@link co.rsk.peg.whitelist.LockWhitelist}
     * @return the serialized data
     */
    public static byte[] serializeOneOffLockWhitelist(Pair<List<OneOffWhiteListEntry>, Integer> data) {
        List<OneOffWhiteListEntry> entries = data.getLeft();
        Integer disableBlockHeight = data.getRight();
        int serializationSize = entries.size() * 2 + 1;
        byte[][] serializedLockWhitelist = new byte[serializationSize][];
        for (int i = 0; i < entries.size(); i++) {
            OneOffWhiteListEntry entry = entries.get(i);
            serializedLockWhitelist[2 * i] = RLP.encodeElement(entry.address().getHash160());
            serializedLockWhitelist[2 * i + 1] = RLP.encodeBigInteger(BigInteger.valueOf(entry.maxTransferValue().longValue()));
        }
        serializedLockWhitelist[serializationSize - 1] = RLP.encodeBigInteger(BigInteger.valueOf(disableBlockHeight));
        return RLP.encodeList(serializedLockWhitelist);
    }

    /**
     * Serializes the provided list of {@link co.rsk.peg.whitelist.UnlimitedWhiteListEntry}
     * @param entries
     * @return the serialized data
     */
    public static byte[] serializeUnlimitedLockWhitelist(List<UnlimitedWhiteListEntry> entries) {
        int serializationSize = entries.size();
        byte[][] serializedLockWhitelist = new byte[serializationSize][];
        for (int i = 0; i < entries.size(); i++) {
            serializedLockWhitelist[i] = RLP.encodeElement(entries.get(i).address().getHash160());
        }
        return RLP.encodeList(serializedLockWhitelist);
    }

    public static Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> deserializeOneOffLockWhitelistAndDisableBlockHeight(byte[] data, NetworkParameters parameters) {
        if (data == null || data.length == 0) {
            return null;
        }
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);
        int serializedAddressesSize = rlpList.size() - 1;

        // serialized addresses size must be even - key, value pairs expected in sequence
        if (serializedAddressesSize % 2 != 0) {
            throw new RuntimeException("deserializeLockWhitelist: expected an even number of addresses, but odd given");
        }

        HashMap<Address, OneOffWhiteListEntry> entries = new HashMap<>(serializedAddressesSize / 2);
        for (int i = 0; i < serializedAddressesSize; i = i + 2) {
            byte[] hash160 = rlpList.get(i).getRLPData();
            byte[] maxTransferValueData = rlpList.get(i + 1).getRLPData();
            Address address = new Address(parameters, hash160);
            entries.put(address, new OneOffWhiteListEntry(address, Coin.valueOf(safeToBigInteger(maxTransferValueData).longValueExact())));
        }
        int disableBlockHeight = safeToBigInteger(rlpList.get(serializedAddressesSize).getRLPData()).intValueExact();
        return Pair.of(entries, disableBlockHeight);
    }

    public static Map<Address, UnlimitedWhiteListEntry> deserializeUnlimitedLockWhitelistEntries(byte[] data, NetworkParameters parameters) {
        if (data == null) {
            return new HashMap<>();
        }

        RLPList unlimitedWhitelistEntriesRlpList = (RLPList)RLP.decode2(data).get(0);
        int unlimitedWhitelistEntriesSerializedAddressesSize = unlimitedWhitelistEntriesRlpList.size();

        Map<Address, UnlimitedWhiteListEntry> entries = new HashMap<>(unlimitedWhitelistEntriesSerializedAddressesSize);

        for (int j = 0; j < unlimitedWhitelistEntriesSerializedAddressesSize; j++) {
            byte[] hash160 = unlimitedWhitelistEntriesRlpList.get(j).getRLPData();
            Address address = new Address(parameters, hash160);
            entries.put(address, new UnlimitedWhiteListEntry(address));
        }

        return entries;
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
        List<ReleaseRequestQueue.Entry> entries = queue.getEntriesWithoutHash();

        byte[][] bytes = new byte[entries.size() * 2][];
        int n = 0;

        for (ReleaseRequestQueue.Entry entry : entries) {
            bytes[n++] = RLP.encodeElement(entry.getDestination().getHash160());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getAmount().getValue()));
        }

        return RLP.encodeList(bytes);
    }

    public static byte[] serializeReleaseRequestQueueWithTxHash(ReleaseRequestQueue queue) {
        List<ReleaseRequestQueue.Entry> entries = queue.getEntriesWithHash();

        byte[][] bytes = new byte[entries.size() * 3][];
        int n = 0;

        for (ReleaseRequestQueue.Entry entry : entries) {
            bytes[n++] = RLP.encodeElement(entry.getDestination().getHash160());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getAmount().getValue()));
            bytes[n++] = RLP.encodeElement(entry.getRskTxHash().getBytes());
        }

        return RLP.encodeList(bytes);
    }

    public static List<ReleaseRequestQueue.Entry> deserializeReleaseRequestQueue(byte[] data, NetworkParameters networkParameters) {
        return deserializeReleaseRequestQueue(data, networkParameters, false);
    }

    public static List<ReleaseRequestQueue.Entry> deserializeReleaseRequestQueue(byte[] data, NetworkParameters networkParameters, boolean hasTxHash) {
        if (data == null || data.length == 0) {
            return new ArrayList<>();
        }

        int elementsMultipleCount = hasTxHash ? 3 : 2;
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        // Must have an even number of items
        if (rlpList.size() % elementsMultipleCount != 0) {
            throw new RuntimeException(String.format("Invalid serialized ReleaseRequestQueue. Expected a multiple of %d number of elements, but got %d", elementsMultipleCount, rlpList.size()));
        }

        return hasTxHash ? deserializeReleaseRequestQueueWithTxHash(rlpList, networkParameters) : deserializeReleaseRequestQueueWithoutTxHash(rlpList, networkParameters);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeReleaseRequestQueue
    private static List<ReleaseRequestQueue.Entry> deserializeReleaseRequestQueueWithoutTxHash(RLPList rlpList, NetworkParameters networkParameters) {
        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();

        int n = rlpList.size() / 2;
        for (int k = 0; k < n; k++) {
            byte[] addressBytes = rlpList.get(k * 2).getRLPData();
            Address address = new Address(networkParameters, addressBytes);
            long amount = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 2 + 1).getRLPData()).longValue();

            entries.add(new ReleaseRequestQueue.Entry(address, Coin.valueOf(amount), null));
        }

        return entries;
    }

    // For the serialization format, see BridgeSerializationUtils::serializeReleaseRequestQueue
    private static List<ReleaseRequestQueue.Entry> deserializeReleaseRequestQueueWithTxHash(RLPList rlpList, NetworkParameters networkParameters) {
        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();

        int n = rlpList.size() / 3;
        for (int k = 0; k < n; k++) {
            byte[] addressBytes = rlpList.get(k * 3).getRLPData();
            Address address = new Address(networkParameters, addressBytes);
            long amount = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 3 + 1).getRLPData()).longValue();
            Keccak256 txHash = new Keccak256(rlpList.get(k * 3 + 2).getRLPData());

            entries.add(new ReleaseRequestQueue.Entry(address, Coin.valueOf(amount), txHash));
        }

        return entries;
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
        List<ReleaseTransactionSet.Entry> entries = set.getEntriesWithoutHash().stream().collect(Collectors.toList());
        entries.sort(ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR);

        byte[][] bytes = new byte[entries.size() * 2][];
        int n = 0;

        for (ReleaseTransactionSet.Entry entry : entries) {
            bytes[n++] = RLP.encodeElement(entry.getTransaction().bitcoinSerialize());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getRskBlockNumber()));
        }

        return RLP.encodeList(bytes);
    }

    public static byte[] serializeReleaseTransactionSetWithTxHash(ReleaseTransactionSet set) {
        List<ReleaseTransactionSet.Entry> entries = new ArrayList<>(set.getEntriesWithHash());
        entries.sort(ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR);

        byte[][] bytes = new byte[entries.size() * 3][];
        int n = 0;

        for (ReleaseTransactionSet.Entry entry : entries) {
            bytes[n++] = RLP.encodeElement(entry.getTransaction().bitcoinSerialize());
            bytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getRskBlockNumber()));
            bytes[n++] = RLP.encodeElement(entry.getRskTxHash().getBytes());
        }

        return RLP.encodeList(bytes);
    }

    public static ReleaseTransactionSet deserializeReleaseTransactionSet(byte[] data, NetworkParameters networkParameters) {
        return deserializeReleaseTransactionSet(data, networkParameters, false);
    }

    public static ReleaseTransactionSet deserializeReleaseTransactionSet(byte[] data, NetworkParameters networkParameters, boolean hasTxHash) {
        if (data == null || data.length == 0) {
            return new ReleaseTransactionSet(new HashSet<>());
        }

        int elementsMultipleCount = hasTxHash ? 3 : 2;
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        // Must have an even number of items
        if (rlpList.size() % elementsMultipleCount != 0) {
            throw new RuntimeException(String.format("Invalid serialized ReleaseTransactionSet. Expected a multiple of %d number of elements, but got %d", elementsMultipleCount, rlpList.size()));
        }

        return hasTxHash ? deserializeReleaseTransactionSetWithTxHash(rlpList, networkParameters) : deserializeReleaseTransactionSetWithoutTxHash(rlpList, networkParameters);
    }

    // For the serialization format, see BridgeSerializationUtils::serializeReleaseTransactionSet
    private static ReleaseTransactionSet deserializeReleaseTransactionSetWithoutTxHash(RLPList rlpList, NetworkParameters networkParameters) {
        Set<ReleaseTransactionSet.Entry> entries = new HashSet<>();

        int n = rlpList.size() / 2;
        for (int k = 0; k < n; k++) {
            byte[] txPayload = rlpList.get(k * 2).getRLPData();
            BtcTransaction tx =  new BtcTransaction(networkParameters, txPayload);

            long height = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 2 + 1).getRLPData()).longValue();

            entries.add(new ReleaseTransactionSet.Entry(tx, height));
        }

        return new ReleaseTransactionSet(entries);
    }

    private static ReleaseTransactionSet deserializeReleaseTransactionSetWithTxHash(RLPList rlpList, NetworkParameters networkParameters) {
        Set<ReleaseTransactionSet.Entry> entries = new HashSet<>();

        int n = rlpList.size() / 3;
        for (int k = 0; k < n; k++) {
            byte[] txPayload = rlpList.get(k * 3).getRLPData();
            BtcTransaction tx =  new BtcTransaction(networkParameters, txPayload);

            long height = BigIntegers.fromUnsignedByteArray(rlpList.get(k * 3 + 1).getRLPData()).longValue();
            Keccak256 rskTxHash = new Keccak256(rlpList.get(k * 3 + 2).getRLPData());

            entries.add(new ReleaseTransactionSet.Entry(tx, height, rskTxHash));
        }

        return new ReleaseTransactionSet(entries);
    }

    public static byte[] serializeInteger(Integer value) {
        return RLP.encodeBigInteger(BigInteger.valueOf(value));
    }

    public static Integer deserializeInteger(byte[] data) {
        return RLP.decodeBigInteger(data, 0).intValue();
    }

    public static byte[] serializeLong(long value) { return RLP.encodeBigInteger(BigInteger.valueOf(value)); }

    public static Optional<Long> deserializeOptionalLong(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(RLP.decodeBigInteger(data, 0).longValue());
    }

    public static CoinbaseInformation deserializeCoinbaseInformation(byte[] data) {
        if (data == null) {
            return null;
        }
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        if (rlpList.size() != 1) {
            throw new RuntimeException(String.format("Invalid serialized coinbase information, expected 1 value but got %d", rlpList.size()));
        }

        Sha256Hash witnessMerkleRoot = Sha256Hash.wrap(rlpList.get(0).getRLPData());

        return new CoinbaseInformation(witnessMerkleRoot);
    }

    public static byte[] serializeCoinbaseInformation(CoinbaseInformation coinbaseInformation) {
        if (coinbaseInformation == null) {
            return null;
        }
        byte[][] rlpElements = new byte[1][];
        rlpElements[0] = RLP.encodeElement(coinbaseInformation.getWitnessMerkleRoot().getBytes());
        return RLP.encodeList(rlpElements);
    }

    public static FastBridgeFederationInformation deserializeFastBridgeInformation(byte[] data) {
        if ((data == null) || (data.length == 0)) {
            throw new RuntimeException("Invalid serialized Fast Bridge Federation: null data or no data to deserialize");
        }

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        if (rlpList.size() != 2) {
            throw new RuntimeException(String.format("Invalid serialized Fast Bridge Federation: expected 2 value but got %d", rlpList.size()));
        }
        Sha256Hash derivationHash = Sha256Hash.wrap(rlpList.get(0).getRLPData());
        byte[] federationP2SH = rlpList.get(1).getRLPData();

        return new FastBridgeFederationInformation(derivationHash, federationP2SH);
    }

    public static byte[] serializeFastBridgeInformation(FastBridgeFederationInformation fastBridgeFederationP2SH) {
        if (fastBridgeFederationP2SH == null) {
            return new byte[]{};
        }
        byte[][] rlpElements = new byte[2][];
        rlpElements[0] = RLP.encodeElement(fastBridgeFederationP2SH.getDerivationHash().getBytes());
        rlpElements[1] = RLP.encodeElement(fastBridgeFederationP2SH.getFederationScriptHash());

        return RLP.encodeList(rlpElements);
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
        RLPList rlpArguments = (RLPList)rlpList.get(1);
        byte[][] arguments = new byte[rlpArguments.size()][];

        for (int k = 0; k < rlpArguments.size(); k++) {
            arguments[k] = rlpArguments.get(k).getRLPData();
        }

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

        List<BtcECKey> keys = new ArrayList<>();

        for (int k = 0; k < rlpList.size(); k++) {
            RLPElement element = rlpList.get(k);
            BtcECKey key = BtcECKey.fromPublicOnly(element.getRLPData());
            keys.add(key);
        }

        return keys;
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

        List<RskAddress> addresses = new ArrayList<>();

        for (int k = 0; k < rlpList.size(); k++) {
            RLPElement element = rlpList.get(k);
            RskAddress address = new RskAddress(element.getRLPData());
            addresses.add(address);
        }

        return addresses;
    }
}
