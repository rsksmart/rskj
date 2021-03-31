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

package co.rsk.rpc.modules.trace;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceAction {
    private final CallType callType;
    private final String from;
    private final String to;
    private final String gas;
    private final String input;
    private final String init;
    private final String value;
    private final String address;
    private final String refundAddress;
    private final String balance;
    private final String creationMethod;

    public TraceAction(
            CallType callType,
            String from,
            String to,
            String gas,
            String input,
            String init,
            String creationMethod,
            String value,
            String address,
            String refundAddress,
            String balance) {
        this.callType = callType;
        this.from = from;
        this.to = to;
        this.gas = gas;
        this.input = input;
        this.init = init;
        this.creationMethod = creationMethod;
        this.value = value;
        this.address = address;
        this.refundAddress = refundAddress;
        this.balance = balance;
    }

    @JsonGetter("callType")
    public String getCallType() {
        if (this.callType == CallType.NONE) {
            return null;
        }

        return this.callType.name().toLowerCase();
    }

    @JsonGetter("from")
    public String getFrom() {
        return this.from;
    }

    @JsonGetter("to")
    public String getTo() {
        return this.to;
    }

    @JsonGetter("gas")
    public String getGas() {
        return this.gas;
    }

    @JsonGetter("input")
    public String getInput() {
        return this.input;
    }

    @JsonGetter("creationMethod")
    public String getCreationMethod() {
        return this.creationMethod;
    }

    @JsonGetter("init")
    public String getInit() {
        return this.init;
    }

    @JsonGetter("value")
    public String getValue() {
        return this.value;
    }

    @JsonGetter("balance")
    public String getBalance() { return this.balance; }

    @JsonGetter("address")
    public String getAddress() { return this.address; }

    @JsonGetter("refundAddress")
    public String getRefundAddress() { return this.refundAddress; }
}
