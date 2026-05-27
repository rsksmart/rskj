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
package org.ethereum.core.transaction;

import co.rsk.core.RskAddress;
import org.ethereum.crypto.signature.ECDSASignature;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

public record SetCodeAuthorization(BigInteger chainId, RskAddress address, byte[] nonce, ECDSASignature signature) {

    public SetCodeAuthorization(BigInteger chainId, RskAddress address, byte[] nonce, ECDSASignature signature) {
        this.chainId = Objects.requireNonNull(chainId, "chainId");
        this.address = Objects.requireNonNull(address, "address");
        this.nonce = Objects.requireNonNull(nonce, "nonce");
        this.signature = Objects.requireNonNull(signature, "signature");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SetCodeAuthorization that = (SetCodeAuthorization) o;
        return Objects.equals(chainId, that.chainId)
                && Objects.equals(address, that.address)
                && Objects.deepEquals(nonce, that.nonce)
                && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainId, address, Arrays.hashCode(nonce), signature);
    }
}
