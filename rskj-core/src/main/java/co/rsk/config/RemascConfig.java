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
package co.rsk.config;

import co.rsk.core.RskAddress;

/**
 * Created by mario on 12/12/16.
 */
public class RemascConfig {
    // Number of blocks until mining fees are processed
    private long maturity;

    // Number of blocks block reward is split into
    private long syntheticSpan;

    // RSK labs address.
    // Note that his has to be a basic type (such as String) because RemascConfig
    // is deserialized automatically from JSON.
    private String remascRewardAddress;

    // RSK labs address.
    // Note that his has to be a basic type (such as String) because RemascConfig
    // is deserialized automatically from JSON.
    private String remascRewardAddressRskip218;

    // RSK labs address.
    // Note that his has to be a basic type (such as String) because RemascConfig
    // is deserialized automatically from JSON.
    private String remascRewardAddressRskip348;

    // RSK labs cut. Available reward / rskLabsDivisor is what RSK gets.
    private long remascDivisor = 5;

    // Federation cut. Available reward / rskFederationDivisor is what Federation gets.
    private long federationDivisor = 100;

    // Punishment in case of broken selection rule. The punishment applied is available reward / punishmentDivisor.
    private long punishmentDivisor = 10;

    // Reward to block miners who included uncles in their blocks. Available reward / publishersDivisor is the total reward.
    private long publishersDivisor = 10;

    private long lateUncleInclusionPunishmentDivisor = 20;

    // Multiplier and Divisor for paid fees comparison in selection rule
    private long paidFeesMultiplier = 2;
    private long paidFeesDivisor = 1;

    public long getPaidFeesMultiplier() { return paidFeesMultiplier; }

    public long getPaidFeesDivisor() { return paidFeesDivisor; }

    public long getMaturity() {
        return maturity;
    }

    public long getSyntheticSpan() {
        return syntheticSpan;
    }

    public RskAddress getRemascRewardAddress() {
        return new RskAddress(this.remascRewardAddress);
    }

    public RskAddress getRemascRewardAddressRskip218() {
        return new RskAddress(this.remascRewardAddressRskip218);
    }

    public RskAddress getRemascRewardAddressRskip348() {
        return new RskAddress(this.remascRewardAddressRskip348);
    }

    public long getRemascDivisor() {
        return remascDivisor;
    }

    public long getFederationDivisor() {
        return federationDivisor;
    }

    public long getPunishmentDivisor() {
        return punishmentDivisor;
    }

    public long getLateUncleInclusionPunishmentDivisor() { return this.lateUncleInclusionPunishmentDivisor; }

    public long getPublishersDivisor() {
        return publishersDivisor;
    }

}
