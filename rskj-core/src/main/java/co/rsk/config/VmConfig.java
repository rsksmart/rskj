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

package co.rsk.config;

import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.Block;

/**
 * Wraps configuration for the VM, which is usually derived from configuration files.
 */
public class VmConfig {

    private final boolean vmTrace;
    private final int vmTraceInitStorageLimit;
    private final int dumpBlock;
    private final String dumpStyle;
    private final BlockchainNetConfig blockchainNetConfig;

    public VmConfig(
            boolean vmTrace,
            int vmTraceInitStorageLimit,
            int dumpBlock,
            String dumpStyle,
            final BlockchainNetConfig blockchainNetConfig) {
        this.vmTrace = vmTrace;
        this.vmTraceInitStorageLimit = vmTraceInitStorageLimit;
        this.dumpBlock = dumpBlock;
        this.dumpStyle = dumpStyle;
        this.blockchainNetConfig = blockchainNetConfig;
    }

    public int dumpBlock() {
        return dumpBlock;
    }

    public String dumpStyle() {
        return dumpStyle;
    }

    public boolean vmTrace() {
        return vmTrace;
    }

    public int vmTraceInitStorageLimit() {
        return vmTraceInitStorageLimit;
    }

    public BlockchainNetConfig getBlockchainNetConfig() {
        return this.blockchainNetConfig;
    }
}