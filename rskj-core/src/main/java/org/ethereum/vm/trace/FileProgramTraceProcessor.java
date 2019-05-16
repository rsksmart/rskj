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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FileProgramTraceProcessor implements ProgramTraceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FileProgramTraceProcessor.class);

    private final String databaseDir;
    private final String vmTraceDir;
    private final boolean vmTraceCompressed;

    public FileProgramTraceProcessor(String databaseDir, String vmTraceDir, boolean vmTraceCompressed) {
        this.databaseDir = databaseDir;
        this.vmTraceDir = vmTraceDir;
        this.vmTraceCompressed = vmTraceCompressed;
    }

    @Override
    public void processProgramTrace(ProgramTrace programTrace, Keccak256 txHash) {
        try {
            VMUtils.saveProgramTraceFile(txHash.toHexString(), programTrace, databaseDir, vmTraceDir, vmTraceCompressed);
        } catch (IOException e) {
            logger.error("Cannot write trace to file", e);
        }
    }
}
