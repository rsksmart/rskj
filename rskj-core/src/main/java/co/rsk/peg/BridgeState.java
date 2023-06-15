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
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Keccak256;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import javax.annotation.Nullable;
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
    private final long nextPegoutCreationBlockNumber;
    private final List<UTXO> activeFederationBtcUTXOs;
    private final SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures;
    private final ReleaseRequestQueue releaseRequestQueue;
    private final PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations;
    private final ActivationConfig.ForBlock activations;

    private BridgeState(int btcBlockchainBestChainHeight,
                        long nextPegoutCreationBlockNumber,
                        List<UTXO> activeFederationBtcUTXOs,
                        SortedMap<Keccak256,
                        BtcTransaction> rskTxsWaitingForSignatures,
                        ReleaseRequestQueue releaseRequestQueue,
                        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations,
                        @Nullable ActivationConfig.ForBlock activations) {
        this.btcBlockchainBestChainHeight = btcBlockchainBestChainHeight;
        this.nextPegoutCreationBlockNumber = nextPegoutCreationBlockNumber;
        this.activeFederationBtcUTXOs = activeFederationBtcUTXOs;
        this.rskTxsWaitingForSignatures = rskTxsWaitingForSignatures;
        this.releaseRequestQueue = releaseRequestQueue;
        this.pegoutsWaitingForConfirmations = pegoutsWaitingForConfirmations;
        this.activations = activations;
    }

    public BridgeState(int btcBlockchainBestChainHeight, BridgeStorageProvider provider, ActivationConfig.ForBlock activations) throws IOException {
        this(btcBlockchainBestChainHeight,
                provider.getNextPegoutHeight().orElse(0L),
                provider.getNewFederationBtcUTXOs(),
                provider.getPegoutsWaitingForSignatures(),
                provider.getReleaseRequestQueue(),
                provider.getPegoutsWaitingForConfirmations(),
                activations);
    }

    public int getBtcBlockchainBestChainHeight() {
        return this.btcBlockchainBestChainHeight;
    }

    public List<UTXO> getActiveFederationBtcUTXOs() {
        return activeFederationBtcUTXOs;
    }

    public SortedMap<Keccak256, BtcTransaction> getRskTxsWaitingForSignatures() {
        return rskTxsWaitingForSignatures;
    }

    public ReleaseRequestQueue getReleaseRequestQueue() {
        return releaseRequestQueue;
    }

    public PegoutsWaitingForConfirmations getPegoutsWaitingForConfirmations() {
        return pegoutsWaitingForConfirmations;
    }

    public long getNextPegoutCreationBlockNumber() {
        return nextPegoutCreationBlockNumber;
    }

    @Override
    public String toString() {
        return "StateForDebugging{" + "\n" +
                "btcBlockchainBestChainHeight=" + btcBlockchainBestChainHeight + "\n" +
                ", nextPegoutCreationBlockNumber=" + nextPegoutCreationBlockNumber + "\n" +
                ", activeFederationBtcUTXOs=" + activeFederationBtcUTXOs + "\n" +
                ", rskTxsWaitingForSignatures=" + rskTxsWaitingForSignatures + "\n" +
                ", releaseRequestQueue=" + releaseRequestQueue + "\n" +
                ", pegoutsWaitingForConfirmations=" + pegoutsWaitingForConfirmations + "\n" +
                '}';
    }

    public Map<String, Object> stateToMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("rskTxsWaitingForSignatures", this.toStringList(rskTxsWaitingForSignatures.keySet()));
        result.put("btcBlockchainBestChainHeight", this.btcBlockchainBestChainHeight);
        return result;
    }

    public byte[] getEncoded() throws IOException {
        byte[] rlpBtcBlockchainBestChainHeight = RLP.encodeBigInteger(BigInteger.valueOf(this.btcBlockchainBestChainHeight));
        byte[] rlpActiveFederationBtcUTXOs = RLP.encodeElement(BridgeSerializationUtils.serializeUTXOList(activeFederationBtcUTXOs));
        byte[] rlpRskTxsWaitingForSignatures = RLP.encodeElement(BridgeSerializationUtils.serializeMap(rskTxsWaitingForSignatures));
        byte[] serializedReleaseRequestQueue = shouldUsePapyrusEncoding(this.activations) ?
                BridgeSerializationUtils.serializeReleaseRequestQueueWithTxHash(releaseRequestQueue):
                BridgeSerializationUtils.serializeReleaseRequestQueue(releaseRequestQueue);
        byte[] rlpReleaseRequestQueue = RLP.encodeElement(serializedReleaseRequestQueue);
        byte[] serializedPegoutWaitingForConfirmations = shouldUsePapyrusEncoding(this.activations) ?
                BridgeSerializationUtils.serializePegoutsWaitingForConfirmationsWithTxHash(pegoutsWaitingForConfirmations):
                BridgeSerializationUtils.serializePegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations);
        byte[] rlpRPegoutWaitingForConfirmations = RLP.encodeElement(serializedPegoutWaitingForConfirmations);
        byte[] rlpNextPegoutCreationBlockNumber = RLP.encodeElement(BridgeSerializationUtils.serializeLong(nextPegoutCreationBlockNumber));

        return RLP.encodeList(rlpBtcBlockchainBestChainHeight, rlpActiveFederationBtcUTXOs, rlpRskTxsWaitingForSignatures, rlpReleaseRequestQueue, rlpRPegoutWaitingForConfirmations, rlpNextPegoutCreationBlockNumber);
    }

    public static BridgeState create(BridgeConstants bridgeConstants, byte[] data, @Nullable ActivationConfig.ForBlock activations) throws IOException {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        byte[] btcBlockchainBestChainHeightBytes = rlpList.get(0).getRLPData();
        int btcBlockchainBestChainHeight = btcBlockchainBestChainHeightBytes == null ? 0 : (new BigInteger(1, btcBlockchainBestChainHeightBytes)).intValue();
        byte[] btcUTXOsBytes = rlpList.get(1).getRLPData();
        List<UTXO> btcUTXOs = BridgeSerializationUtils.deserializeUTXOList(btcUTXOsBytes);
        byte[] rskTxsWaitingForSignaturesBytes = rlpList.get(2).getRLPData();
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = BridgeSerializationUtils.deserializeMap(rskTxsWaitingForSignaturesBytes, bridgeConstants.getBtcParams(), false);
        byte[] releaseRequestQueueBytes = rlpList.get(3).getRLPData();
        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(BridgeSerializationUtils.deserializeReleaseRequestQueue(releaseRequestQueueBytes, bridgeConstants.getBtcParams(), shouldUsePapyrusEncoding(activations)));
        byte[] pegoutsWaitingForConfirmationsBytes = rlpList.get(4).getRLPData();
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(pegoutsWaitingForConfirmationsBytes, bridgeConstants.getBtcParams(), shouldUsePapyrusEncoding(activations));
        byte[] nextPegoutCreationBlockNumberBytes = rlpList.get(5).getRLPData();
        long nextPegoutCreationBlockNumber = BridgeSerializationUtils.deserializeOptionalLong(nextPegoutCreationBlockNumberBytes).orElse(0L);

        return new BridgeState(
                btcBlockchainBestChainHeight,
                nextPegoutCreationBlockNumber,
                btcUTXOs,
                rskTxsWaitingForSignatures,
                releaseRequestQueue,
            pegoutsWaitingForConfirmations,
                activations
        );
    }

    private List<String> toStringList(Set<Keccak256> keys) {
        List<String> hashes = new ArrayList<>();
        if(keys != null) {
            keys.forEach(s -> hashes.add(s.toHexString()));
        }

        return hashes;
    }

    public static boolean shouldUsePapyrusEncoding(ActivationConfig.ForBlock activations) {
        return activations != null && activations.isActive(ConsensusRule.RSKIP146);
    }
}
