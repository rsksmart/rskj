/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.rpc.netty;

import co.rsk.rpc.ModuleDescription;

import java.util.ArrayList;
import java.util.List;

public class JsonRpcWeb3ServerProperties {
    private int maxBatchRequestsSize;
    private List<ModuleDescription> rpcModules;
    private int rpcMaxResponseSize;
    private long rpcTimeout;

    //Default values
    private JsonRpcWeb3ServerProperties() {
        this.maxBatchRequestsSize = 100;
        this.rpcModules = new ArrayList<>();
        this.rpcMaxResponseSize = 0;
        this.rpcTimeout = 0;
    }


    public int getMaxBatchRequestsSize() {
        return maxBatchRequestsSize;
    }

    public List<ModuleDescription> getRpcModules() {
        return rpcModules;
    }

    public int getRpcMaxResponseSize() {
        return rpcMaxResponseSize;
    }

    public long getRpcTimeout() {
        return rpcTimeout;
    }

    public static JsonRpcWeb3ServerPropertiesBuilder builder() {
        return new JsonRpcWeb3ServerPropertiesBuilder();
    }

    //Builder pattern
    public static class JsonRpcWeb3ServerPropertiesBuilder {
        private Integer maxBatchRequestsSize;
        private List<ModuleDescription> rpcModules;
        private Integer rpcMaxResponseSize;
        private Long rpcTimeout;

        public JsonRpcWeb3ServerPropertiesBuilder() {
        }

        public JsonRpcWeb3ServerProperties build() {
            JsonRpcWeb3ServerProperties jsonRpcWeb3ServerProperties = new JsonRpcWeb3ServerProperties();
            if(maxBatchRequestsSize != null){
                jsonRpcWeb3ServerProperties.maxBatchRequestsSize = maxBatchRequestsSize;
            }
            if(rpcModules != null){
                jsonRpcWeb3ServerProperties.rpcModules = rpcModules;
            }
            if(rpcMaxResponseSize != null){
                jsonRpcWeb3ServerProperties.rpcMaxResponseSize = rpcMaxResponseSize;
            }
            if(rpcTimeout != null){
                jsonRpcWeb3ServerProperties.rpcTimeout = rpcTimeout;
            }
            return jsonRpcWeb3ServerProperties;
        }

        public JsonRpcWeb3ServerPropertiesBuilder maxBatchRequestsSize(int maxBatchRequestSize) {
            this.maxBatchRequestsSize = maxBatchRequestSize;
            return this;
        }

        public JsonRpcWeb3ServerPropertiesBuilder rpcModules(List<ModuleDescription> rpcModules) {
            this.rpcModules = rpcModules;
            return this;
        }

        public JsonRpcWeb3ServerPropertiesBuilder rpcMaxResponseSize(int rpcMaxResponseSize) {
            this.rpcMaxResponseSize = rpcMaxResponseSize;
            return this;
        }

        public JsonRpcWeb3ServerPropertiesBuilder rpcTimeout(long rpcTimeout) {
            this.rpcTimeout = rpcTimeout;
            return this;
        }
    }

}
