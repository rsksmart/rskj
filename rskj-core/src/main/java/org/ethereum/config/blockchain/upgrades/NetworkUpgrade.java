/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package org.ethereum.config.blockchain.upgrades;

public enum NetworkUpgrade {
    GENESIS("genesis"),
    BAHAMAS("bahamas"),
    AFTER_BRIDGE_SYNC("afterBridgeSync"),
    ORCHID("orchid"),
    ORCHID_060("orchid060"),
    WASABI_100("wasabi100"),
    PAPYRUS_200("papyrus200"),
    TWOTOTHREE("twoToThree"),
    IRIS300("iris300"),
    HOP400("hop400"),
    FINGERROOT500("fingerroot500");

    private String name;

    NetworkUpgrade(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static NetworkUpgrade named(String networkUpgradeName) {
        for (NetworkUpgrade networkUpgrade : NetworkUpgrade.values()) {
            if (networkUpgrade.name.equals(networkUpgradeName)) {
                return networkUpgrade;
            }
        }

        throw new IllegalArgumentException(String.format("Unknown network upgrade %s", networkUpgradeName));
    }
}
