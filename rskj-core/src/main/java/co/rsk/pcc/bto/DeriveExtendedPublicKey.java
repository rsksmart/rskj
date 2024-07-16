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
import co.rsk.pcc.NativeMethod;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import co.rsk.util.StringUtils;
import org.ethereum.core.CallTransaction;

import java.util.Arrays;
import java.util.List;

/**
 * This implements the "deriveExtendedPublicKey" method
 * that belongs to the HDWalletUtils native contract.
 *
 * @author Ariel Mendelzon
 */
public class DeriveExtendedPublicKey extends NativeMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "deriveExtendedPublicKey",
            new String[]{"string", "string"},
            new String[]{"string"}
    );

    private final HDWalletUtilsHelper helper;

    public DeriveExtendedPublicKey(ExecutionEnvironment executionEnvironment, HDWalletUtilsHelper helper) {
        super(executionEnvironment);
        this.helper = helper;
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) throws NativeContractIllegalArgumentException {
        if (arguments == null) {
            throw new NativeContractIllegalArgumentException("Must provide xpub and path arguments. None was provided");
        }
        String xpub = (String) arguments[0];
        String path = (String) arguments[1];

        NetworkParameters params = helper.validateAndExtractNetworkFromExtendedPublicKey(xpub);
        DeterministicKey key;
        try {
            key = DeterministicKey.deserializeB58(xpub, params);
        } catch (IllegalArgumentException e) {
            throw new NativeContractIllegalArgumentException("Invalid extended public key", e);
        }

        // Path must be of the form S, with S ::= n || n/S with n an unsigned integer

        // Covering special case: upon splitting a string, if the string ends with the delimiter, then
        // there is no empty string as a last element. Make sure that the whole path starts and ends with a digit
        // just in case.
        if (path == null || path.length() == 0 || !isDecimal(path.substring(0,1)) || !isDecimal(path.substring(path.length()-1, path.length()))) {
            throwInvalidPath(path);
        }

        String[] pathChunks = path.split("/");

        if (pathChunks.length > 10) {
            throw new NativeContractIllegalArgumentException("Path should contain 10 levels at most");
        }

        if (Arrays.stream(pathChunks).anyMatch(s -> !isDecimal(s))) {
            throwInvalidPath(path);
        }

        List<ChildNumber> pathList;
        try {
            pathList = HDUtils.parsePath(path);
        } catch (NumberFormatException ex) {
            throw new NativeContractIllegalArgumentException(getInvalidPathErrorMessage(path), ex);
        }

        DeterministicKey derived = key;
        for (ChildNumber pathItem : pathList) {
            derived = HDKeyDerivation.deriveChildKey(derived, pathItem.getI());
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

    @Override
    public long getGas(Object[] parsedArguments, byte[] originalData) {
        return 107_000L;
    }

    private void throwInvalidPath(String path) throws NativeContractIllegalArgumentException {
        throw new NativeContractIllegalArgumentException(getInvalidPathErrorMessage(path));
    }

    private String getInvalidPathErrorMessage(String path) {
        return String.format("Invalid path '%s'", StringUtils.trim(path));
    }

    private boolean isDecimal(String s) {
        try {
            return Integer.parseInt(s) >= 0;
        } catch(NumberFormatException e) {
            return false;
        }
    }
}