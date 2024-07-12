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
import co.rsk.core.types.bytes.Bytes;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeMethod;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.core.CallTransaction;

import java.math.BigInteger;
import java.util.ArrayList;
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

    private final static long BASE_COST = 20_000L;
    private final static long COST_PER_EXTRA_KEY = 700L;

    private final static int MINIMUM_REQUIRED_KEYS = 2;

    // Enforced by the 520-byte size limit of the redeem script
    // (see https://github.com/bitcoin/bips/blob/master/bip-0016.mediawiki#520byte_limitation_on_serialized_script_size)
    private final static int MAXIMUM_ALLOWED_KEYS = 15;

    private final static String REQUIRED_SIGNATURE_NULL_OR_ZERO = "Minimum required signatures must be present and greater than zero";
    private final static String PUBLIC_KEYS_NULL_OR_ONE = String.format("At least %d public keys are required", MINIMUM_REQUIRED_KEYS);
    private final static String INVALID_REQUIRED_SIGNATURE_AND_PUBLIC_KEYS_PAIR = "Given public keys (%d) are less than the minimum required signatures (%d)";


    public GetMultisigScriptHash(ExecutionEnvironment executionEnvironment) {
        super(executionEnvironment);
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) throws NativeContractIllegalArgumentException {
        if (arguments == null || arguments[0] == null) {
            throw new NativeContractIllegalArgumentException(REQUIRED_SIGNATURE_NULL_OR_ZERO);
        }
        int minimumSignatures = ((BigInteger) arguments[0]).intValueExact();
        Object[] publicKeys = (Object[]) arguments[1];

        if (minimumSignatures <= 0) {
            throw new NativeContractIllegalArgumentException(REQUIRED_SIGNATURE_NULL_OR_ZERO);
        }

        if (publicKeys == null || publicKeys.length < MINIMUM_REQUIRED_KEYS) {
            throw new NativeContractIllegalArgumentException(PUBLIC_KEYS_NULL_OR_ONE);
        }

        if (publicKeys.length < minimumSignatures) {
            throw new NativeContractIllegalArgumentException(String.format(
                    INVALID_REQUIRED_SIGNATURE_AND_PUBLIC_KEYS_PAIR,
                    publicKeys.length, minimumSignatures
            ));
        }

        if (publicKeys.length > MAXIMUM_ALLOWED_KEYS) {
            throw new NativeContractIllegalArgumentException(String.format(
                    "Given public keys (%d) are more than the maximum allowed signatures (%d)",
                    publicKeys.length, MAXIMUM_ALLOWED_KEYS
            ));
        }

        List<BtcECKey> btcPublicKeys = new ArrayList<>();
        for (Object o: publicKeys) {
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
                        "Invalid public key format: %s", Bytes.of(publicKey)
                ), e);
            }
        }

        Script multisigScript = ScriptBuilder.createP2SHOutputScript(minimumSignatures, btcPublicKeys);

        return multisigScript.getPubKeyHash();
    }

    @Override
    public long getGas(Object[] parsedArguments, byte[] originalData) {
        Object[] keys = ((Object[]) parsedArguments[1]);

        if (keys == null || keys.length < 2) {
            return BASE_COST;
        }

        // Base cost is the cost for 2 keys (the minimum).
        // Then a fee is payed per additional key.
        return BASE_COST + (keys.length - 2) * COST_PER_EXTRA_KEY;
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