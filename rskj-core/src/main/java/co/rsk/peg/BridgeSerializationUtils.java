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

import co.rsk.crypto.Sha3Hash;
import org.apache.commons.lang3.tuple.Pair;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.UTXO;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;

/**
 * Created by mario on 20/04/17.
 */
public class BridgeSerializationUtils {

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

    public static byte[] serializeList(List<UTXO> list) throws IOException {
        int nutxos = list.size();

        byte[][] bytes = new byte[nutxos][];
        int n = 0;

        for (UTXO utxo : list) {
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            utxo.serializeToStream(ostream);
            ostream.close();
            bytes[n++] = RLP.encodeElement(ostream.toByteArray());
        }

        return RLP.encodeList(bytes);
    }

    public static SortedMap<Sha3Hash, BtcTransaction> deserializeMap(byte[] data, NetworkParameters networkParameters, boolean noInputsTxs) {
        SortedMap<Sha3Hash, BtcTransaction> map = new TreeMap<>();

        if (data == null || data.length == 0)
            return map;

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

        if (data == null || data.length == 0)
            return map;

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

    public static List<UTXO> deserializeList(byte[] data) throws IOException {
        List<UTXO> list = new ArrayList<>();

        if (data == null || data.length == 0)
            return list;

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

        for (Sha256Hash hash : set)
            bytes[n++] = RLP.encodeElement(hash.getBytes());

        return RLP.encodeList(bytes);
    }

    public static SortedSet<Sha256Hash> deserializeSet(byte[] data) {
        SortedSet<Sha256Hash> set = new TreeSet<>();

        if (data == null || data.length == 0)
            return set;

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        int nhashes = rlpList.size();

        for (int k = 0; k < nhashes; k++)
            set.add(Sha256Hash.wrap(rlpList.get(k).getRLPData()));

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

        if (data == null || data.length == 0)
            return map;

        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        // List size must be even - key, value pairs expected in sequence
        if (rlpList.size() % 2 != 0) {
            return map;
        }

        int numEntries = rlpList.size() / 2;

        for (int k = 0; k < numEntries; k++) {
            Sha256Hash hash = Sha256Hash.wrap(rlpList.get(k*2).getRLPData());
            Long number = new BigInteger(rlpList.get(k*2 + 1).getRLPData()).longValue();
            map.put(hash, number);
        }

        return map;
    }
}
