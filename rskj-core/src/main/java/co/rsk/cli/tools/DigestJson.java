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
package co.rsk.cli.tools;

/**
 * JSONL field helpers shared by the consensus validation tools.
 */
final class DigestJson {

    private DigestJson() { }

    /**
     * Renders an exception as digest fields. A failure is a result to be recorded, not a reason
     * to abandon the range: each block is independent of the ones around it.
     */
    static String errorFields(Throwable e) {
        return String.format("\"errorType\":\"%s\",\"errorMessage\":%s,\"errorAt\":%s",
                e.getClass().getName(), quoteOrNull(e.getMessage()), quoteOrNull(firstOwnFrame(e)));
    }

    static String firstOwnFrame(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith("co.rsk.") || className.startsWith("org.ethereum.")) {
                return frame.toString();
            }
        }

        return null;
    }

    static String quoteOrNull(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"";
    }
}
