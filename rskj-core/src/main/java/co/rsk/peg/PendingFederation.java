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
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Sha3Hash;
import org.ethereum.crypto.HashUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable representation of an RSK Pending Federation.
 * A pending federation is one that is being actively
 * voted by the current federation to potentially become
 * the new active federation.
 *
 * @author Ariel Mendelzon
 */
public final class PendingFederation {
    private static final int MIN_MEMBERS_REQUIRED = 2;

    private List<FederationMember> members;

    public PendingFederation(List<FederationMember> members) {
        // Sorting members ensures same order for members
        // Immutability provides protection against unwanted modification, thus making the Pending Federation instance
        // effectively immutable
        this.members = Collections.unmodifiableList(members.stream().sorted(FederationMember.BTC_RSK_PUBKEYS_COMPARATOR).collect(Collectors.toList()));
    }

    public List<FederationMember> getMembers() {
        // Safe to return instance since both the list and instances are immutable
        return members;
    }

    public List<BtcECKey> getBtcPublicKeys() {
        // Copy keys since we don't control immutability of BtcECKey(s)
        return members.stream().map(m -> m.getBtcPublicKey()).collect(Collectors.toList());
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
     * @param btcParams the bitcoin parameters for the new Federation
     * @return a Federation
     */
    public Federation buildFederation(Instant creationTime, long blockNumber, NetworkParameters btcParams) {
        if (!this.isComplete()) {
            throw new IllegalStateException("PendingFederation is incomplete");
        }

        return new Federation(
                members,
                creationTime,
                blockNumber,
                btcParams
        );
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

    public Sha3Hash getHash() {
        byte[] encoded = BridgeSerializationUtils.serializePendingFederation(this);
        return new Sha3Hash(HashUtil.sha3(encoded));
    }

    @Override
    public int hashCode() {
        // Can use java.util.Objects.hash since List<BtcECKey> has a
        // well-defined hashCode()
        return Objects.hash(getBtcPublicKeys());
    }
}
