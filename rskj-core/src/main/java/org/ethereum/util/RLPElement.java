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

import javax.annotation.Nullable;

/**
 * Wrapper class for decoded elements from an RLP encoded byte array.
 *
 * @author Roman Mandeleil
 * @since 01.04.2014
 */
public interface RLPElement {

    /**
     * @implNote this function will return null when the RLP data is an empty byte array.
     * It's recommended to use {@link RLPElement#getRLPRawData()} and handle this distinction in the calling code
     * because RLP can't know if you intended to encode a null or an empty byte array.
     */
    @Nullable
    byte[] getRLPData();

    byte[] getRLPRawData();
}
