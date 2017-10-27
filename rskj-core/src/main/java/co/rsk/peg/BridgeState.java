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

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import org.apache.commons.collections4.map.HashedMap;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * DTO to send the contract state.
 * Not production code, just used for debugging.
 *
 * Created by mario on 27/09/2016.
 */
public class BridgeState {
    private final int btcBlockchainBestChainHeight;
    private final Map<Sha256Hash, Long> btcTxHashesAlreadyProcessed;
    private final List<UTXO> activeFederationBtcUTXOs;
    private final SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForConfirmations;
    private final SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures;


    private static final NetworkParameters params =RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

    private BridgeState(int btcBlockchainBestChainHeight, Map<Sha256Hash, Long> btcTxHashesAlreadyProcessed, List<UTXO> activeFederationBtcUTXOs,
                       SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForConfirmations, SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures) {
        this.btcBlockchainBestChainHeight = btcBlockchainBestChainHeight;
        this.btcTxHashesAlreadyProcessed = btcTxHashesAlreadyProcessed;
        this.activeFederationBtcUTXOs = activeFederationBtcUTXOs;
        this.rskTxsWaitingForConfirmations = rskTxsWaitingForConfirmations;
        this.rskTxsWaitingForSignatures = rskTxsWaitingForSignatures;
    }

    public BridgeState(int btcBlockchainBestChainHeight, BridgeStorageProvider provider) throws IOException {
        this.btcBlockchainBestChainHeight = btcBlockchainBestChainHeight;
        this.btcTxHashesAlreadyProcessed = provider.getBtcTxHashesAlreadyProcessed();
        this.activeFederationBtcUTXOs = provider.getActiveFederationBtcUTXOs();
        this.rskTxsWaitingForConfirmations = provider.getRskTxsWaitingForConfirmations();
        this.rskTxsWaitingForSignatures = provider.getRskTxsWaitingForSignatures();
    }

    public int getBtcBlockchainBestChainHeight() {
        return this.btcBlockchainBestChainHeight;
    }

    public Map<Sha256Hash, Long> getBtcTxHashesAlreadyProcessed() {
        return btcTxHashesAlreadyProcessed;
    }

    public List<UTXO> getActiveFederationBtcUTXOs() {
        return activeFederationBtcUTXOs;
    }

    public SortedMap<Sha3Hash, BtcTransaction> getRskTxsWaitingForConfirmations() {
        return rskTxsWaitingForConfirmations;
    }

    public SortedMap<Sha3Hash, BtcTransaction> getRskTxsWaitingForSignatures() {
        return rskTxsWaitingForSignatures;
    }

    @Override
    public String toString() {
        return "StateForDebugging{" + "\n" +
                "btcBlockchainBestChainHeight=" + btcBlockchainBestChainHeight + "\n" +
                ", btcTxHashesAlreadyProcessed=" + btcTxHashesAlreadyProcessed + "\n" +
                ", activeFederationBtcUTXOs=" + activeFederationBtcUTXOs + "\n" +
                ", rskTxsWaitingForConfirmations=" + rskTxsWaitingForConfirmations + "\n" +
                ", rskTxsWaitingForSignatures=" + rskTxsWaitingForSignatures + "\n" +
                '}';
    }

    public List<String> formatedAlreadyProcessedHashes() {
        List<String> hashes = new ArrayList<>();
        if(this.btcTxHashesAlreadyProcessed != null) {
            this.btcTxHashesAlreadyProcessed.keySet().forEach(s -> hashes.add(s.toString()));
        }
        return hashes;
    }

    public Map<String, Object> stateToMap() {
        Map<String, Object> result = new HashedMap<>();
        result.put("btcTxHashesAlreadyProcessed", this.formatedAlreadyProcessedHashes());
        result.put("rskTxsWaitingForConfirmations", this.toStringList(rskTxsWaitingForConfirmations.keySet()));
        result.put("rskTxsWaitingForSignatures", this.toStringList(rskTxsWaitingForSignatures.keySet()));
        result.put("btcBlockchainBestChainHeight", this.btcBlockchainBestChainHeight);
        return result;
    }

    public byte[] getEncoded() throws IOException {
        byte[] rlpBtcBlockchainBestChainHeight = RLP.encodeBigInteger(BigInteger.valueOf(this.btcBlockchainBestChainHeight));
        byte[] rlpBtcTxHashesAlreadyProcessed = RLP.encodeElement(BridgeSerializationUtils.serializeMapOfHashesToLong(btcTxHashesAlreadyProcessed));
        byte[] rlpActiveFederationBtcUTXOs = RLP.encodeElement(BridgeSerializationUtils.serializeList(activeFederationBtcUTXOs));
        byte[] rlpRskTxsWaitingForConfirmations = RLP.encodeElement(BridgeSerializationUtils.serializeMap(rskTxsWaitingForConfirmations));
        byte[] rlpRskTxsWaitingForSignatures = RLP.encodeElement(BridgeSerializationUtils.serializeMap(rskTxsWaitingForSignatures));

        return RLP.encodeList(rlpBtcBlockchainBestChainHeight, rlpBtcTxHashesAlreadyProcessed, rlpActiveFederationBtcUTXOs, rlpRskTxsWaitingForConfirmations, rlpRskTxsWaitingForSignatures);
    }

    public static BridgeState create(byte[] data) throws IOException {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        byte[] btcBlockchainBestChainHeightBytes = rlpList.get(0).getRLPData();
        int btcBlockchainBestChainHeight = btcBlockchainBestChainHeightBytes == null ? 0 : (new BigInteger(1, btcBlockchainBestChainHeightBytes)).intValue();
        byte[] btcTxHashesAlreadyProcessedBytes = rlpList.get(1).getRLPData();
        Map<Sha256Hash, Long> btcTxHashesAlreadyProcessed = BridgeSerializationUtils.deserializeMapOfHashesToLong(btcTxHashesAlreadyProcessedBytes);
        byte[] btcUTXOsBytes = rlpList.get(2).getRLPData();
        List<UTXO> btcUTXOs = BridgeSerializationUtils.deserializeList(btcUTXOsBytes);
        byte[] rskTxsWaitingForConfirmationsBytes = rlpList.get(3).getRLPData();
        SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForConfirmations = BridgeSerializationUtils.deserializeMap(rskTxsWaitingForConfirmationsBytes, params, true);
        byte[] rskTxsWaitingForSignaturesBytes = rlpList.get(4).getRLPData();
        SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures = BridgeSerializationUtils.deserializeMap(rskTxsWaitingForSignaturesBytes, params, false);

        return new BridgeState(btcBlockchainBestChainHeight, btcTxHashesAlreadyProcessed, btcUTXOs, rskTxsWaitingForConfirmations, rskTxsWaitingForSignatures);
    }

    private List<String> toStringList(Set<Sha3Hash> keys) {
        List<String> hashes = new ArrayList<>();
        if(keys != null) {
            keys.forEach(s -> hashes.add(s.toString()));
        }
        return hashes;
    }
}
