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

package co.rsk.net.handler;

import co.rsk.net.handler.txvalidator.*;
import org.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

/**
 * Validator for using in pending state.
 *
 * Add/remove checks here.
 */
public class TxPendingValidator {

    private List<TxValidatorStep> validatorSteps = new LinkedList<>();

    public TxPendingValidator() {
        validatorSteps.add(new TxNotNullValidator());
        validatorSteps.add(new TxValidatorNotRemascTxValidator());
        validatorSteps.add(new TxValidatorGasLimitValidator());
    }

    public boolean isValid(Transaction tx, BigInteger gasLimit) {
        return validatorSteps.stream()
                .allMatch(v -> v.validate(tx, null, gasLimit, null, 0, false));
    }
}
