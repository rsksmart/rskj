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

package co.rsk.jmh.web3.e2e;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;

import java.util.Collections;

public class RskWeb3j extends JsonRpc2_0Web3j {

    public RskWeb3j(Web3jService web3jService) {
        super(web3jService);
    }

    public Request<?, RawBlockHeaderByNumberResponse> rskGetRawBlockHeaderByNumber(String bnOrId) {
        return new Request<>("rsk_getRawBlockHeaderByNumber", Collections.singletonList(bnOrId), web3jService, RawBlockHeaderByNumberResponse.class);
    }

    public static class RawBlockHeaderByNumberResponse extends Response<String> {

        public String getRawHeader() {
            return getResult();
        }

    }

}
