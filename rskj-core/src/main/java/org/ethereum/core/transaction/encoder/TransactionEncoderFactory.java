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
package org.ethereum.core.transaction.encoder;

import org.ethereum.core.Transaction;

public class TransactionEncoderFactory {

    private static final TransactionEncoder TYPE_0_ENCODER = new Type0TransactionEncoder();
    private static final TransactionEncoder TYPE_1_ENCODER = new Type1TransactionEncoder();
    private static final TransactionEncoder TYPE_2_ENCODER = new Type2TransactionEncoder();
    private static final TransactionEncoder TYPE_2_RSK_ENCODER = new Type2RSKTransactionEncoder();
    private static final TransactionEncoder TYPE_4_ENCODER = new Type4TransactionEncoder();

    private TransactionEncoderFactory() {}

    public static TransactionEncoder getEncoder(Transaction transaction) {
        return switch (transaction.getType()) {
            case TYPE_1 -> TYPE_1_ENCODER;
            case TYPE_2 -> (transaction.isRskNamespaceTransaction()) ? TYPE_2_RSK_ENCODER : TYPE_2_ENCODER;
            case TYPE_4 -> TYPE_4_ENCODER;
            default -> TYPE_0_ENCODER;
        };
    }
}
