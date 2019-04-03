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

package org.ethereum.vm.trace;

import co.rsk.crypto.Keccak256;
import org.ethereum.vm.VMUtils;

import java.io.IOException;

/**
 * Created by ajlopez on 09/04/2019.
 */
public class FileProgramTraceProcessor implements ProgramTraceProcessor {
    private final boolean vmTrace;
    private final String databaseDir;
    private final String vmTraceDir;
    private final boolean vmTraceCompressed;

    public FileProgramTraceProcessor(boolean vmTrace, String databaseDir, String vmTraceDir, boolean vmTraceCompressed) {
        this.vmTrace = vmTrace;
        this.databaseDir = databaseDir;
        this.vmTraceDir = vmTraceDir;
        this.vmTraceCompressed = vmTraceCompressed;
    }

    @Override
    public boolean enabled() {
        return this.vmTrace;
    }

    @Override
    public void processProgramTrace(ProgramTrace programTrace, Keccak256 txHash)  throws IOException {
        if (!this.vmTrace) {
            return;
        }

        VMUtils.saveProgramTraceFile(txHash.toHexString(), programTrace, databaseDir, vmTraceDir, vmTraceCompressed);
    }
}
