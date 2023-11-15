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

import co.rsk.core.RskAddress;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = HexAddressParam.Deserializer.class)
public class HexAddressParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient final RskAddress address;

    public HexAddressParam(String hexAddress) {
        if (hexAddress == null || hexAddress.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid address: empty or null.");
        }

        try {
            this.address = new RskAddress(hexAddress);
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid address format: invalid hex value.", e);
        }
    }

    public RskAddress getAddress() {
        return address;
    }

    public static class Deserializer extends StdDeserializer<HexAddressParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexAddressParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexAddress = jp.getText();
            return new HexAddressParam(hexAddress);
        }
    }
}
