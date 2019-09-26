/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.core.RskAddress;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.channel.Channel;
import org.ethereum.rpc.Topic;

import java.util.Arrays;

@JsonDeserialize
public class EthSubscribeLogsParams implements EthSubscribeParams {

    private final RskAddress address;
    private final Topic[] topics;

    public EthSubscribeLogsParams() {
        this(null, new Topic[0]);
    }

    @JsonCreator
    public EthSubscribeLogsParams(
            @JsonProperty("address") RskAddress address,
            @JsonProperty("topics") Topic[] topics
    ) {
        this.address = address;
        this.topics = topics == null? new Topic[0]: topics;
    }

    public RskAddress getAddress() {
        return address;
    }

    public Topic[] getTopics() {
        return Arrays.copyOf(topics, topics.length);
    }

    @Override
    public SubscriptionId accept(EthSubscribeParamsVisitor visitor, Channel channel) {
        return visitor.visit(this, channel);
    }
}
