/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.rpc.modules.trace;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceResult {

    private final String gasUsed;
    private final String output;
    private final String code;
    private final String address;

    public TraceResult(String gasUsed, String output, String code, String address) {
        this.gasUsed = gasUsed;
        this.output = output;
        this.code = code;
        this.address = address;
    }

    @JsonGetter("gasUsed")
    public String getGasUsed() {
        return this.gasUsed;
    }

    @JsonGetter("output")
    public String getOutput() {
        return this.output;
    }

    @JsonGetter("code")
    public String getCode() {
        return this.code;
    }

    @JsonGetter("address")
    public String getAddress() {
        return this.address;
    }
}

