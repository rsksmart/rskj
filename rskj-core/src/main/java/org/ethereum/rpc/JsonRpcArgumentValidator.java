/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package org.ethereum.rpc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonRpcArgumentValidator {
    //Same regex as "^(0x([1-9a-f]+[0-9a-f]*|0)|earliest|finalized|safe|latest|pending)$" optimized for java:S5852
    private static final String HEX_BN_OR_ID_REGEX = "^(?:0x[0-9a-fA-F]+$|earliest$|finalized$|safe$|latest$|pending$)";
    private static final Pattern HEX_BN_OR_ID_PATTERN = Pattern.compile(HEX_BN_OR_ID_REGEX);

    private JsonRpcArgumentValidator() { }

    public static boolean isValidHexBlockNumberOrId(String parameter) {
        Matcher matcher = HEX_BN_OR_ID_PATTERN.matcher(parameter);
        return matcher.matches();
    }
}
