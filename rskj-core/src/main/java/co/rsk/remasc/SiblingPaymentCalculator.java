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

package co.rsk.remasc;

import co.rsk.config.RemascConfig;

import java.math.BigInteger;

/**
 * Created by mario on 09/01/17.
 */
public class SiblingPaymentCalculator {

    private BigInteger individualPublisherReward;
    private BigInteger publishersSurplus;
    private BigInteger individualMinerReward;
    private BigInteger minersSurplus;
    private BigInteger punishment;

    public SiblingPaymentCalculator(BigInteger fullBlockReward, boolean brokenSelectionRule, long siblingsNumber, RemascConfig remascConstants) {
        BigInteger publishersReward = fullBlockReward.divide(BigInteger.valueOf(remascConstants.getPublishersDivisor()));
        BigInteger minersReward = fullBlockReward.subtract(publishersReward);
        BigInteger[] integerDivisionResult = publishersReward.divideAndRemainder(BigInteger.valueOf(siblingsNumber));
        this.individualPublisherReward = integerDivisionResult[0];
        this.publishersSurplus = integerDivisionResult[1];

        BigInteger[] individualRewardDivisionResult = minersReward.divideAndRemainder(BigInteger.valueOf(siblingsNumber + 1L));
        this.individualMinerReward = individualRewardDivisionResult[0];
        this.minersSurplus = individualRewardDivisionResult[1];

        if (brokenSelectionRule) {
            this.punishment = individualMinerReward.divide(BigInteger.valueOf(remascConstants.getPunishmentDivisor()));
            individualMinerReward = individualMinerReward.subtract(punishment);
        }
    }

    public BigInteger getIndividualPublisherReward() {
        return individualPublisherReward;
    }

    public BigInteger getPublishersSurplus() {
        return publishersSurplus;
    }

    public BigInteger getIndividualMinerReward() {
        return individualMinerReward;
    }

    public BigInteger getMinersSurplus() {
        return minersSurplus;
    }

    public BigInteger getPunishment() {
        return punishment;
    }
}
