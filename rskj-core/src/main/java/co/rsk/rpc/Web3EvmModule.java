/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.rpc.modules.evm.EvmModule;

public interface Web3EvmModule {
    default String evm_snapshot() {
        return getEvmModule().evm_snapshot();
    }

    default boolean evm_revert(String snapshotId) {
        return getEvmModule().evm_revert(snapshotId);
    }

    default void evm_reset() {
        getEvmModule().evm_reset();
    }

    default void evm_mine() {
        getEvmModule().evm_mine();
    }

    default void evm_minemany(String numberOfBlocks) {
        int nBlocks = 0;
        if ((numberOfBlocks==null) || (numberOfBlocks.equals(""))) {
            nBlocks = 1;
        } else
            nBlocks = Integer.parseInt(numberOfBlocks);

        for(int i=0;i<nBlocks;i++)
            getEvmModule().evm_mine();
    }
    default void evm_startMining() {
        getEvmModule().evm_startMining();
    }

    default void evm_stopMining() {
        getEvmModule().evm_stopMining();
    }

    default String evm_increaseTime(String seconds) {
        return getEvmModule().evm_increaseTime(seconds);
    }

    EvmModule getEvmModule();
}
