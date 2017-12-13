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

package co.rsk.core;

import org.ethereum.db.ByteArrayWrapper;

public class AccountData {

    private final byte[] address;
    private final byte[] privateKey;

    public AccountData(byte[] address, byte[] privateKey) {
        this.address = address;
        this.privateKey = privateKey;
    }

    public byte[] getAddress() {
        return address;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public boolean validatePrivateKey(byte[] privateKey) {
        return this.compare(this.privateKey, privateKey);
    }

    public boolean validateAddress(byte[] address) {
        return this.compare(this.address, address);
    }

    private boolean compare(byte[] valid, byte[] toCompare) {
        if(toCompare == null) {
            return false;
        }
        ByteArrayWrapper toValidate = new ByteArrayWrapper(toCompare);
        ByteArrayWrapper compare = new ByteArrayWrapper(valid);
        return compare.equals(toValidate);
    }
}
