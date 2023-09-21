/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public abstract class HexStringParam {
    HexStringParam(String hexString) {
        if(hexString.isEmpty()) {
            return;
        }

        if (!HexUtils.hasHexPrefix(hexString) || !HexUtils.isHex(hexString,2)) {
            throw RskJsonRpcRequestException.invalidParamError(String.format("Invalid argument \"%s\": param should be a hex value string.", hexString));
        }
    }
}
