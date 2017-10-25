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
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.program.Program;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    private int id;
    private int numberOfPublicKeysRequired;
    private int numberOfSignaturesRequired;
    private List<BtcECKey> publicKeys;

    public PendingFederation(int id, int numberOfSignaturesRequired, int numberOfPublicKeysRequired, List<BtcECKey> publicKeys) {
        this.id = id;
        this.numberOfSignaturesRequired = numberOfSignaturesRequired;
        this.numberOfPublicKeysRequired = numberOfPublicKeysRequired;
        // Sorting public keys ensures same order of federators for same public keys
        // Immutability provides protection unless unwanted modification, thus making the Pending Federation instance
        // effectively immutable
        this.publicKeys = Collections.unmodifiableList(publicKeys.stream().sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList()));
    }

    public int getId() {
        return id;
    }

    public List<BtcECKey> getPublicKeys() {
        return publicKeys;
    }

    public int getNumberOfSignaturesRequired() {
        return this.numberOfSignaturesRequired;
    }

    public int getNumberOfPublicKeysRequired() {
        return this.numberOfPublicKeysRequired;
    }

    public boolean isComplete() {
        return this.publicKeys.size() == this.numberOfPublicKeysRequired;
    }

    /**
     * Creates a new PendingFederation with the additional specified public key
     * @param key the new public key
     * @return a new PendingFederation with the added public key
     */
    public PendingFederation addPublicKey(BtcECKey key) {
        if (this.numberOfPublicKeysRequired == publicKeys.size()) {
            throw new IllegalStateException("PendingFederation is already complete");
        }

        List<BtcECKey> newKeys = new ArrayList<>(publicKeys);
        newKeys.add(key);
        return new PendingFederation(this.id, this.numberOfSignaturesRequired, this.numberOfPublicKeysRequired, newKeys);
    }

    /**
     * Creates a new PendingFederation without the specified public key
     * @param key the public key to remove
     * @return a new PendingFederation without the given public key
     */
    public PendingFederation removePublicKey(BtcECKey key) {
        if (!publicKeys.contains(key)) {
            throw new IllegalStateException("PendingFederation doesn't contain the given public key");
        }

        List<BtcECKey> newKeys = new ArrayList<>(publicKeys);
        newKeys.remove(key);
        return new PendingFederation(this.id, this.numberOfSignaturesRequired, this.numberOfPublicKeysRequired, newKeys);
    }

    public Federation buildFederation(Instant creationTime, NetworkParameters btcParams) {
        if (!this.isComplete()) {
            throw new IllegalStateException("PendingFederation is incomplete");
        }

        return new Federation(
                numberOfSignaturesRequired,
                publicKeys,
                creationTime,
                btcParams
        );
    }

    @Override
    public String toString() {
        return String.format("%d of %d signatures pending federation (%s)", numberOfSignaturesRequired, publicKeys.size(), isComplete() ? "complete" : "incomplete");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof PendingFederation) {
            PendingFederation otherFederation = (PendingFederation) other;
            ByteArrayWrapper[] thisPublicKeys = this.getPublicKeys().stream()
                    .sorted(BtcECKey.PUBKEY_COMPARATOR)
                    .map(k -> new ByteArrayWrapper(k.getPubKey()))
                    .toArray(ByteArrayWrapper[]::new);
            ByteArrayWrapper[] otherPublicKeys = otherFederation.getPublicKeys().stream()
                    .sorted(BtcECKey.PUBKEY_COMPARATOR)
                    .map(k -> new ByteArrayWrapper(k.getPubKey()))
                    .toArray(ByteArrayWrapper[]::new);

            return this.getId() == ((PendingFederation) other).getId() &&
                    this.getNumberOfSignaturesRequired() == otherFederation.getNumberOfSignaturesRequired() &&
                    this.getNumberOfPublicKeysRequired() == otherFederation.getNumberOfPublicKeysRequired() &&
                    this.getPublicKeys().size() == otherFederation.getPublicKeys().size() &&
                    Arrays.equals(thisPublicKeys, otherPublicKeys);
        }

        return false;
    }
}
