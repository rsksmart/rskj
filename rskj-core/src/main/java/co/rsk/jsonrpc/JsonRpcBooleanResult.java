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

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A simple true/false JSON-RPC result.
 */
public class JsonRpcBooleanResult extends JsonRpcResult {
    private final boolean result;

    public JsonRpcBooleanResult(boolean result) {
        this.result = result;
    }

    @JsonValue
    public boolean getResult() {
        return result;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(result);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof JsonRpcBooleanResult)) {
            return false;
        }

        JsonRpcBooleanResult other = (JsonRpcBooleanResult) o;
        return this.result == other.result;
    }
}
