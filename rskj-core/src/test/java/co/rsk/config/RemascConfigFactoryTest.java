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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by mario on 12/12/16.
 */
class RemascConfigFactoryTest {
    private static final String REMASC_FILE = "remasc.json";

    @Test
    void createRemascConfig() {
        RemascConfigFactory factory = new RemascConfigFactory(REMASC_FILE);

        Assertions.assertNotNull(factory);

        RemascConfig remascConfig = factory.createRemascConfig("devnet");
        Assertions.assertNotNull(remascConfig);
        Assertions.assertNotNull(remascConfig.getRskLabsAddress());
        Assertions.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRskLabsAddress());


        remascConfig = factory.createRemascConfig("regtest");
        Assertions.assertNotNull(remascConfig);
        Assertions.assertNotNull(remascConfig.getRskLabsAddress());
        Assertions.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRskLabsAddress());

        remascConfig = factory.createRemascConfig("main");
        Assertions.assertNotNull(remascConfig);
        Assertions.assertNotNull(remascConfig.getRskLabsAddress());
        Assertions.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRskLabsAddress());

        remascConfig = factory.createRemascConfig("testnet");
        Assertions.assertNotNull(remascConfig);
        Assertions.assertNotNull(remascConfig.getRskLabsAddress());
        Assertions.assertNotEquals(RskAddress.nullAddress(), remascConfig.getRskLabsAddress());
    }

    @Test
    void createRemascConfigInvalidFile() {
        Assertions.assertThrows(RemascException.class, () -> new RemascConfigFactory("NotAFile"));
    }

    @Test
    void createRemascConfigInvalidConfig() {
        RemascConfigFactory factory = new RemascConfigFactory(REMASC_FILE);
        Assertions.assertThrows(RemascException.class, () -> factory.createRemascConfig("fakeNet"));
    }
}
