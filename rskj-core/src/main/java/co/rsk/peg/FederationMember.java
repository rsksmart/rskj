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

package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import com.google.common.primitives.UnsignedBytes;
import org.ethereum.crypto.ECKey;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable representation of an RSK Federation member.
 *
 * It's composed of two public keys: one for the RSK and one for the
 * BTC network.
 *
 * @author Ariel Mendelzon
 */
public final class FederationMember {
    private final BtcECKey btcPublicKey;
    private final ECKey rskPublicKey;

    // To be removed when different keys per federation member feature is implemented. This is just a helper
    // method to make it easier w.r.t. compatibility with the current approach
    public static List<FederationMember> getFederationMembersFromKeys(List<BtcECKey> pks) {
        return pks.stream().map(pk ->
                new FederationMember(pk, ECKey.fromPublicOnly(pk.getPubKey()))
        ).collect(Collectors.toList());
    }

    /**
     * Compares federation members based on their underlying keys.
     *
     * The total ordering is defined such that, for any two members M1, M2,
     * 1) M1 < M2 iif BTC_PUB_KEY(M1) <lex BTC_PUB_KEY(M2) OR
     *              (BTC_PUB_KEY(M1) ==lex BTC_PUB_KEY(M2) AND
     *               RSK_PUB_KEY(M1) <lex RSK_PUB_KEY(M2))
     * 2) M1 == M2 iff BTC_PUB_KEY(M1) ==lex BTC_PUB_KEY(M2) AND RSK_PUB_KEY(M1) ==lex RSK_PUB_KEY(M2)
     * 3) M1 > M2 otherwise
     *
     * where <lex and ==lex is given by negative and zero values (resp.) of the
     * UnsignedBytes.lexicographicalComparator() comparator.
     */
    public static final Comparator<FederationMember> BTC_RSK_PUBKEYS_COMPARATOR = new Comparator<FederationMember>() {
        private Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        @Override
        public int compare(FederationMember m1, FederationMember m2) {
            int btcKeysComparison = comparator.compare(m1.getBtcPublicKey().getPubKey(), m2.getBtcPublicKey().getPubKey());
            if (btcKeysComparison == 0) {
                return comparator.compare(m1.getRskPublicKey().getPubKey(), m2.getRskPublicKey().getPubKey());
            }
            return btcKeysComparison;
        }
    };

    public FederationMember(BtcECKey btcPublicKey, ECKey rskPublicKey) {
        // Copy public keys to ensure effective immutability
        this.btcPublicKey = BtcECKey.fromPublicOnly(btcPublicKey.getPubKey());
        this.rskPublicKey = ECKey.fromPublicOnly(rskPublicKey.getPubKey());
    }

    BtcECKey getBtcPublicKey() {
        // Return a copy
        return BtcECKey.fromPublicOnly(btcPublicKey.getPubKey());
    }

    ECKey getRskPublicKey() {
        // Return a copy
        return ECKey.fromPublicOnly(rskPublicKey.getPubKey());
    }

    @Override
    public String toString() {
        return String.format("<BTC-%s, RSK-%s> federation member", Hex.toHexString(btcPublicKey.getPubKey()), Hex.toHexString(rskPublicKey.getPubKey()));
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
                Arrays.equals(rskPublicKey.getPubKey(), otherFederationMember.rskPublicKey.getPubKey());
    }

    @Override
    public int hashCode() {
        // Can use java.util.Objects.hash since all of Instant, int and List<BtcECKey> have
        // well-defined hashCode()s
        return Objects.hash(
                btcPublicKey,
                rskPublicKey
        );
    }
}
