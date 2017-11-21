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

import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;

import java.util.List;

/**
 * Representation of a given state of the election
 * of an ABI function call by a series of known
 * and authorized electors.
 *
 * @author Ariel Mendelzon
 */
public class ABICallAuthorizer {
    private List<ECKey> authorizedKeys;

    public ABICallAuthorizer(List<ECKey> authorizedKeys) {
        this.authorizedKeys = authorizedKeys;
    }

    public boolean isAuthorized(Transaction tx) {
        if (tx.getSignature() == null)
            return false;

        return isAuthorized(getVoter(tx));
    }

    public boolean isAuthorized(ECKey key) {
        return authorizedKeys.contains(key);
    }

    public ECKey getVoter(Transaction tx) {
        if (tx.getSignature() == null)
            return null;

        return tx.getKey();
    }

    public int getNumberOfAuthorizedKeys() {
        return authorizedKeys.size();
    }

    public int getRequiredAuthorizedKeys() {
        return getNumberOfAuthorizedKeys() / 2 + 1;
    }
}
