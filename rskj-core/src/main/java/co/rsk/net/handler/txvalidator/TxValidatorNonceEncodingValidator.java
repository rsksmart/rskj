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

package co.rsk.net.handler.txvalidator;

import co.rsk.core.Coin;
import co.rsk.net.TransactionValidationResult;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Validates that the transaction nonce uses canonical encoding:
 * - The byte representation must fit in a long (at most 8 bytes).
 * - There must be no unnecessary leading zero bytes (e.g. [0x00, 0x01] is
 *   rejected because the canonical form is [0x01]).
 *
 * This prevents malformed nonce encodings from reaching downstream code
 * (such as {@link org.ethereum.util.ByteUtil#byteArrayToLong}) that assumes
 * the nonce fits in 8 bytes.
 */
public class TxValidatorNonceEncodingValidator implements TxValidatorStep {

    public static boolean hasCanonicalEncoding(byte[] nonce) {
        if (nonce == null) {
            return true;
        }
        if (nonce.length > Long.BYTES) {
            return false;
        }
        if (nonce.length > 1 && nonce[0] == 0) {
            return false;
        }
        return true;
    }

    @Override
    public TransactionValidationResult validate(Transaction tx, @Nullable AccountState state, BigInteger gasLimit, Coin minimumGasPrice, long bestBlockNumber, boolean isFreeTx) {
        byte[] nonce = tx.getNonce();

        if (hasCanonicalEncoding(nonce)) {
            return TransactionValidationResult.ok();
        }

        if (nonce.length > Long.BYTES) {
            return TransactionValidationResult.withError("transaction nonce byte length exceeds maximum");
        }

        return TransactionValidationResult.withError("transaction nonce has non-canonical encoding");
    }
}
