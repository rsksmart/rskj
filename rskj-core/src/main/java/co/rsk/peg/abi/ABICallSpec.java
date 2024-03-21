/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg.abi;

import com.google.common.primitives.SignedBytes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Immutable representation of a function call
 * spec to any given contract.
 * For simplicity, each of the arguments is assumed to be a byte array.
 * Encoding is up to the user.
 *
 * @author Ariel Mendelzon
 */
public final class ABICallSpec {
    public static final Comparator<ABICallSpec> byBytesComparator = new Comparator<ABICallSpec>() {
        @Override
        public int compare(ABICallSpec specA, ABICallSpec specB) {
            return SignedBytes.lexicographicalComparator().compare(
                    specA.getEncoded(),
                    specB.getEncoded()
            );
        }
    };

    private String function;
    private byte[][] arguments;

    public ABICallSpec(String function, byte[][] arguments) {
        this.function = function;
        // Keep internal copies, so that the instance
        // is immutable
        this.arguments = copy(arguments);
    }

    public String getFunction() {
        return function;
    }

    public byte[][] getArguments() {
        return copy(arguments);
    }

    public byte[] getEncoded() {
        byte[] functionBytes = function.getBytes(StandardCharsets.UTF_8);
        int totalLength = functionBytes.length;
        for (int i = 0; i < arguments.length; i++) {
            totalLength += arguments[i].length;
        }
        byte[] result = new byte[totalLength];
        System.arraycopy(functionBytes, 0, result, 0, functionBytes.length);
        int offset = functionBytes.length;
        for (int i = 0; i < arguments.length; i++) {
            System.arraycopy(arguments[i], 0, result, offset, arguments[i].length);
            offset += arguments[i].length;
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("Call to %s with %d arguments", function, arguments.length);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        ABICallSpec otherSpec = ((ABICallSpec) other);
        return otherSpec.getFunction().equals(getFunction()) &&
                areEqual(arguments, otherSpec.arguments);
    }

    @Override
    public int hashCode() {
        int[] argumentsHashes = Arrays.stream(arguments).map(argument -> Arrays.hashCode(argument)).mapToInt(Integer::intValue).toArray();
        return Objects.hash(function, Arrays.hashCode(argumentsHashes));
    }

    private boolean areEqual(byte[][] first, byte[][] second) {
        if (first.length != second.length) {
            return false;
        }

        for (int i = 0; i < first.length; i++) {
            if (!Arrays.equals(first[i], second[i])) {
                return false;
            }
        }

        return true;
    }

    private byte[][] copy(byte[][] array) {
        byte[][] result = new byte[array.length][];
        for (int i = 0; i < array.length; i++) {
            result[i] = Arrays.copyOf(array[i], array[i].length);
        }
        return result;
    }
}
