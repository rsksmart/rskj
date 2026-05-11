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
package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

public sealed interface ParsedRawTransaction
        permits
        ParsedType0Transaction,
        ParsedType1Transaction,
        ParsedType2Transaction,
        ParsedType2RSKTransaction {

    TransactionTypePrefix typePrefix();

    byte[] nonce();

    byte[] gasLimit();

    RskAddress receiveAddress();

    Coin value();

    byte[] data();

    SignatureState signatureState();

    default byte chainId() {
        SignatureState state = signatureState();
        if (state instanceof SignedSignature signed) {
            return signed.chainId();
        }
        if (state instanceof UnsignedSignature unsigned) {
            return unsigned.chainId() == null ? 0 : unsigned.chainId();
        }
        return 0;
    }

    default ECDSASignature signature() {
        SignatureState state = signatureState();
        return state instanceof SignedSignature signed ? signed.signature() : null;
    }
}
