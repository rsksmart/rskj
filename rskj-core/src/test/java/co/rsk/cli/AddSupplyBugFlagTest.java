/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.cli;

import co.rsk.config.ConfigLoader;
import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import co.rsk.config.RskSystemProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole chain from the command line to the setting the executor reads, so that "off unless
 * asked for" is verified where it is actually decided rather than only in the class that consumes it.
 */
class AddSupplyBugFlagTest {

    private RskSystemProperties propertiesFor(String... args) {
        RskCli rskCli = new RskCli();
        rskCli.load(args);
        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = rskCli.getCliArgs();
        return new RskSystemProperties(new ConfigLoader(cliArgs));
    }

    @Test
    void theFlagIsRecognisedOnTheCommandLine() {
        RskCli rskCli = new RskCli();
        rskCli.load(new String[]{"--regtest", "--add-supply-bug"});

        assertTrue(rskCli.getCliArgs().getFlags().contains(NodeCliFlags.ADD_SUPPLY_BUG));
    }

    @Test
    void theFlagIsAbsentWhenNotGiven() {
        RskCli rskCli = new RskCli();
        rskCli.load(new String[]{"--regtest"});

        assertFalse(rskCli.getCliArgs().getFlags().contains(NodeCliFlags.ADD_SUPPLY_BUG));
    }

    @Test
    void passingTheFlagTurnsTheSettingOn() {
        assertTrue(propertiesFor("--regtest", "--add-supply-bug").isSupplyBugEnabled());
    }

    /**
     * The property that matters most: no flag, no bug. Checked against a real configuration built
     * the way the node builds it, not against a stub.
     */
    @Test
    void withoutTheFlagTheSettingIsOff() {
        assertFalse(propertiesFor("--regtest").isSupplyBugEnabled());
    }

    @Test
    void withoutTheFlagTheSettingIsOffOnEveryNetwork() {
        assertFalse(propertiesFor("--main").isSupplyBugEnabled());
        assertFalse(propertiesFor("--testnet").isSupplyBugEnabled());
        assertFalse(propertiesFor("--regtest").isSupplyBugEnabled());
    }
}
