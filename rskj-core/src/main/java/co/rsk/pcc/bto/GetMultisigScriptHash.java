/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc.bto;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeContractIllegalArgumentException;
import co.rsk.pcc.NativeMethod;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This implements the "getMultisigScriptHash" method
 * that belongs to the HDWalletUtils native contract.
 *
 * @author Ariel Mendelzon
 */
public class GetMultisigScriptHash extends NativeMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "getMultisigScriptHash",
            new String[]{"int256", "bytes[]"},
            new String[]{"bytes"}
    );

    private final static int COMPRESSED_PUBLIC_KEY_LENGTH = 33;
    private final static int UNCOMPRESSED_PUBLIC_KEY_LENGTH = 65;

    // Enforced by the 520-byte size limit of the redeem script
    // (see https://github.com/bitcoin/bips/blob/master/bip-0016.mediawiki#520byte_limitation_on_serialized_script_size)
    private final static int MAXIMUM_ALLOWED_SIGNATURES = 15;

    private final String REQUIRED_SIGNATURE_NULL_OR_ZERO = "Minimum required signatures must be present and greater than zero";
    private final String PUBLIC_KEYS_NULL_OR_ZERO = "At least one public key is required";
    private final String INVALID_REQUIRED_SIGNATURE_AND_PUBLIC_KEYS_PAIR = "Given public keys (%d) are less than the minimum required signatures (%d)";


    public GetMultisigScriptHash(ExecutionEnvironment executionEnvironment) {
        super(executionEnvironment);
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) {
        if (arguments == null || arguments[0] == null) {
            throw new NativeContractIllegalArgumentException(REQUIRED_SIGNATURE_NULL_OR_ZERO);
        }
        int minimumSignatures = ((BigInteger) arguments[0]).intValueExact();
        Object[] publicKeys = (Object[]) arguments[1];

        if (minimumSignatures <= 0) {
            throw new NativeContractIllegalArgumentException(REQUIRED_SIGNATURE_NULL_OR_ZERO);
        }

        if (publicKeys == null || publicKeys.length == 0) {
            throw new NativeContractIllegalArgumentException(PUBLIC_KEYS_NULL_OR_ZERO);
        }

        if (publicKeys.length < minimumSignatures) {
            throw new NativeContractIllegalArgumentException(String.format(
                    INVALID_REQUIRED_SIGNATURE_AND_PUBLIC_KEYS_PAIR,
                    publicKeys.length, minimumSignatures
            ));
        }

        if (publicKeys.length > MAXIMUM_ALLOWED_SIGNATURES) {
            throw new NativeContractIllegalArgumentException(String.format(
                    "Given public keys (%d) are more than the maximum allowed signatures (%d)",
                    publicKeys.length, MAXIMUM_ALLOWED_SIGNATURES
            ));
        }

        List<BtcECKey> btcPublicKeys = new ArrayList<>();
        Arrays.stream(publicKeys).forEach(o -> {
            byte[] publicKey = (byte[]) o;
            if (publicKey.length != COMPRESSED_PUBLIC_KEY_LENGTH && publicKey.length != UNCOMPRESSED_PUBLIC_KEY_LENGTH) {
                throw new NativeContractIllegalArgumentException(String.format(
                        "Invalid public key length: %d", publicKey.length
                ));
            }

            // Avoid extra work by not recalculating compressed keys on already compressed keys
            try {
                BtcECKey btcPublicKey = BtcECKey.fromPublicOnly(publicKey);
                if (publicKey.length == UNCOMPRESSED_PUBLIC_KEY_LENGTH) {
                    btcPublicKey = BtcECKey.fromPublicOnly(btcPublicKey.getPubKeyPoint().getEncoded(true));
                }
                btcPublicKeys.add(btcPublicKey);
            } catch (IllegalArgumentException e) {
                throw new NativeContractIllegalArgumentException(String.format(
                        "Invalid public key format: %s", Hex.toHexString(publicKey)
                ), e);
            }
        });

        Script multisigScript = ScriptBuilder.createP2SHOutputScript(minimumSignatures, btcPublicKeys);

        return multisigScript.getPubKeyHash();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onlyAllowsLocalCalls() {
        return false;
    }
}