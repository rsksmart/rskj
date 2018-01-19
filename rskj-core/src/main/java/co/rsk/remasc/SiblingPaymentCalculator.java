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
import co.rsk.core.Coin;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Created by mario on 09/01/17.
 */
public class SiblingPaymentCalculator {

    private final Coin individualPublisherReward;
    private final Coin publishersSurplus;
    private final Coin individualMinerReward;
    private final Coin minersSurplus;
    private final Coin punishment;

    public SiblingPaymentCalculator(Coin fullBlockReward, boolean brokenSelectionRule, long siblingsNumber, RemascConfig remascConstants) {
        Coin publishersReward = fullBlockReward.divide(BigInteger.valueOf(remascConstants.getPublishersDivisor()));
        Coin minersReward = fullBlockReward.subtract(publishersReward);
        Coin[] integerDivisionResult = publishersReward.divideAndRemainder(BigInteger.valueOf(siblingsNumber));
        this.individualPublisherReward = integerDivisionResult[0];
        this.publishersSurplus = integerDivisionResult[1];

        Coin[] individualRewardDivisionResult = minersReward.divideAndRemainder(BigInteger.valueOf(siblingsNumber + 1L));
        this.minersSurplus = individualRewardDivisionResult[1];
        if (brokenSelectionRule) {
            this.punishment = individualRewardDivisionResult[0].divide(BigInteger.valueOf(remascConstants.getPunishmentDivisor()));
            this.individualMinerReward = individualRewardDivisionResult[0].subtract(punishment);
        } else {
            this.punishment = null;
            this.individualMinerReward = individualRewardDivisionResult[0];
        }
    }

    public Coin getIndividualPublisherReward() {
        return individualPublisherReward;
    }

    public Coin getPublishersSurplus() {
        return publishersSurplus;
    }

    public Coin getIndividualMinerReward() {
        return individualMinerReward;
    }

    public Coin getMinersSurplus() {
        return minersSurplus;
    }

    @Nullable
    public Coin getPunishment() {
        return punishment;
    }
}
