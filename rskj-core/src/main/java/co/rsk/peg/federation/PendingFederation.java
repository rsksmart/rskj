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

package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Keccak256;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable representation of an RSK Pending Federation.
 * A pending federation is one that is being actively
 * voted by the current federation to potentially become
 * the new active federation.
 *
 * @author Ariel Mendelzon
 */
public final class PendingFederation {
    private static final Logger logger = LoggerFactory.getLogger("PendingFederation");
    private static final int MIN_MEMBERS_REQUIRED = 2;
    private final List<FederationMember> members;

    public PendingFederation(List<FederationMember> members) {
        // Sorting members ensures same order for members
        // Immutability provides protection against unwanted modification, thus making the Pending Federation instance
        // effectively immutable
        this.members = Collections.unmodifiableList(
            members.stream()
                .sorted(FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR)
                .collect(Collectors.toList())
        );
    }

    public List<FederationMember> getMembers() {
        // Safe to return instance since both the list and instances are immutable
        return members;
    }

    public List<BtcECKey> getBtcPublicKeys() {
        // Copy keys since we don't control immutability of BtcECKey(s)
        return members.stream()
            .map(FederationMember::getBtcPublicKey)
            .collect(Collectors.toList());
    }

    public boolean isComplete() {
        return this.members.size() >= MIN_MEMBERS_REQUIRED;
    }

    /**
     * Creates a new PendingFederation with the additional specified member
     * @param member the new federation member
     * @return a new PendingFederation with the added member
     */
    public PendingFederation addMember(FederationMember member) {
        List<FederationMember> newMembers = new ArrayList<>(members);
        newMembers.add(member);
        return new PendingFederation(newMembers);
    }

    /**
     * Builds a Federation from this PendingFederation
     * @param creationTime the creation time for the new Federation
     * @param bridgeConstants to get the bitcoin parameters for the new Federation,
     * and the keys for creating an ERP Federation
     * @param activations Activation configuration to check hard fork
     * @return a Federation
     */
    public Federation buildFederation(
        Instant creationTime,
        long blockNumber,
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations
        ) {
        if (!this.isComplete()) {
            throw new IllegalStateException("PendingFederation is incomplete");
        }

        NetworkParameters btcParams = bridgeConstants.getBtcParams();
        FederationArgs federationArgs = new FederationArgs(members, creationTime, blockNumber, btcParams);
        if (!activations.isActive(ConsensusRule.RSKIP201)){
            return FederationFactory.buildStandardMultiSigFederation(federationArgs);
        }

        List<BtcECKey> erpPubKeys = bridgeConstants.getErpFedPubKeysList();
        long activationDelay = bridgeConstants.getErpFedActivationDelay();
        ErpFederationArgs erpFederationArgs = new ErpFederationArgs(members, creationTime, blockNumber, btcParams, erpPubKeys, activationDelay);
        if (!activations.isActive(ConsensusRule.RSKIP353)) {
            logger.info("[buildFederation] Going to create an ERP Federation");
            return FederationFactory.buildNonStandardErpFederation(erpFederationArgs, activations);
        }
        logger.info("[buildFederation] Going to create a P2SH ERP Federation");
        return FederationFactory.buildP2shErpFederation(erpFederationArgs);
    }

    @Override
    public String toString() {
        return String.format("%d signatures pending federation (%s)", members.size(), isComplete() ? "complete" : "incomplete");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        return this.members.equals(((PendingFederation) other).members);
    }

    public Keccak256 getHash() {
        byte[] encoded = this.serializeOnlyBtcKeys();
        return new Keccak256(HashUtil.keccak256(encoded));
    }

    @Override
    public int hashCode() {
        // Can use java.util.Objects.hash since List<BtcECKey> has a
        // well-defined hashCode()
        return Objects.hash(getBtcPublicKeys());
    }

    public byte[] serialize(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP123)) {
            return serializeOnlyBtcKeys();
        }
        return serializeFromMembers();
    }

    public static PendingFederation deserialize(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);
        List<FederationMember> deserializedMembers = new ArrayList<>();

        for (int k = 0; k < rlpList.size(); k++) {
            RLPElement element = rlpList.get(k);
            FederationMember member = FederationMember.deserialize(element.getRLPData());
            deserializedMembers.add(member);
        }

        return new PendingFederation(deserializedMembers);
    }

    /**
     * A pending federation is serialized as the
     * list of its sorted members serialized.
     * A FederationMember is serialized as a list in the following order:
     * - BTC public key
     * - RSK public key
     * - MST public key
     * All keys are stored in their COMPRESSED versions.
     */
    private byte[] serializeFromMembers() {
        List<byte[]> encodedMembers = this.getMembers().stream()
            .sorted(FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR)
            .map(FederationMember::serialize)
            .collect(Collectors.toList());
        return RLP.encodeList(encodedMembers.toArray(new byte[0][]));
    }

    /**
     * A pending federation is serialized as the
     * public keys conforming it.
     * A list of btc public keys is serialized as
     * [pubkey1, pubkey2, ..., pubkeyn], sorted
     * using the lexicographical order of the public keys
     * (see BtcECKey.PUBKEY_COMPARATOR).
     * This is a legacy format for blocks before the Wasabi
     * network upgrade.
     */
    private byte[] serializeOnlyBtcKeys() {
        List<byte[]> encodedKeys = this.getBtcPublicKeys().stream()
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .map(key -> RLP.encodeElement(key.getPubKey()))
            .collect(Collectors.toList());
        return RLP.encodeList(encodedKeys.toArray(new byte[0][]));
    }

    public static PendingFederation deserializeOnlyBtcKeys(byte[] data) {
        // BTC, RSK and MST keys are the same
        List<FederationMember> deserializedMembers = deserializeBtcPublicKeys(data).stream()
            .map(FederationMember::getFederationMemberFromKey)
            .collect(Collectors.toList());

        return new PendingFederation(deserializedMembers);
    }

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
}
