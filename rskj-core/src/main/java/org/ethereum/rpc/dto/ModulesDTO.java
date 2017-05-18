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

package org.ethereum.rpc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by Ruben on 4/4/2017.
 */
public class ModulesDTO {

    @JsonProperty("eth")
    public String getEthVersion()
    {
        return "1.0";
    }

    @JsonProperty("net")
    public String getNetVersion()
    {
        return "1.0";
    }

    @JsonProperty("rpc")
    public String getRpcVersion()
    {
        return "1.0";
    }

    @JsonProperty("web3")
    public String getWeb3Version()
    {
        return "1.0";
    }
}
