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

import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDurationParam;
import org.ethereum.rpc.parameters.HexKeyParam;

public interface PersonalModule {
    String dumpRawKey(HexAddressParam address) throws Exception;

    String importRawKey(HexKeyParam key, String passphrase);

    void init();

    String[] listAccounts();

    boolean lockAccount(HexAddressParam address);

    String newAccountWithSeed(String seed);

    String newAccount(String passphrase);

    String sendTransaction(CallArgumentsParam args, String passphrase) throws Exception;

    boolean unlockAccount(HexAddressParam address, String passphrase, HexDurationParam duration);
}
