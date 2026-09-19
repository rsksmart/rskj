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
package co.rsk.core.bc.supply;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enforces supply conservation over one block: no block may increase the total of all account
 * balances.
 *
 * <p>The RSK peg is backed 1:1 by bitcoin. The Bridge contract holds the entire supply, and a peg-in
 * transfers value out of the Bridge's balance rather than creating it. A node that enforces this
 * detects any bug -- in the Bridge, in a precompile, in fee accounting, in the EVM itself -- whose
 * effect is to conjure native currency, regardless of how the bug was reached.
 *
 * <p>Two scopes are checked, and neither subsumes the other:
 *
 * <ul>
 *   <li><b>Per transaction</b> is the primary check. Block-level netting conceals a creation
 *       whenever one transaction mints an amount and another destroys the same amount: the block's
 *       {@code B} is then zero while two transactions are individually wrong.
 *   <li><b>Per block</b> is a cross-check. If the sum of the per-transaction {@code B} differs from
 *       the block's {@code B}, value moved outside any transaction -- for example in block-level
 *       bookkeeping performed outside the transaction loop. That discrepancy is itself a finding
 *       and is reported, though it does not by itself invalidate the block.
 * </ul>
 *
 * <p>An instance covers a single block execution and is not thread safe; the parallel execution path
 * uses one instance per sublist and merges the results.
 */
public class SupplyConservationCheck {

    private static final Logger logger = LoggerFactory.getLogger("supplyconservation");

    /** How many accounts to name in a log line before truncating. */
    private static final int MAX_LOGGED_ACCOUNTS = 32;

    private final Block block;

    /** Running sum of B over the transactions checked so far, for the block-level cross-check. */
    private Coin sumOfTransactionDeltas = Coin.ZERO;

    public SupplyConservationCheck(Block block) {
        this.block = block;
    }

    /**
     * Checks one transaction's scope and records its {@code B} for the later cross-check.
     *
     * @return the delta. The caller must reject the block if {@link SupplyDelta#isCreation()}.
     */
    public SupplyDelta checkTransaction(
            Transaction tx,
            int txIndex,
            Collection<RskAddress> modifiedAccounts,
            BalanceLookup before,
            BalanceLookup after) {
        return checkTransaction(tx, txIndex, SupplyDelta.between(modifiedAccounts, before, after));
    }

    /**
     * Checks a transaction's scope from an already computed delta, for callers that had to adjust it
     * -- for instance to model a fee that was debited here but will be credited in bulk later.
     */
    public SupplyDelta checkTransaction(Transaction tx, int txIndex, SupplyDelta delta) {
        sumOfTransactionDeltas = sumOfTransactionDeltas.add(delta.getBalance());

        if (delta.isCreation()) {
            logger.error(
                    "Supply conservation violated by tx [{}] (index {}) in block {} [{}]: {} weis created. Gained: {}",
                    tx.getHash(), txIndex, block.getNumber(), block.getPrintableHash(),
                    delta.getBalance().asBigInteger(), describe(delta.getGained()));
        } else if (delta.isDestruction()) {
            logger.warn(
                    "Tx [{}] (index {}) in block {} [{}] destroyed {} weis. Lost: {}",
                    tx.getHash(), txIndex, block.getNumber(), block.getPrintableHash(),
                    delta.getBalance().negate().asBigInteger(), describe(delta.getLost()));
        }

        return delta;
    }

    /**
     * Adds an already computed sum of per-transaction B values to the running total, for the
     * parallel path where the transactions were checked by the sublist executors.
     */
    public void recordTransactionDeltas(Coin sum) {
        sumOfTransactionDeltas = sumOfTransactionDeltas.add(sum);
    }

    public Coin getSumOfTransactionDeltas() {
        return sumOfTransactionDeltas;
    }

    /**
     * Checks the block scope, and cross-checks it against the sum of the per-transaction scopes.
     *
     * @return the delta. The caller must reject the block if {@link SupplyDelta#isCreation()}.
     */
    public SupplyDelta checkBlock(
            Collection<RskAddress> modifiedAccounts,
            BalanceLookup before,
            BalanceLookup after) {
        SupplyDelta delta = SupplyDelta.between(modifiedAccounts, before, after);

        if (delta.isCreation()) {
            logger.error(
                    "Supply conservation violated by block {} [{}]: {} weis created. Gained: {}",
                    block.getNumber(), block.getPrintableHash(),
                    delta.getBalance().asBigInteger(), describe(delta.getGained()));
        } else if (delta.isDestruction()) {
            logger.warn(
                    "Block {} [{}] destroyed {} weis. Lost: {}",
                    block.getNumber(), block.getPrintableHash(),
                    delta.getBalance().negate().asBigInteger(), describe(delta.getLost()));
        }

        if (!delta.getBalance().equals(sumOfTransactionDeltas)) {
            // Value moved outside any transaction. Reported, but not by itself a rejection: the
            // block-level B above already decides that.
            logger.error(
                    "Block {} [{}] moved value outside its transactions: block B = {} but the transactions sum to {}",
                    block.getNumber(), block.getPrintableHash(),
                    delta.getBalance().asBigInteger(), sumOfTransactionDeltas.asBigInteger());
        }

        return delta;
    }

    private static String describe(Map<RskAddress, Coin> accounts) {
        String rendered = accounts.entrySet().stream()
                .limit(MAX_LOGGED_ACCOUNTS)
                .map(e -> e.getKey() + "=" + e.getValue().asBigInteger())
                .collect(Collectors.joining(", "));

        if (accounts.size() > MAX_LOGGED_ACCOUNTS) {
            rendered += ", ... (" + (accounts.size() - MAX_LOGGED_ACCOUNTS) + " more)";
        }

        return "[" + rendered + "]";
    }
}
