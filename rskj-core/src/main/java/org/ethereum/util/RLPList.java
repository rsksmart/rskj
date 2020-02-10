/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.util;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 21.04.14
 */
public class RLPList extends RLPItem implements RLPElement {
    private final List<RLPElement> elements;

    public RLPList(byte[] rlpData, List<RLPElement> elements) {
        super(rlpData);
        this.elements = Collections.unmodifiableList(elements);
    }

    public int size() {
        return this.elements.size();
    }

    public RLPElement get(int n) {
        return this.elements.get(n);
    }

    public List<RLPElement> getElements() {
        return this.elements;
    }
}
