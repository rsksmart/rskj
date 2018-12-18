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
package co.rsk.peg;

import org.ethereum.config.BlockchainConfig;

public class BridgeStorageConfiguration {
    private final boolean isUnlimitedWhitelistEnabled;
    private final boolean isMultikeyFederation;

    public BridgeStorageConfiguration(
            boolean isUnlimitedWhitelistEnabled,
            boolean isMultikeyFederation
    ) {
        this.isUnlimitedWhitelistEnabled = isUnlimitedWhitelistEnabled;
        this.isMultikeyFederation = isMultikeyFederation;
    }

    public boolean isUnlimitedWhitelistEnabled() {
        return isUnlimitedWhitelistEnabled;
    }

    public boolean isMultikeyFederation() {
        return isMultikeyFederation;
    }

    public static BridgeStorageConfiguration fromBlockchainConfig(BlockchainConfig config) {
        return new BridgeStorageConfiguration(
                config.isRskip87(),
                config.isRskipMultipleKeyFederateMembers()
        );
    }
}
