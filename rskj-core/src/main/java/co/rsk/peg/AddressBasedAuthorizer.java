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

import co.rsk.core.commons.RskAddress;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;

import java.util.Arrays;
import java.util.List;

/**
 * Authorizes an operation based
 * on an RSK address.
 *
 * @author Ariel Mendelzon
 */
public class AddressBasedAuthorizer {
    public enum MinimumRequiredCalculation { ONE, MAJORITY, ALL };

    private List<ECKey> authorizedKeys;
    private MinimumRequiredCalculation requiredCalculation;

    public AddressBasedAuthorizer(List<ECKey> authorizedKeys, MinimumRequiredCalculation requiredCalculation) {
        this.authorizedKeys = authorizedKeys;
        this.requiredCalculation = requiredCalculation;
    }

    public boolean isAuthorized(RskAddress sender) {
        return authorizedKeys.stream()
                .map(key -> key.getAddress())
                .anyMatch(address -> Arrays.equals(address, sender.getBytes()));
    }

    public boolean isAuthorized(Transaction tx) {
        return isAuthorized(tx.getSender());
    }

    public int getNumberOfAuthorizedKeys() {
        return authorizedKeys.size();
    }

    public int getRequiredAuthorizedKeys() {
        switch (requiredCalculation) {
            case ONE:
                return 1;
            case MAJORITY:
                return getNumberOfAuthorizedKeys() / 2 + 1;
            case ALL:
            default:
                return getNumberOfAuthorizedKeys();
        }
    }
}
