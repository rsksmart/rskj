/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.util.MaxSizeHashMap;
import co.rsk.util.StringUtils;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of an RSK Federation member.
 *
 * It's composed of three public keys: one for the RSK network, one for the
 * BTC network and one called MST of yet undefined usage.
 *
 * @author Ariel Mendelzon
 */
public final class FederationMember {
    public static final FederationMemberPubKeysComparator BTC_RSK_MST_PUBKEYS_COMPARATOR = new FederationMemberPubKeysComparator();
    private static final int KEYS_QUANTITY = 3;
    private static final int BTC_KEY_INDEX = 0;
    private static final int RSK_KEY_INDEX = 1;
    private static final int MST_KEY_INDEX = 2;
    private final BtcECKey btcPublicKey;
    private final ECKey rskPublicKey;
    private final ECKey mstPublicKey;

    /**
     * Building a member decompresses each of its public keys, and decompression is a modular square
     * root over the secp256k1 field. From the first federation change onwards the active federation
     * is read back from storage, so REMASC made the node re-deserialize it - and re-decompress every
     * federator key - for every block it imported. The federation is a handful of keys that never
     * change, so the results are memoised.
     *
     * <p>Keyed on the encoding handed in, which is the raw stored bytes and costs nothing to read.
     * The two directions are kept in separate maps: the same bytes mean "normalise this key to its
     * compressed form" in one and "decode exactly these bytes" in the other, and for an uncompressed
     * input those are different keys.
     */
    private static final int KEY_CACHE_SIZE = 2048;
    private static final Map<ByteArrayWrapper, BtcECKey> COMPRESSED_BTC_KEYS =
            Collections.synchronizedMap(new MaxSizeHashMap<>(KEY_CACHE_SIZE, true));
    private static final Map<ByteArrayWrapper, ECKey> COMPRESSED_RSK_KEYS =
            Collections.synchronizedMap(new MaxSizeHashMap<>(KEY_CACHE_SIZE, true));
    private static final Map<ByteArrayWrapper, BtcECKey> DECODED_BTC_KEYS =
            Collections.synchronizedMap(new MaxSizeHashMap<>(KEY_CACHE_SIZE, true));
    private static final Map<ByteArrayWrapper, ECKey> DECODED_RSK_KEYS =
            Collections.synchronizedMap(new MaxSizeHashMap<>(KEY_CACHE_SIZE, true));

    private static BtcECKey toCompressed(BtcECKey key) {
        return COMPRESSED_BTC_KEYS.computeIfAbsent(ByteUtil.wrap(key.getPubKey()),
                k -> BtcECKey.fromPublicOnly(key.getPubKeyPoint().getEncoded(true)));
    }

    private static ECKey toCompressed(ECKey key) {
        return COMPRESSED_RSK_KEYS.computeIfAbsent(ByteUtil.wrap(key.getPubKey()),
                k -> ECKey.fromPublicOnly(key.getPubKey(true)));
    }

    private static BtcECKey decodeBtcKey(byte[] encoded) {
        return DECODED_BTC_KEYS.computeIfAbsent(ByteUtil.wrap(encoded),
                k -> BtcECKey.fromPublicOnly(k.getData()));
    }

    private static ECKey decodeRskKey(byte[] encoded) {
        return DECODED_RSK_KEYS.computeIfAbsent(ByteUtil.wrap(encoded),
                k -> ECKey.fromPublicOnly(k.getData()));
    }

    public FederationMember(BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        // Copy public keys to ensure effective immutability
        // Make sure we always use compressed versions of public keys
        this.btcPublicKey = toCompressed(btcPublicKey);
        this.rskPublicKey = toCompressed(rskPublicKey);
        this.mstPublicKey = toCompressed(mstPublicKey);
    }

    public enum KeyType {
        BTC("btc"),
        RSK("rsk"),
        MST("mst");

        private final String value;

        KeyType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static KeyType byValue(String value) {
            return switch (value) {
                case "rsk" -> KeyType.RSK;
                case "mst" -> KeyType.MST;
                case "btc" -> KeyType.BTC;
                default ->
                    throw new IllegalArgumentException(String.format("Invalid value for FederationMember.KeyType: %s", StringUtils.trim(value)));
            };
        }
    }

    // To be removed when different keys per federation member feature is implemented. These are just helper
    // methods to make it easier w.r.t. compatibility with the current approach

    public static FederationMember getFederationMemberFromKey(BtcECKey pk) {
        ECKey ethKey = ECKey.fromPublicOnly(pk.getPubKey());
        return new FederationMember(pk, ethKey, ethKey);
    }

    public static List<FederationMember> getFederationMembersFromKeys(List<BtcECKey> pks) {
        return pks.stream().map(FederationMember::getFederationMemberFromKey).toList();
    }

    public BtcECKey getBtcPublicKey() {
        // The stored key is public-only and already normalised to its compressed form by the
        // constructor, so it is effectively immutable and can be shared directly. Rebuilding it with
        // fromPublicOnly() on every call forced a secp256k1 point decompression (a modular square
        // root), which REMASC triggered O(members^2) times per block.
        return btcPublicKey;
    }

    public ECKey getRskPublicKey() {
        return rskPublicKey;
    }

    public ECKey getMstPublicKey() {
        // Return a copy
        return ECKey.fromPublicOnly(mstPublicKey.getPubKey());
    }

    public ECKey getPublicKey(KeyType keyType) {
        return switch (keyType) {
            case RSK -> getRskPublicKey();
            case MST -> getMstPublicKey();
            default -> ECKey.fromPublicOnly(btcPublicKey.getPubKey());
        };
    }

    @Override
    public String toString() {
        return String.format(
            "<BTC-%s, RSK-%s, MST-%s> federation member",
            ByteUtil.toHexString(btcPublicKey.getPubKey()),
            ByteUtil.toHexString(rskPublicKey.getPubKey()),
            ByteUtil.toHexString(mstPublicKey.getPubKey())
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        FederationMember otherFederationMember = (FederationMember) other;

        return Arrays.equals(btcPublicKey.getPubKey(), otherFederationMember.btcPublicKey.getPubKey()) &&
                Arrays.equals(rskPublicKey.getPubKey(), otherFederationMember.rskPublicKey.getPubKey()) &&
                Arrays.equals(mstPublicKey.getPubKey(), otherFederationMember.mstPublicKey.getPubKey());
    }

    @Override
    public int hashCode() {
        // Can use java.util.Objects.hash since both BtcECKey and ECKey have
        // well-defined hashCode(s).
        return Objects.hash(
            btcPublicKey,
            rskPublicKey,
            mstPublicKey
        );
    }

    public byte[] serialize() {
        byte[][] rlpElements = new byte[KEYS_QUANTITY][];
        byte[] btcPublicKeyBytes = this.getBtcPublicKey().getPubKeyPoint().getEncoded(true);
        byte[] rskPublicKeyBytes = this.getRskPublicKey().getPubKey(true);
        byte[] mstPublicKeyBytes = this.getMstPublicKey().getPubKey(true);

        rlpElements[BTC_KEY_INDEX] = RLP.encodeElement(btcPublicKeyBytes);
        rlpElements[RSK_KEY_INDEX] = RLP.encodeElement(rskPublicKeyBytes);
        rlpElements[MST_KEY_INDEX] = RLP.encodeElement(mstPublicKeyBytes);
        return RLP.encodeList(rlpElements);
    }

    public static FederationMember deserialize(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);
        if (rlpList.size() != KEYS_QUANTITY) {
            throw new RuntimeException(String.format(
                "Invalid serialized FederationMember. Expected %d elements but got %d", KEYS_QUANTITY, rlpList.size())
            );
        }

        byte[] btcKeyData = rlpList.get(BTC_KEY_INDEX).getRLPData();
        byte[] rskKeyData = rlpList.get(RSK_KEY_INDEX).getRLPData();
        byte[] mstKeyData = rlpList.get(MST_KEY_INDEX).getRLPData();

        BtcECKey btcKey = decodeBtcKey(btcKeyData);
        ECKey rskKey = decodeRskKey(rskKeyData);
        ECKey mstKey = decodeRskKey(mstKeyData);

        return new FederationMember(btcKey, rskKey, mstKey);
    }
}
