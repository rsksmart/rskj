/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.rpc.validation;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class EthAddressValidator {
    private static final int ETH_ADDRESS_BYTE_LENGTH = 20;
    private EthAddressValidator() { }

    public static boolean isValid(String parameter){
        byte[] addressBytes = null;
        try {
            addressBytes = HexUtils.stringHexToByteArray(parameter);
        }catch (Exception e){
            throw RskJsonRpcRequestException.invalidParamError("Invalid address format. " + e.getMessage());
        }
        if(ETH_ADDRESS_BYTE_LENGTH != addressBytes.length ){
            throw RskJsonRpcRequestException.invalidParamError("Invalid address: incorrect length.");
        }
        return true;
    }
}
