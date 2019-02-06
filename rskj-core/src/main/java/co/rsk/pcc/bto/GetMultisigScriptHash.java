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
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.crypto.DeterministicKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeContractIllegalArgumentException;
import co.rsk.pcc.NativeMethod;
import org.ethereum.core.CallTransaction;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This implements the "getMultisigScriptHash" method
 * that belongs to the BTOUtils native contract.
 *
 * @author Ariel Mendelzon
 */
public class GetMultisigScriptHash extends NativeMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "getMultisigScriptHash",
            new String[]{"uint8", "bytes[]"},
            new String[]{"bytes"}
    );

    private final int COMPRESSED_PUBLIC_KEY_LENGTH = 33;
    private final int UNCOMPRESSED_PUBLIC_KEY_LENGTH = 65;

    private BTOUtilsHelper helper;

    public GetMultisigScriptHash(ExecutionEnvironment executionEnvironment, BTOUtilsHelper helper) {
        super(executionEnvironment);
        this.helper = helper;
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) {
        byte minimumSignatures = helper.validateAndGetByteFromBigInteger((BigInteger) arguments[0]);

        Object[] publicKeys = (Object[]) arguments[1];
        if (publicKeys.length < minimumSignatures) {
            throw new NativeContractIllegalArgumentException(String.format(
                    "Given public keys (%d) are less than the minimum required signatures (%d)",
                    publicKeys.length, minimumSignatures
            ));
        }

        Arrays.stream(publicKeys).forEach(o -> {
            byte[] publicKey = (byte[]) o;
            if (publicKey.length != COMPRESSED_PUBLIC_KEY_LENGTH && publicKey.length != UNCOMPRESSED_PUBLIC_KEY_LENGTH) {
                throw new NativeContractIllegalArgumentException(String.format(
                        "Invalid public key length: %d", publicKey.length
                ));
            }
        });

        List<BtcECKey> btcPublicKeys = Arrays.stream(publicKeys)
                .map(o -> BtcECKey.fromPublicOnly((byte[]) o))
                .collect(Collectors.toList());

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