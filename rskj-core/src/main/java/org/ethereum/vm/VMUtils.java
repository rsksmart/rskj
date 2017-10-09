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

package org.ethereum.vm;

import org.ethereum.vm.trace.ProgramTrace;
import org.ethereum.vm.trace.Serializers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.*;

import static org.ethereum.config.SystemProperties.CONFIG;

public final class VMUtils {
    private VMUtils() {
    }

    public static void saveProgramTraceFile(Path basePath, String txHash, boolean compress, ProgramTrace trace) throws IOException {
        if (compress) {
            try(final FileOutputStream fos = new FileOutputStream(basePath.resolve(txHash + ".zip").toFile());
                final ZipOutputStream zos = new ZipOutputStream(fos)
            ) {
                ZipEntry zipEntry = new ZipEntry(txHash + ".json");
                zos.putNextEntry(zipEntry);
                Serializers.serializeFieldsOnly(trace, true, zos);
            }
        } else {
            try (final OutputStream out = Files.newOutputStream(basePath.resolve(txHash + ".json"))) {
                Serializers.serializeFieldsOnly(trace, true, out);
            }
        }
    }

    public static void saveProgramTraceFile(String txHash, boolean compress, ProgramTrace trace) throws IOException {
        Path tracePath = Paths.get(CONFIG.databaseDir(), CONFIG.vmTraceDir());
        File traceDir = tracePath.toFile();
        if (!traceDir.exists()) {
            traceDir.mkdirs();
        }
        saveProgramTraceFile(tracePath, txHash, compress, trace);
    }
}
