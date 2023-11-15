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

package co.rsk.rpc.modules.personal;

import org.ethereum.rpc.exception.DisabledWalletException;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDurationParam;
import org.ethereum.rpc.parameters.HexKeyParam;

public class PersonalModuleWalletDisabled implements PersonalModule {
    @Override
    public void init() {
        // Init steps are only needed when using a wallet.
        // This method is called from Web3Impl even if the wallet is disabled,
        // so we don't throw here.
    }

    @Override
    public String newAccountWithSeed(String seed) {
        throw new DisabledWalletException();
    }

    @Override
    public String newAccount(String passphrase) {
        throw new DisabledWalletException();
    }

    @Override
    public String[] listAccounts() {
        throw new DisabledWalletException();
    }

    @Override
    public String importRawKey(HexKeyParam key, String passphrase) {
        throw new DisabledWalletException();
    }

    @Override
    public String sendTransaction(CallArgumentsParam args, String passphrase) {
        throw new DisabledWalletException();
    }

    @Override
    public boolean unlockAccount(HexAddressParam address, String passphrase, HexDurationParam duration) {
        throw new DisabledWalletException();
    }

    @Override
    public boolean lockAccount(HexAddressParam address) {
        throw new DisabledWalletException();
    }

    @Override
    public String dumpRawKey(HexAddressParam address) {
        throw new DisabledWalletException();
    }
}