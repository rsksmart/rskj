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
import co.rsk.bitcoinj.crypto.DeterministicKey;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeMethod;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import co.rsk.util.StringUtils;
import org.ethereum.core.CallTransaction;

/**
 * This implements the "extractPublicKeyFromExtendedPublicKey" method
 * that belongs to the HDWalletUtils native contract.
 *
 * @author Ariel Mendelzon
 */
public class ExtractPublicKeyFromExtendedPublicKey extends NativeMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "extractPublicKeyFromExtendedPublicKey",
            new String[]{"string"},
            new String[]{"bytes"}
    );

    private final HDWalletUtilsHelper helper;

    private final static String INVALID_EXTENDED_PUBLIC_KEY = "Invalid extended public key '%s'";

    public ExtractPublicKeyFromExtendedPublicKey(ExecutionEnvironment executionEnvironment, HDWalletUtilsHelper helper) {
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
            throw new NativeContractIllegalArgumentException(String.format(INVALID_EXTENDED_PUBLIC_KEY, null));
        }
        String xpub = (String) arguments[0];

        NetworkParameters params = helper.validateAndExtractNetworkFromExtendedPublicKey(xpub);
        DeterministicKey key;
        try {
            key = DeterministicKey.deserializeB58(xpub, params);
        } catch (IllegalArgumentException e) {
            throw new NativeContractIllegalArgumentException(String.format(INVALID_EXTENDED_PUBLIC_KEY, StringUtils.trim(xpub)), e);
        }

        return key.getPubKeyPoint().getEncoded(true);
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
        return 11_300L;
    }
}