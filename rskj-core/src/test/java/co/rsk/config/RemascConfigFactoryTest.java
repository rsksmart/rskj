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
import co.rsk.remasc.RemascException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by mario on 12/12/16.
 */
public class RemascConfigFactoryTest {
    private static final String REMASC_FILE = "remasc.json";

    @Test
    public void createRemascConfig() {
        RemascConfigFactory factory = new RemascConfigFactory(REMASC_FILE);

        Assert.assertNotNull(factory);

        RemascConfig remascConfig = factory.createRemascConfig("devnet");
        Assert.assertNotNull(remascConfig);
        Assert.assertNotNull(remascConfig.getMaturity());
        Assert.assertNotNull(remascConfig.getPublishersDivisor());
        Assert.assertNotNull(remascConfig.getPunishmentDivisor());
        Assert.assertNotNull(remascConfig.getRemascRewardAddress());
        Assert.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRemascRewardAddress());
        Assert.assertNotNull(remascConfig.getRemascDivisor());
        Assert.assertNotNull(remascConfig.getSyntheticSpan());
        Assert.assertNotNull(remascConfig.getLateUncleInclusionPunishmentDivisor());


        remascConfig = factory.createRemascConfig("regtest");
        Assert.assertNotNull(remascConfig);
        Assert.assertNotNull(remascConfig.getMaturity());
        Assert.assertNotNull(remascConfig.getPublishersDivisor());
        Assert.assertNotNull(remascConfig.getPunishmentDivisor());
        Assert.assertNotNull(remascConfig.getRemascRewardAddress());
        Assert.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRemascRewardAddress());
        Assert.assertNotNull(remascConfig.getRemascDivisor());
        Assert.assertNotNull(remascConfig.getSyntheticSpan());
        Assert.assertNotNull(remascConfig.getLateUncleInclusionPunishmentDivisor());

        remascConfig = factory.createRemascConfig("main");
        Assert.assertNotNull(remascConfig);
        Assert.assertNotNull(remascConfig.getMaturity());
        Assert.assertNotNull(remascConfig.getPublishersDivisor());
        Assert.assertNotNull(remascConfig.getPunishmentDivisor());
        Assert.assertNotNull(remascConfig.getRemascRewardAddress());
        Assert.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRemascRewardAddress());
        Assert.assertNotNull(remascConfig.getRemascDivisor());
        Assert.assertNotNull(remascConfig.getSyntheticSpan());
        Assert.assertNotNull(remascConfig.getLateUncleInclusionPunishmentDivisor());

        remascConfig = factory.createRemascConfig("testnet");
        Assert.assertNotNull(remascConfig);
        Assert.assertNotNull(remascConfig.getMaturity());
        Assert.assertNotNull(remascConfig.getPublishersDivisor());
        Assert.assertNotNull(remascConfig.getPunishmentDivisor());
        Assert.assertNotNull(remascConfig.getRemascRewardAddress());
        Assert.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRemascRewardAddress());
        Assert.assertNotNull(remascConfig.getRemascDivisor());
        Assert.assertNotNull(remascConfig.getSyntheticSpan());
        Assert.assertNotNull(remascConfig.getLateUncleInclusionPunishmentDivisor());
    }

    @Test(expected = RemascException.class)
    public void createRemascConfigInvalidFile() {
        RemascConfigFactory factory = new RemascConfigFactory("NotAFile");
        factory.createRemascConfig("testnet");
        Assert.fail("This should FAIL");
    }

    @Test(expected = RemascException.class)
    public void createRemascConfigInvalidConfig() {
        RemascConfigFactory factory = new RemascConfigFactory(REMASC_FILE);
        factory.createRemascConfig("fakeNet");
        Assert.fail("This should FAIL");
    }
}
