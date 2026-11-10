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
package co.rsk.core.bc.supply;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;

/**
 * Reads the balance of an account in one particular view of the state.
 *
 * A supply delta is computed between two of these: the state as it was when a scope started,
 * and the state as it is when the scope ended. An account that does not exist in a view
 * reads as {@link Coin#ZERO}, which is what makes account creation and destruction fall out
 * of the arithmetic without being special cased.
 */
@FunctionalInterface
public interface BalanceLookup {
    Coin getBalance(RskAddress address);
}
