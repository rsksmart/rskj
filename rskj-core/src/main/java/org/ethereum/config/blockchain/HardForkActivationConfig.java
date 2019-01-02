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
    private final int orchid060ActivationHeight;

    private static final String PROPERTY_ORCHID_NAME = "orchid";
    private static final String PROPERTY_ORCHID_060_NAME = "orchid060";

    public HardForkActivationConfig(Config config) {
        // If I don't have any config for orchidActivationHeight I will set it to 0
        this(
                config.hasPath(PROPERTY_ORCHID_NAME) ? config.getInt(PROPERTY_ORCHID_NAME) : 0,
                config.hasPath(PROPERTY_ORCHID_060_NAME) ? config.getInt(PROPERTY_ORCHID_060_NAME) : 0
        );
    }

    public HardForkActivationConfig(int orchidActivationHeight, int orchid060ActivationHeight) {
        this.orchidActivationHeight = orchidActivationHeight;
        this.orchid060ActivationHeight = orchid060ActivationHeight;
    }

    public int getOrchidActivationHeight() {
        return orchidActivationHeight;
    }

    /**
     * TODO(mc): Only Devnet knows about Orchid060 activation config.
     *           This is a quick solution but the whole HF activation needs work.
     *           E.g. we don't handle the case where Orchid060 < Orchid (fails at runtime).
     */
    public int getOrchid060ActivationHeight() {
        return orchid060ActivationHeight;
    }
}
