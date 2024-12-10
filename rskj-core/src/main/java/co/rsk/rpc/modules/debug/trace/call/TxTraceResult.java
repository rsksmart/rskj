/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.rpc.modules.debug.trace.call;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TxTraceResult {

    private final String type;
    private final String from;
    private final String to;
    private final String value;
    private final String gas;
    private final String gasUsed;
    private final String input;
    private final String output;
    private final String error;
    private final String revertReason;
    private final List<TxTraceResult> calls;
    private final List<LogInfoResult> logs;

    //Used by deserializer
    public TxTraceResult(){
        this.type = null;
        this.from = null;
        this.to = null;
        this.value = null;
        this.gas = null;
        this.gasUsed = null;
        this.input = null;
        this.output = null;
        this.error = null;
        this.revertReason = null;
        this.calls = new ArrayList<>();
        this.logs = new ArrayList<>();
    }

    public TxTraceResult(String type, String from, String to, String value, String gas, String gasUsed, String input, String output, String error, String revertReason, List<TxTraceResult> calls, List<LogInfoResult> logs) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.value = value;
        this.gas = gas;
        this.gasUsed = gasUsed;
        this.input = input;
        this.output = output;
        this.error = error;
        this.revertReason = revertReason;
        this.calls = calls == null ? new ArrayList<>() : calls;
        this.logs = logs == null ? new ArrayList<>() : logs;
    }

    @JsonGetter("type")
    public String getType() {
        return type;
    }

    @JsonGetter("from")
    public String getFrom() {
        return from;
    }

    @JsonGetter("to")
    public String getTo() {
        return to;
    }

    @JsonGetter("value")
    public String getValue() {
        return value;
    }

    @JsonGetter("gas")
    public String getGas() {
        return gas;
    }

    @JsonGetter("gasUsed")
    public String getGasUsed() {
        return gasUsed;
    }

    @JsonGetter("input")
    public String getInput() {
        return input;
    }

    @JsonGetter("output")
    public String getOutput() {
        return output;
    }

    @JsonGetter("error")
    public String getError() {
        return error;
    }

    @JsonGetter("revertReason")
    public String getRevertReason() {
        return revertReason;
    }

    @JsonGetter("calls")
    public List<TxTraceResult> getCalls() {
        return calls.isEmpty() ? null : calls;
    }

    @JsonGetter("logs")
    public List<LogInfoResult> getLogs() {
        return logs.isEmpty() ? null : logs;
    }

    @JsonIgnore
    public static Builder builder() {
        return new Builder();
    }

    public void addCall(TxTraceResult call) {
        calls.add(call);
    }

    //Builder class
    public static class Builder {
        private String type;
        private String from;
        private String to;
        private String value;
        private String gas;
        private String gasUsed;
        private String input;
        private String output;
        private String error;
        private String revertReason;
        private List<TxTraceResult> calls;
        private List<LogInfoResult> logs;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder gas(String gas) {
            this.gas = gas;
            return this;
        }

        public Builder gasUsed(String gasUsed) {
            this.gasUsed = gasUsed;
            return this;
        }

        public Builder input(String input) {
            this.input = input;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder revertReason(String revertReason) {
            this.revertReason = revertReason;
            return this;
        }

        public Builder calls(List<TxTraceResult> calls) {
            this.calls = calls;
            return this;
        }

        public Builder logs(List<LogInfoResult> logs) {
            this.logs = logs;
            return this;
        }

        public TxTraceResult build() {
            return new TxTraceResult(type, from, to, value, gas, gasUsed, input, output, error, revertReason, calls, logs);
        }
    }

}
