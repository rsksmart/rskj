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

import io.netty.channel.Channel;

/**
 * Classes implementing this interface know how to handle JSON-RPC eth_subscribe requests.
 */
public interface EthSubscribeParamsVisitor {
    /**
     * @param params new heads subscription request parameters.
     * @param channel a Netty channel to subscribe notifications to.
     * @return a subscription id which should be used as an unsubscribe parameter.
     */
    SubscriptionId visit(EthSubscribeNewHeadsParams params, Channel channel);

    /**
     * @param params logs subscription request parameters.
     * @param channel a Netty channel to subscribe notifications to.
     * @return a subscription id which should be used as an unsubscribe parameter.
     */
    SubscriptionId visit(EthSubscribeLogsParams params, Channel channel);

    /**
     * @param params logs subscription request parameters.
     * @param channel a Netty channel to subscribe notifications to.
     * @return a subscription id which should be used as an unsubscribe parameter.
     */
    SubscriptionId visit(EthSubscribePendingTransactionsParams params, Channel channel);

    /**
     * @param params logs subscription request parameters.
     * @param channel a Netty channel to subscribe notifications to.
     * @return a subscription id which should be used as an unsubscribe parameter.
     */
    SubscriptionId visit(EthSubscribeSyncParams params, Channel channel);
}
