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

package co.rsk.rpc.modules.eth;

import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class EthModuleWalletDisabled implements EthModuleWallet {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    @Override
    public String[] accounts() {
        String[] accounts = {};
        LOGGER.debug("eth_accounts(): {}", Arrays.toString(accounts));
        return accounts;
    }

    @Override
    public String sign(String addr, String data) {
        LOGGER.debug("eth_sign({}, {}): {}", addr, data, null);
        throw invalidParamError("Local wallet is disabled in this node");
    }
}