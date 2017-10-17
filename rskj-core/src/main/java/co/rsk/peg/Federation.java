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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable representation of an RSK Federation in the context of
 * a specific BTC network.
 *
 * @author Ariel Mendelzon
 */
public final class Federation {
    private int numberOfSignaturesRequired;
    private List<BtcECKey> publicKeys;
    private long creationTime;
    private NetworkParameters btcParams;
    private Script redeemScript;
    private Script p2shScript;
    private Address address;

    public static Federation fromPrivateKeys(int numberOfSignaturesRequired, List<BtcECKey> privateKeys, long creationTime, NetworkParameters btcParams) {
        List<BtcECKey> publicKeys = privateKeys.stream()
                .map(key -> BtcECKey.fromPublicOnly(key.getPubKey()))
                .collect(Collectors.toList());
        return new Federation(numberOfSignaturesRequired, publicKeys, creationTime, btcParams);
    }

    public Federation(int numberOfSignaturesRequired, List<BtcECKey> publicKeys, long creationTime, NetworkParameters btcParams) {
        this.numberOfSignaturesRequired = numberOfSignaturesRequired;
        this.publicKeys = Collections.unmodifiableList(publicKeys);
        this.creationTime = creationTime;
        this.btcParams = btcParams;
        // Calculated once on-demand
        this.redeemScript = null;
        this.address = null;
    }

    public List<BtcECKey> getPublicKeys() {
        return publicKeys;
    }

    public int getNumberOfSignaturesRequired() {
        return this.numberOfSignaturesRequired;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public NetworkParameters getBtcParams() {
        return btcParams;
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

    @Override
    public String toString() {
        return getAddress().toString();
    }
}
