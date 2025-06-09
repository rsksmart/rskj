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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

@JsonDeserialize(using = HexNumberParam.Deserializer.class)
public class HexNumberParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int HEX_NUM_BYTE_LENGTH = 32;
    public static final int MAX_HEX_NUM_LEN = 2 + 2 * HEX_NUM_BYTE_LENGTH; // 2 bytes for 0x prefix; 2 hex characters per byte

    private final String hexNumber;

    public HexNumberParam(String hexNumber) {
        if (!HexUtils.isHexWithPrefix(hexNumber)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid param: invalid hex string.");
        }

        if (!isHexNumberLengthValid(hexNumber)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid param: invalid hex length.");
        }

        this.hexNumber = hexNumber;
    }

    public String getHexNumber() {
        return this.hexNumber;
    }

    @Override
    public String toString() {
        return this.hexNumber;
    }

    public static boolean isHexNumberLengthValid(String hex) {
        return hex != null && hex.length() <= MAX_HEX_NUM_LEN;
    }

    public static class Deserializer extends StdDeserializer<HexNumberParam> {

        @Serial
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexNumberParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexNumber = jp.getText();
            return new HexNumberParam(hexNumber);
        }
    }
}
