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
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.channel.Channel;
import org.ethereum.rpc.Topic;

import java.util.Arrays;

@JsonDeserialize
public class EthSubscribeLogsParams implements EthSubscribeParams {

    private final RskAddress[] addresses;
    private final String[] fromBlock;
    private final Topic[][] topics;
    private final String[] toBlock;

    public EthSubscribeLogsParams() {
        this(new RskAddress[0], new String[0], new String[0], new Topic[0][]);
    }

    @JsonCreator
    public EthSubscribeLogsParams(
            @JsonProperty("address") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) RskAddress[] addresses,
            @JsonProperty("fromBlock") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) String[] fromBlock,
            @JsonProperty("toBlock") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) String[] toBlock,
            @JsonProperty("topics") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) Topic[][] topics
    ) {
        this.addresses = addresses == null? new RskAddress[0]: addresses;
        this.fromBlock = fromBlock == null? new String[0]: fromBlock;
        this.toBlock = fromBlock == null? new String[0]: toBlock;
        this.topics = topics == null? new Topic[0][]: topics;
    }

    public RskAddress[] getAddresses() {
        return Arrays.copyOf(addresses, addresses.length);
    }

    public String[] getFromBlock(){
        return Arrays.copyOf(fromBlock, fromBlock.length);
    }

    public String[] getToBlock(){
        return Arrays.copyOf(toBlock, toBlock.length);
    }

    public Topic[][] getTopics() {
        // TODO(mc) deep copy
        return Arrays.copyOf(topics, topics.length);
    }

    @Override
    public SubscriptionId accept(EthSubscribeParamsVisitor visitor, Channel channel) {
        return visitor.visit(this, channel);
    }
}
