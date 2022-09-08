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

package co.rsk.mine;

import co.rsk.config.TestSystemProperties;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;

/**
 * This class helps when running tests that should work both before and after a network upgrade.
 * To use it, you need to:
 * 1. Extend it
 * 2. Create a public constructor that receives a TestSystemProperties
 * 3. call super(config)
 * <p>
 * It will execute your test cases once per fork, with adequate configuration object arguments.
 */
public enum NetworkUpgrades {

    BAMBOO_PROPERTIES(new TestSystemProperties() {
        @Override
        public ActivationConfig getActivationConfig() {
            return ActivationConfigsForTest.genesis();
        }

        @Override
        public String toString() {
            return "Bamboo";
        }

        @Override
        public String projectVersionModifier() {
            return "Bamboo";
        }
    }),

    ORCHID_PROPERTIES(new TestSystemProperties() {
        @Override
        public ActivationConfig getActivationConfig() {
            return ActivationConfigsForTest.orchid();
        }

        @Override
        public String toString() {
            return "Orchid";
        }

        @Override
        public String projectVersionModifier() {
            return "Orchid";
        }
    });

    private final TestSystemProperties properties;

    NetworkUpgrades(TestSystemProperties testSystemProperties) {
        this.properties = testSystemProperties;
    }

    public TestSystemProperties getProperties() {
        return properties;
    }
}
