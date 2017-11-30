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

package co.rsk.rpc.exception;

/**
 * Holds error codes defined as determined by JSON-RPC standard.
 * For more information please refer to http://www.jsonrpc.org/specification (section 5.1)
 *
 * @author martin.medina
 * @since  10.23.17
 */
class JsonRpcApplicationDefinedErrorCodes {

    static final Integer SUBMIT_BLOCK = -33000;

    private JsonRpcApplicationDefinedErrorCodes() {
        throw new IllegalAccessError("Utility class");
    }
}
