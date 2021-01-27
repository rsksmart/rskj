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
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;

/**
 * Created by oscar on 05/08/2016.
 */
public class PegTestUtils {

    public static void main(String[] args) {
        for (int i = 0; i < 257; i++) {
            createHash3();
        }
        Keccak256 hash = createHash3();
    }

    private static int nhash = 0;

    /**
     * @deprecated Use createHash3(int) instead. Avoid using persisted state in static class in test environments
     */
    @Deprecated
    public static Keccak256 createHash3() {
        return createHash3(nhash++);
    }

    public static Keccak256 createHash3(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (nHash & 0xFF);
        bytes[1] = (byte) (nHash >>8 & 0xFF);
        return new Keccak256(bytes);
    }

    /**
     * @deprecated Use createHash(int) instead. Avoid using persisted state in static class in test environments
     */
    @Deprecated
    public static Sha256Hash createHash() {
        return createHash(nhash++);
    }

    public static Sha256Hash createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nHash;
        return Sha256Hash.wrap(bytes);
    }

    public static Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        return inputScript;
    }

    public static Script createBaseRedeemScriptThatSpendsFromTheFederation(Federation federation) {
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        return redeemScript;
    }

    public static Script createOpReturnScriptForRsk(
        int protocolVersion,
        RskAddress rskDestinationAddress,
        Optional<Address> btcRefundAddressOptional
    ) {
        int index = 0;
        int payloadLength;
        if (btcRefundAddressOptional.isPresent()) {
            payloadLength = 46;
        } else {
            payloadLength = 25;
        }
        byte[] payloadBytes = new byte[payloadLength];

        byte[] prefix = Hex.decode("52534b54"); // 'RSKT' in hexa
        System.arraycopy(prefix, 0, payloadBytes, index, prefix.length);
        index += prefix.length;

        payloadBytes[index] = (byte) protocolVersion;
        index++;

        System.arraycopy(
            rskDestinationAddress.getBytes(),
            0,
            payloadBytes,
            index,
            rskDestinationAddress.getBytes().length
        );
        index += rskDestinationAddress.getBytes().length;

        if (btcRefundAddressOptional.isPresent()) {
            Address btcRefundAddress = btcRefundAddressOptional.get();
            if (btcRefundAddress.isP2SHAddress()) {
                payloadBytes[index] = 2; // P2SH address type
            } else {
                payloadBytes[index] = 1; // P2PKH address type
            }
            index++;

            System.arraycopy(
                btcRefundAddress.getHash160(),
                0,
                payloadBytes,
                index,
                btcRefundAddress.getHash160().length
            );
        }

        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }

    public static Script createOpReturnScriptForRskWithCustomPayload(int protocolVersion, byte[] customPayload) {
        int index = 0;
        int payloadLength = customPayload.length;

        byte[] payloadBytes = new byte[payloadLength + 5]; // Add 4 bytes for the prefix, and another for the protocol version

        byte[] prefix = Hex.decode("52534b54"); // 'RSKT' in hexa
        System.arraycopy(prefix, 0, payloadBytes, index, prefix.length);
        index += prefix.length;

        payloadBytes[index] = (byte) protocolVersion;
        index++;

        System.arraycopy(customPayload, 0, payloadBytes, index, customPayload.length);

        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }
}
