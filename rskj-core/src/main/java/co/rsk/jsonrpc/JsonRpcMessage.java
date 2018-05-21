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
package co.rsk.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The basic JSON-RPC 2.0 message.
 *
 * It defines the version property, which should always be '2.0'.
 */
@JsonPropertyOrder({"jsonrpc", "id", "method", "result", "params", "error"})
public abstract class JsonRpcMessage {
    private final JsonRpcVersion version;

    public JsonRpcMessage(JsonRpcVersion version) {
        this.version = verifyVersion(version);
    }

    @JsonProperty("jsonrpc")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public JsonRpcVersion getVersion() {
        return version;
    }

    private static JsonRpcVersion verifyVersion(JsonRpcVersion version) {
        if (version != JsonRpcVersion.V2_0) {
            throw new IllegalArgumentException(
                    String.format("JSON-RPC version should always be %s, but was %s.", JsonRpcVersion.V2_0, version)
            );
        }

        return version;
    }
}
