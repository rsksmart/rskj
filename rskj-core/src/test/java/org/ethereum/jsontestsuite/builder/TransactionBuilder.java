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

package org.ethereum.jsontestsuite.builder;

import org.ethereum.core.Transaction;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.jsontestsuite.model.TransactionTck;

import static org.ethereum.json.Utils.*;

public class TransactionBuilder {

    public static Transaction build(TransactionTck transactionTck) {

        final Transaction transaction;
        if (transactionTck.getSecretKey() != null) {

            transaction = Transaction.builder()
                    .nonce(parseVarData(transactionTck.getNonce()))
                    .gasPrice(parseVarData(transactionTck.getGasPrice()))
                    .gasLimit(parseVarData(transactionTck.getGasLimit()))
                    .destination(parseData(transactionTck.getTo()))
                    .value(parseVarData(transactionTck.getValue()))
                    .data(parseData(transactionTck.getData()))
                    .build();
            transaction.sign(parseData(transactionTck.getSecretKey()));

        } else {

            transaction = Transaction
                    .builder()
                    .nonce(parseNumericData(transactionTck.getNonce()))
                    .gasPrice(parseNumericData(transactionTck.getGasPrice()))
                    .gasLimit(parseVarData(transactionTck.getGasLimit()))
                    .destination(parseData(transactionTck.getTo()))
                    .data(parseData(transactionTck.getData()))
                    .value(parseNumericData(transactionTck.getValue()))
                    .build();
            transaction.setSignature(ECDSASignature.fromComponents(parseData(transactionTck.getR()), parseData(transactionTck.getS()), parseByte(transactionTck.getV())));
        }

        return transaction;
    }
}
