/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package co.rsk.core.bc;

import co.rsk.core.types.bytes.Bytes;
import co.rsk.core.types.bytes.BytesSlice;
import org.ethereum.core.Block;

import javax.annotation.Nullable;

public class SuperBridgeEventResolver {

   public static byte[] resolveSuperBridgeEvent(Block parentSuperBlock) {
    return new byte[0];
   }

    public static boolean equalEvents(@Nullable byte[] event1, @Nullable byte[] event2) {
        if (event1 == null) {
            return event2 == null;
        }
        if (event2 == null) {
            return false;
        }
        return Bytes.equalBytes(Bytes.of(event1), Bytes.of(event2));
    }
}
