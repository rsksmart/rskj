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

package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of a given state of the election
 * of an ABI function call by a series of known
 * and authorized electors.
 *
 * @author Ariel Mendelzon
 */
public class ABICallAuthorizer {
    private List<BtcECKey> authorizedKeys;

    public ABICallAuthorizer(List<BtcECKey> authorizedKeys) {
        this.authorizedKeys = authorizedKeys;
    }

    public boolean isAuthorized(BtcECKey key) {
        return authorizedKeys.contains(key);
    }

    public int getNumberOfAuthorizedKeys() {
        return authorizedKeys.size();
    }
}
