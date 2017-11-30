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
import org.ethereum.core.AccountState;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.TypeConverter;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Used to validate transactions before relaying. This class is highly
 * coupled with TxHandlerImpl. Check that class before modifying. Think
 * twice before reusing.
 */
class TxValidator {

    private List<TxValidatorStep> validatorSteps = new LinkedList<>();
    private List<TxFilter> txFilters = new LinkedList<>();

    public TxValidator() {
        validatorSteps.add(new TxValidatorAccountStateValidator());
        validatorSteps.add(new TxValidatorNonceRangeValidator());
        validatorSteps.add(new TxValidatorGasLimitValidator());
        validatorSteps.add(new TxValidatorAccountBalanceValidator());
        validatorSteps.add(new TxValidatorMinimuGasPriceValidator());
        validatorSteps.add(new TxValidatorIntrinsicGasLimitValidator());

        txFilters.add(new TxFilterAccumCostFilter());
    }

    /**
     * Where the magic occurs, will filter out invalid txs, but still remember some of them
     */
    List<Transaction> filterTxs(Repository repository,
                                Blockchain blockchain,
                                List<Transaction> txs,
                                Map<String, TxTimestamp> knownTxs,
                                Map<String, TxsPerAccount> txsPerAccounts) {
        //FIXME(mmarquez): this method is quite coupled with TxHandlerImpl
        // but it should be fixed when NodeMessageHandler stops managing the wire txs
        // and related stuff
        List<Transaction> acceptedTxs = new LinkedList<>();


        for (Transaction tx : txs) {
            String hash = TypeConverter.toJsonHex(tx.getHash());

            if (knownTxs.containsKey(hash)) {
                continue;
            }
            knownTxs.put(hash, new TxTimestamp(tx, System.currentTimeMillis()));

            AccountState state = repository.getAccountState(tx.getSender());
            if (state == null) {
                state = new AccountState(BigInteger.ZERO, BigInteger.ZERO);
            }
            BigInteger blockGasLimit = BigIntegers.fromUnsignedByteArray(blockchain.getBestBlock().getGasLimit());
            BigInteger minimumGasPrice = BigIntegers.fromUnsignedByteArray(blockchain.getBestBlock().getMinimumGasPrice());
            long bestBlockNumber = blockchain.getBestBlock().getNumber();

            boolean valid = true;

            for (TxValidatorStep step : validatorSteps) {
                if (!step.validate(tx, state, blockGasLimit, minimumGasPrice, bestBlockNumber)) {
                    valid = false;
                    break;
                }
            }
            if (!valid) {
                continue;
            }

            String addr = TypeConverter.toJsonHex(tx.getSender());

            txsPerAccounts.computeIfAbsent(addr, key -> new TxsPerAccount());

            TxsPerAccount txsPerAccount = txsPerAccounts.get(addr);

            BigInteger nonce = new BigInteger(1, tx.getNonce());
            if (txsPerAccount.containsNonce(nonce)) {
                continue;
            }
            txsPerAccount.getTransactions().add(tx);

            for (TxFilter filter : txFilters) {
                txsPerAccount.setTransactions(filter.filter(state, txsPerAccount));
            }

            acceptedTxs.addAll(txsPerAccount.readyToBeSent(state.getNonce()));
        }

        return acceptedTxs;
    }
}
