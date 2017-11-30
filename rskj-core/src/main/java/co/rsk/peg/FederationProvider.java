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

import java.util.List;

/**
 * Implementors of this interface must be able to provide
 * federation instances.
 *
 * @author Ariel Mendelzon
 */
public interface FederationProvider {
    // The currently "active" federation
    Federation getActiveFederation();
    // The currently "retiring" federation
    Federation getRetiringFederation();

    // The federations that are "live", that is, are still
    // operational. This should be the active federation
    // plus the retiring federation, if one exists
    List<Federation> getLiveFederations();
}
