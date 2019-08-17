/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.program.InternalTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SignatureException;
import java.util.Map;
import java.util.WeakHashMap;

public class SenderResolverVisitor implements TransactionVisitor<RskAddress> {

    private static final Logger logger = LoggerFactory.getLogger(SenderResolverVisitor.class);
    private static final Profiler profiler = ProfilerFactory.getInstance();

    private final Map<Transaction, RskAddress> senders = new WeakHashMap<>();

    @Override
    public RskAddress visit(Transaction transaction) {
        return senders.computeIfAbsent(transaction, this::extractSenderFromSignature);
    }

    @Override
    public RskAddress visit(RemascTransaction remascTransaction) {
        return RemascTransaction.REMASC_ADDRESS;
    }

    @Override
    public RskAddress visit(ImmutableTransaction immutableTransaction) {
        return visit((Transaction)immutableTransaction);
    }

    @Override
    public RskAddress visit(InternalTransaction internalTransaction) {
        return internalTransaction.getSender();
    }

    public RskAddress visit(ReversibleTransactionExecutor.UnsignedTransaction unsignedTransaction) {
        return unsignedTransaction.getSender();
    }

    private RskAddress extractSenderFromSignature(Transaction tx) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.KEY_RECOV_FROM_SIG);
        try {
            ECKey key = ECKey.signatureToKey(tx.getRawHash().getBytes(), tx.getSignature());
            return new RskAddress(key.getAddress());
        } catch (SignatureException e) {
            logger.error("Unable to extract signature for tx {}", tx.getHash(), e);
            return RskAddress.nullAddress();
        } finally {
            profiler.stop(metric);
        }
    }
}
