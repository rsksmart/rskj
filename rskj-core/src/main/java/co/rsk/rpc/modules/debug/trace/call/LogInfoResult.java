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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LogInfoResult(@JsonProperty int index, @JsonProperty String address, @JsonProperty List<String> topics,
                            @JsonProperty String data) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int index;
        private String address;
        private List<String> topics;
        private String data;

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder topics(List<String> topics) {
            this.topics = topics;
            return this;
        }

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public LogInfoResult build() {
            return new LogInfoResult(index, address, topics, data);
        }
    }
}
