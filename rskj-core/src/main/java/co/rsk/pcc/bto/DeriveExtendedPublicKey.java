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

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.crypto.ChildNumber;
import co.rsk.bitcoinj.crypto.DeterministicKey;
import co.rsk.bitcoinj.crypto.HDKeyDerivation;
import co.rsk.bitcoinj.crypto.HDUtils;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeContractIllegalArgumentException;
import co.rsk.pcc.NativeMethod;
import org.ethereum.core.CallTransaction;

import java.util.List;

/**
 * This implements the "deriveExtendedPublicKey" method
 * that belongs to the BTOUtils native contract.
 *
 * @author Ariel Mendelzon
 */
public class DeriveExtendedPublicKey extends NativeMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "deriveExtendedPublicKey",
            new String[]{"string", "string"},
            new String[]{"string"}
    );

    private final BTOUtilsHelper helper;

    public DeriveExtendedPublicKey(ExecutionEnvironment executionEnvironment, BTOUtilsHelper helper) {
        super(executionEnvironment);
        this.helper = helper;
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) {
        String xpub = (String) arguments[0];
        String path = (String) arguments[1];

        NetworkParameters params = helper.validateAndExtractNetworkFromExtendedPublicKey(xpub);
        DeterministicKey key;
        try {
            key = DeterministicKey.deserializeB58(xpub, params);
        } catch (IllegalArgumentException e) {
            throw new NativeContractIllegalArgumentException(String.format("Invalid extended public key '%s", xpub), e);
        }

        // Path must be of the form S, with S ::= n || n/S with n an unsigned integer
        final String PATH_REGEX = "^((\\d)+/)*(\\d+)$";
        if (!path.matches(PATH_REGEX)) {
            throw new NativeContractIllegalArgumentException(String.format("Invalid path '%s'", path));
        }

        List<ChildNumber> pathList;
        try {
            pathList = HDUtils.parsePath(path);
        } catch (NumberFormatException e) {
            throw new NativeContractIllegalArgumentException(String.format("Invalid path '%s'", path));
        }

        DeterministicKey derived = key;
        for (int i = 0; i < pathList.size(); i++) {
            derived = HDKeyDerivation.deriveChildKey(derived, pathList.get(i).getI());
        }

        return derived.serializePubB58(params);
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