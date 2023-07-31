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
import org.ethereum.core.genesis.BlockTag;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public final class BnTagOrNumberValidator {
    private BnTagOrNumberValidator() {

    }

    /**
     * Validates if the given parameter is a valid block number or tag.
     * The tags can be one of the following: "earliest", "finalized", "safe", "latest", "pending".
     * If the parameter is not a tag, it checks if the parameter is a hexadecimal number with prefix.
     *
     * @param parameter The string representation of the block number or tag to be validated.
     * @return Nothing.
     * @throws RskJsonRpcRequestException If the parameter is null or it's neither a valid tag nor a hexadecimal number with prefix.
     */
    public static void isValid(String parameter) {
        if (parameter == null) {
            throw RskJsonRpcRequestException.invalidParamError("Cannot process null parameter");
        }

        parameter = parameter.toLowerCase();

        if (BlockTag.fromString(parameter) == null && !HexUtils.isHexWithPrefix(parameter)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block number: " + parameter);
        }
    }
}
