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
package co.rsk.rpc.modules.eth.subscribe;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * The subscription params DTO for JSON serialization purposes.
 */
public class EthSubscriptionParams {
    private final SubscriptionId subscription;
    private final EthSubscriptionNotificationDTO result;

    public EthSubscriptionParams(SubscriptionId subscription, EthSubscriptionNotificationDTO result) {
        this.subscription = Objects.requireNonNull(subscription);
        this.result = Objects.requireNonNull(result);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public SubscriptionId getSubscription() {
        return subscription;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public EthSubscriptionNotificationDTO getResult() {
        return result;
    }
}
