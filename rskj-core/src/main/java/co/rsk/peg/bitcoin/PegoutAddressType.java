/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.SegwitAddress;
import co.rsk.bitcoinj.core.Taproot;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;

/**
 * Maps the value a requester passes to the bridge onto the address shape to derive.
 *
 * <p>The names are the ones Bitcoin Core uses for its {@code addresstype} option, so a requester
 * can pass the same value they would give {@code getnewaddress}. The mapping lives here rather
 * than in bitcoinj-thin because the accepted strings are part of the bridge's API, not of the
 * bitcoin library.</p>
 */
public enum PegoutAddressType {
    P2PKH("legacy") {
        @Override
        public Address deriveAddress(BtcECKey key, NetworkParameters networkParameters) {
            return key.toAddress(Script.ScriptType.P2PKH, networkParameters);
        }
    },
    P2SH_P2WPKH("p2sh-segwit") {
        @Override
        public Address deriveAddress(BtcECKey key, NetworkParameters networkParameters) {
            // BIP49 pins the redeem script to the P2WPKH output script, so the address is the
            // hash of that script. On chain this is an ordinary P2SH output, which is why
            // bitcoinj has no script type for it and it composes here instead.
            // The BtcECKey overload, not the byte[] one. That one only checks the length, and an
            // uncompressed key hashes to 20 bytes too, so it would hand back a P2SH address whose
            // witness cannot be relayed.
            Script redeemScript = ScriptBuilder.createP2WPKHOutputScript(key);
            return LegacyAddress.fromP2SHHash(
                networkParameters, Utils.sha256hash160(redeemScript.getProgram()));
        }
    },
    P2WPKH("bech32") {
        @Override
        public Address deriveAddress(BtcECKey key, NetworkParameters networkParameters) {
            return key.toAddress(Script.ScriptType.P2WPKH, networkParameters);
        }
    },
    P2TR("bech32m") {
        @Override
        public Address deriveAddress(BtcECKey key, NetworkParameters networkParameters) {
            return SegwitAddress.fromProgram(
                networkParameters, TAPROOT_WITNESS_VERSION, Taproot.deriveOutputKey(key));
        }
    };

    private static final int TAPROOT_WITNESS_VERSION = 1;

    private final String apiName;

    PegoutAddressType(String apiName) {
        this.apiName = apiName;
    }

    public String getApiName() {
        return apiName;
    }

    /**
     * Derives the destination this type stands for from the requester's public key.
     *
     * <p>Declared per constant rather than as a switch so that a type added later cannot compile
     * without saying how it is derived.</p>
     */
    public abstract Address deriveAddress(BtcECKey key, NetworkParameters networkParameters);

    /**
     * Resolves the type a requester asked for. Matched exactly: no case folding and no trimming,
     * because the value is consensus input and widening what counts as valid widens it for every
     * node forever.
     *
     * @throws IllegalArgumentException if the name is not one of the supported values
     */
    public static PegoutAddressType fromApiName(String apiName) {
        for (PegoutAddressType type : values()) {
            if (type.apiName.equals(apiName)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported peg-out address type: " + apiName);
    }
}
