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

package co.rsk.rpc.modules.trace;

import co.rsk.core.RskAddress;

import java.util.Arrays;

public class CreationData {
    private final byte[] creationInput;
    private final byte[] createdCode;
    private final RskAddress createdAddress;

    public CreationData(byte[] creationInput, byte[] createdCode, RskAddress createdAddress) {
        this.creationInput = creationInput == null ? null : Arrays.copyOf(creationInput, creationInput.length);
        this.createdCode = createdCode == null ? null : Arrays.copyOf(createdCode, createdCode.length);
        this.createdAddress = createdAddress;
    }

    public byte[] getCreationInput() { return this.creationInput == null ? null : Arrays.copyOf(this.creationInput, this.creationInput.length); }

    public byte[] getCreatedCode() { return this.createdCode == null ? null : Arrays.copyOf(this.createdCode, this.createdCode.length); }

    public RskAddress getCreatedAddress() { return this.createdAddress; }
}
