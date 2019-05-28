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

import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BtcBlockStoreWithCache;
import org.ethereum.core.BlockFactory;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.trace.ProgramTrace;
import org.ethereum.vm.trace.ProgramTraceProcessor;

public class TestTransactionExecutorFactory extends TransactionExecutorFactory {
    public TestTransactionExecutorFactory(
            RskSystemProperties config,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockFactory blockFactory,
            ProgramInvokeFactory programInvokeFactory,
            BtcBlockStoreWithCache btcBlockStore) {
        super(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                programInvokeFactory,
                new DisabledProgramTraceProcessor(),
                new PrecompiledContracts(config, btcBlockStore));
    }

    public static class DisabledProgramTraceProcessor implements ProgramTraceProcessor {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void processProgramTrace(ProgramTrace programTrace, Keccak256 txHash) {
            throw new UnsupportedOperationException("Should never reach here");
        }
    }
}