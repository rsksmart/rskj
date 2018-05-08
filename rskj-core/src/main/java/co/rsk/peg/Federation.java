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
import org.ethereum.crypto.ECKey;
import org.ethereum.db.ByteArrayWrapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable representation of an RSK Federation in the context of
 * a specific BTC network.
 *
 * @author Ariel Mendelzon
 */
public class Federation {
    private final List<BtcECKey> publicKeys;
    private final List<ECKey> rskPublicKeys;
    private final Instant creationTime;
    private final long creationBlockNumber;
    private final NetworkParameters btcParams;

    private Script redeemScript;
    private Script p2shScript;
    private Address address;

    public Federation(List<BtcECKey> publicKeys, Instant creationTime, long creationBlockNumber,  NetworkParameters btcParams) {
        // Sorting public keys ensures same order of federators for same public keys
        // Immutability provides protection unless unwanted modification, thus making the Federation instance
        // effectively immutable
        this.publicKeys = Collections.unmodifiableList(publicKeys.stream().sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList()));
        // using this.publicKeys ensures order in rskPublicKeys
        this.rskPublicKeys = Collections.unmodifiableList(this.publicKeys.stream()
                .map(BtcECKey::getPubKey)
                .map(ECKey::fromPublicOnly)
                .collect(Collectors.toList()));
        this.creationTime = creationTime;
        this.creationBlockNumber = creationBlockNumber;
        this.btcParams = btcParams;
        // Calculated once on-demand
        this.redeemScript = null;
        this.p2shScript = null;
        this.address = null;
    }

    public List<BtcECKey> getPublicKeys() {
        return publicKeys;
    }

    public int getNumberOfSignaturesRequired() {
        return publicKeys.size() / 2 + 1;
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

    public Script getRedeemScript() {
        if (redeemScript == null) {
            redeemScript = ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getPublicKeys());
        }

        return redeemScript;
    }

    public Script getP2SHScript() {
        if (p2shScript == null) {
            p2shScript = ScriptBuilder.createP2SHOutputScript(getNumberOfSignaturesRequired(), getPublicKeys());
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
        return publicKeys.size();
    }

    public Integer getPublicKeyIndex(BtcECKey key) {
        for (int i = 0; i < publicKeys.size(); i++) {
            // note that this comparison doesn't take into account
            // key compression
            if (Arrays.equals(key.getPubKey(), publicKeys.get(i).getPubKey())) {
                return i;
            }
        }

        return null;
    }

    public boolean hasPublicKey(BtcECKey key) {
        return getPublicKeyIndex(key) != null;
    }

    public boolean hasMemberWithRskAddress(byte[] address) {
        return rskPublicKeys.stream()
                .anyMatch(k -> Arrays.equals(k.getAddress(), address));
    }

    @Override
    public String toString() {
        return String.format("%d of %d signatures federation", getNumberOfSignaturesRequired(), publicKeys.size());
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

        ByteArrayWrapper[] thisPublicKeys = this.getPublicKeys().stream()
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(k -> new ByteArrayWrapper(k.getPubKey()))
                .toArray(ByteArrayWrapper[]::new);
        ByteArrayWrapper[] otherPublicKeys = otherFederation.getPublicKeys().stream()
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .map(k -> new ByteArrayWrapper(k.getPubKey()))
                .toArray(ByteArrayWrapper[]::new);

        return this.getNumberOfSignaturesRequired() == otherFederation.getNumberOfSignaturesRequired() &&
                this.getSize() == otherFederation.getSize() &&
                this.getCreationTime().equals(otherFederation.getCreationTime()) &&
                this.creationBlockNumber == otherFederation.creationBlockNumber &&
                this.btcParams.equals(otherFederation.btcParams) &&
                Arrays.equals(thisPublicKeys, otherPublicKeys);
    }

    @Override
    public int hashCode() {
        // Can use java.util.Objects.hash since all of Instant, int and List<BtcECKey> have
        // well-defined hashCode()s
        return Objects.hash(
                getCreationTime(),
                this.creationBlockNumber,
                getNumberOfSignaturesRequired(),
                getPublicKeys()
        );
    }
}
