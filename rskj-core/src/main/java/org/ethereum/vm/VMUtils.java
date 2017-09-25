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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.*;

import static org.ethereum.config.SystemProperties.CONFIG;

public final class VMUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger("VM");

    private VMUtils() {
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
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

    private static final int BUF_SIZE = 4096;

    private static void write(InputStream in, OutputStream out, int bufSize) throws IOException {
        try {
            byte[] buf = new byte[bufSize];
            for (int count = in.read(buf); count != -1; count = in.read(buf)) {
                out.write(buf, 0, count);
            }
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        DeflaterOutputStream out = new DeflaterOutputStream(baos, new Deflater(), BUF_SIZE);

        write(in, out, BUF_SIZE);

        return baos.toByteArray();
    }

    public static byte[] compress(String content) throws IOException {
        return compress(content.getBytes("UTF-8"));
    }

    public static byte[] decompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);

        ByteArrayInputStream in = new ByteArrayInputStream(data);
        InflaterOutputStream out = new InflaterOutputStream(baos, new Inflater(), BUF_SIZE);

        write(in, out, BUF_SIZE);

        return baos.toByteArray();
    }
}
