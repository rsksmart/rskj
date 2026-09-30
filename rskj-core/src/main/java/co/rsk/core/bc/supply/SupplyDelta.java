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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The net change in the total of all account balances produced by a scope -- one transaction,
 * or one block.
 *
 * <p>Over every account the scope modified:
 *
 * <pre>
 *   inflow  = SUM max(0, balance_after - balance_before)
 *   outflow = SUM max(0, balance_before - balance_after)
 *   B       = inflow - outflow
 * </pre>
 *
 * <p>Only modified accounts are considered. Accounts that were merely read are excluded, so an
 * ordinary block does not report spurious changes. An account destroyed within the scope and not
 * re-created has {@code balance_after = 0}; one destroyed and re-created is accounted once, at its
 * final balance, because it appears once in the modified set and is read once from each view.
 *
 * <p>{@code B > 0} means native currency was created and the block must be rejected.
 * {@code B < 0} means it was destroyed, which is legitimate (a contract self-destructing to itself,
 * or a burn of fees) and is only worth logging. {@code B == 0} is conservation.
 */
public final class SupplyDelta {

    private static final SupplyDelta CONSERVED =
            new SupplyDelta(Coin.ZERO, Coin.ZERO, Collections.emptyMap(), Collections.emptyMap());

    private final Coin inflow;
    private final Coin outflow;

    /** Accounts whose balance rose, mapped to the amount gained (always positive). */
    private final Map<RskAddress, Coin> gained;

    /** Accounts whose balance fell, mapped to the amount lost (always positive). */
    private final Map<RskAddress, Coin> lost;

    private SupplyDelta(Coin inflow, Coin outflow, Map<RskAddress, Coin> gained, Map<RskAddress, Coin> lost) {
        this.inflow = inflow;
        this.outflow = outflow;
        this.gained = gained;
        this.lost = lost;
    }

    /**
     * Computes the delta over {@code modifiedAccounts}, reading each account's balance from the
     * view held before the scope ran and from the view held after it ran.
     */
    public static SupplyDelta between(
            Collection<RskAddress> modifiedAccounts,
            BalanceLookup before,
            BalanceLookup after) {
        if (modifiedAccounts.isEmpty()) {
            return CONSERVED;
        }

        Coin inflow = Coin.ZERO;
        Coin outflow = Coin.ZERO;
        Map<RskAddress, Coin> gained = new LinkedHashMap<>();
        Map<RskAddress, Coin> lost = new LinkedHashMap<>();

        for (RskAddress address : modifiedAccounts) {
            Coin balanceBefore = before.getBalance(address);
            Coin balanceAfter = after.getBalance(address);
            int change = balanceAfter.compareTo(balanceBefore);

            if (change > 0) {
                Coin amount = balanceAfter.subtract(balanceBefore);
                inflow = inflow.add(amount);
                gained.put(address, amount);
            } else if (change < 0) {
                Coin amount = balanceBefore.subtract(balanceAfter);
                outflow = outflow.add(amount);
                lost.put(address, amount);
            }
            // change == 0: the account was modified in some other way (nonce, storage, code).
            // It contributes nothing to either side.
        }

        return new SupplyDelta(inflow, outflow, gained, lost);
    }

    /**
     * Returns this delta with {@code amount} added to its inflow side, for currency that the scope
     * is owed but has not yet been credited.
     *
     * <p>This exists for the parallel execution path, where gas is debited from the sender inside
     * the transaction but the matching credit to the fee recipient is postponed and applied in bulk
     * later (see {@code postponeFeePayment}). Without modelling the fee explicitly, every ordinary
     * transaction would report a burn and the crediting step would be indistinguishable from a mint.
     */
    public SupplyDelta withPendingInflow(Coin amount) {
        if (amount.compareTo(Coin.ZERO) <= 0) {
            return this;
        }

        // A postponed credit can only offset currency that actually left an account in this scope,
        // so it is capped at the measured outflow. In a well formed scope the cap never binds:
        // B = inflow - outflow is -amount when conserved, and at most inflow when currency was
        // created, so outflow >= amount either way. It matters when the debit is absent, where an
        // uncapped credit would turn a conserved scope into an apparent creation -- a false
        // rejection, which on a consensus rule would fork this node off the network.
        Coin credited = amount.compareTo(outflow) > 0 ? outflow : amount;

        if (credited.compareTo(Coin.ZERO) == 0) {
            return this;
        }

        return new SupplyDelta(inflow.add(credited), outflow, gained, lost);
    }

    /**
     * Returns this delta with {@code amount} added to its outflow side, for currency credited
     * within this scope that was already accounted as pending inflow by an earlier one.
     */
    public SupplyDelta withSettledInflow(Coin amount) {
        if (amount.compareTo(Coin.ZERO) == 0) {
            return this;
        }
        return new SupplyDelta(inflow, outflow.add(amount), gained, lost);
    }

    /** {@code B}: positive if currency was created, negative if destroyed, zero if conserved. */
    public Coin getBalance() {
        return inflow.subtract(outflow);
    }

    public Coin getInflow() {
        return inflow;
    }

    public Coin getOutflow() {
        return outflow;
    }

    /** True when currency appeared from nowhere. The only condition that invalidates a block. */
    public boolean isCreation() {
        return getBalance().compareTo(Coin.ZERO) > 0;
    }

    /** True when currency was destroyed. Legitimate, but logged. */
    public boolean isDestruction() {
        return getBalance().compareTo(Coin.ZERO) < 0;
    }

    public boolean isConserved() {
        return getBalance().compareTo(Coin.ZERO) == 0;
    }

    public Map<RskAddress, Coin> getGained() {
        return Collections.unmodifiableMap(gained);
    }

    public Map<RskAddress, Coin> getLost() {
        return Collections.unmodifiableMap(lost);
    }

    public static SupplyDelta conserved() {
        return CONSERVED;
    }

    public SupplyDelta add(SupplyDelta other) {
        return new SupplyDelta(
                inflow.add(other.inflow),
                outflow.add(other.outflow),
                gained,
                lost);
    }

    @Override
    public String toString() {
        return "B=" + getBalance().asBigInteger()
                + " (inflow=" + inflow.asBigInteger()
                + ", outflow=" + outflow.asBigInteger() + ")";
    }
}
