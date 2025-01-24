/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.rpc.modules.debug.trace;

public enum TracerType {
    CALL_TRACER("callTracer"), RSK_TRACER("rskTracer");

    private final String tracerName;
    TracerType(String tracerName) {
        this.tracerName = tracerName;
    }

    public static TracerType getTracerType(String tracerName) {
        for (TracerType tracerType : TracerType.values()) {
            if (tracerType.getTracerName().equalsIgnoreCase(tracerName)) {
                return tracerType;
            }
        }
        return null;
    }
    public String getTracerName() {
        return tracerName;
    }

    public TracerType getDefault() {
        return RSK_TRACER;
    }
}
