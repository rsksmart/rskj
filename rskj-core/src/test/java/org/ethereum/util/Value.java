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

package org.ethereum.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Class to encapsulate an object and provide utilities for conversion
 */
public class Value {

    private Object value;

    public Value(Object obj) {
        if (obj == null) {
            return;
        }

        if (obj instanceof Value) {
            this.value = ((Value) obj).asObj();
        } else {
            this.value = obj;
        }
    }

    /* *****************
     *      Convert
     * *****************/

    public Object asObj() {
        return value;
    }

    public List<Object> asList() {
        Object[] valueArray = (Object[]) value;
        return Arrays.asList(valueArray);
    }

    public String asString() {
        if (isBytes()) {
            return new String((byte[]) value);
        } else if (isString()) {
            return (String) value;
        }
        return "";
    }

    public byte[] asBytes() {
        if (isBytes()) {
            return (byte[]) value;
        } else if (isString()) {
            return asString().getBytes(StandardCharsets.UTF_8);
        }
        return ByteUtil.EMPTY_BYTE_ARRAY;
    }

    public Value get(int index) {
        if (isList()) {
            // Guard for OutOfBounds
            if (asList().size() <= index) {
                return new Value(null);
            }
            if (index < 0) {
                throw new RuntimeException("Negative index not allowed");
            }
            return new Value(asList().get(index));
        }
        // If this wasn't a slice you probably shouldn't be using this function
        return new Value(null);
    }

    /* *****************
     *      Checks
     * *****************/

    public boolean isList() {
        return value != null && value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive();
    }

    public boolean isString() {
        return value instanceof String;
    }

    public boolean isInt() {
        return value instanceof Integer;
    }

    public boolean isLong() {
        return value instanceof Long;
    }

    public boolean isBytes() {
        return value instanceof byte[];
    }

    // it's only if the isBytes() = true
    public boolean isReadableString() {

        int readableChars = 0;
        byte[] data = (byte[]) value;

        if (data.length == 1 && data[0] > 31 && data[0] < 126) {
            return true;
        }

        for (byte aData : data) {
            if (aData > 32 && aData < 126) {
                ++readableChars;
            }
        }

        return (double) readableChars / (double) data.length > 0.55;
    }

    public boolean isHashCode() {
        return this.asBytes().length == 32;
    }

    public boolean isNull() {
        return value == null;
    }

    public boolean isEmpty() {
        if (isNull()) {
            return true;
        }
        if (isBytes() && asBytes().length == 0) {
            return true;
        }
        if (isList() && asList().isEmpty()) {
            return true;
        }
        if (isString() && asString().equals("")) {
            return true;
        }

        return false;
    }

    public int length() {
        if (isList()) {
            return asList().size();
        } else if (isBytes()) {
            return asBytes().length;
        } else if (isString()) {
            return asString().length();
        }
        return 0;
    }

    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();

        if (isList()) {

            Object[] list = (Object[]) value;

            // special case - key/value node
            if (list.length == 2) {

                stringBuilder.append("[ ");

                Value key = new Value(list[0]);

                byte[] keyNibbles = CompactEncoder.binToNibblesNoTerminator(key.asBytes());
                String keyString = ByteUtil.nibblesToPrettyString(keyNibbles);
                stringBuilder.append(keyString);

                stringBuilder.append(",");

                Value val = new Value(list[1]);
                stringBuilder.append(val.toString());

                stringBuilder.append(" ]");
                return stringBuilder.toString();
            }
            stringBuilder.append(" [");

            for (int i = 0; i < list.length; ++i) {
                Value val = new Value(list[i]);
                if (val.isString() || val.isEmpty()) {
                    stringBuilder.append("'").append(val.toString()).append("'");
                } else {
                    stringBuilder.append(val.toString());
                }
                if (i < list.length - 1) {
                    stringBuilder.append(", ");
                }
            }
            stringBuilder.append("] ");

            return stringBuilder.toString();
        } else if (isEmpty()) {
            return "";
        } else if (isBytes()) {

            StringBuilder output = new StringBuilder();
            if (isHashCode()) {
                output.append(ByteUtil.toHexString(asBytes()));
            } else if (isReadableString()) {
                output.append("'");
                for (byte oneByte : asBytes()) {
                    if (oneByte < 16) {
                        output.append("\\x").append(ByteUtil.oneByteToHexString(oneByte));
                    } else {
                        output.append(Character.valueOf((char) oneByte));
                    }
                }
                output.append("'");
                return output.toString();
            }
            return ByteUtil.toHexString(this.asBytes());
        } else if (isString()) {
            return asString();
        }
        return "Unexpected type";
    }

}
