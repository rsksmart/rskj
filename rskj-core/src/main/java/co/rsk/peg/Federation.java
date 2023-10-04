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

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable representation of an RSK Federation in the context of
 * a specific BTC network.
 *
 */

public abstract class Federation {
    protected final List<FederationMember> members;
    protected final Instant creationTime;
    protected final long creationBlockNumber;
    protected final NetworkParameters btcParams;

    protected Script redeemScript;
    protected Script p2shScript;
    protected Address address;

    protected Federation(List<FederationMember> members, Instant creationTime, long creationBlockNumber, NetworkParameters btcParams) {
        // Sorting members ensures same order of federation members for same members
        // Immutability provides protection against unwanted modification, thus making the Federation instance
        // effectively immutable
        this.members = Collections.unmodifiableList(members.stream().sorted(FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR).collect(Collectors.toList()));

        this.creationTime = creationTime.truncatedTo(ChronoUnit.MILLIS);
        this.creationBlockNumber = creationBlockNumber;
        this.btcParams = btcParams;
    }

    public List<FederationMember> getMembers() {
        // Safe to return members since
        // both list and instances are immutable
        return members;
    }

    public List<BtcECKey> getBtcPublicKeys() {
        // Copy instances since we don't control
        // immutability of BtcECKey instances
        return members.stream()
            .map(m -> m.getBtcPublicKey().getPubKey())
            .map(BtcECKey::fromPublicOnly)
            .collect(Collectors.toList());
    }

    public int getNumberOfSignaturesRequired() {
        return members.size() / 2 +1 ;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public NetworkParameters getBtcParams() {
        return btcParams;
    }

    public long getCreationBlockNumber() {
        return creationBlockNumber;
    }

    public abstract Script getRedeemScript();
    public Script getP2SHScript() {
        if (p2shScript == null) {
            p2shScript = ScriptBuilder.createP2SHOutputScript(getRedeemScript());
        }

        return p2shScript;
    }

    public Address getAddress() {
        if (address == null) {
            address = Address.fromP2SHScript(btcParams, getP2SHScript());
        }

        return address;
    }

    public int getSize() {
        return members.size();
    }

    public Integer getBtcPublicKeyIndex(BtcECKey key) {
        for (int i = 0; i < members.size(); i++) {
            // note that this comparison doesn't take into account
            // key compression
            if (Arrays.equals(key.getPubKey(), members.get(i).getBtcPublicKey().getPubKey())) {
                return i;
            }
        }

        return null;
    }

    public boolean hasBtcPublicKey(BtcECKey key) {
        return getBtcPublicKeyIndex(key) != null;
    }

    public boolean hasMemberWithRskAddress(byte[] address) {
        return members.stream()
            .anyMatch(m -> Arrays.equals(m.getRskPublicKey().getAddress(), address));
    }

    public boolean isMember(FederationMember federationMember){
        return this.members.contains(federationMember);
    }

    @Override
    public String toString() {
        return String.format("Got %d of %d signatures federation with address %s", getNumberOfSignaturesRequired(), members.size(), getAddress());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        Federation otherFederation = (Federation) other;
        return this.getAddress().equals(otherFederation.getAddress());
    }
}
