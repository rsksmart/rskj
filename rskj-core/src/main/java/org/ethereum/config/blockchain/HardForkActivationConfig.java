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
package org.ethereum.config.blockchain;

import com.typesafe.config.Config;

public class HardForkActivationConfig {
    private final int orchidActivationHeight;
    private final int secondForkActivationHeight;

    private static final String PROPERTY_ORCHID_NAME = "orchid";
    private static final String PROPERTY_SECOND_FORK_NAME = "secondFork";

    public HardForkActivationConfig(Config config) {
        // Default values for activation heights is zero
        this(
                config.hasPath(PROPERTY_ORCHID_NAME) ? config.getInt(PROPERTY_ORCHID_NAME) : 0,
                config.hasPath(PROPERTY_SECOND_FORK_NAME) ? config.getInt(PROPERTY_SECOND_FORK_NAME) : 0
        );
    }

    public HardForkActivationConfig(int orchidActivationHeight, int secondForkActivationHeight) {
        this.orchidActivationHeight = orchidActivationHeight;
        this.secondForkActivationHeight = secondForkActivationHeight;
    }

    public int getOrchidActivationHeight() {
        return orchidActivationHeight;
    }

    public int getSecondForkActivationHeight() {
        return secondForkActivationHeight;
    }
}
