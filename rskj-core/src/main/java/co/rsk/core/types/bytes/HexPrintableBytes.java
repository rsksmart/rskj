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

package co.rsk.core.types.bytes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A {@link HexPrintableBytes} is an extension of the {@link PrintableBytes} class with capabilities to
 * represent it in hexadecimal format.
 */
public interface HexPrintableBytes extends PrintableBytes {

    Formatter<HexPrintableBytes> SIMPLE_HEX_FORMATTER = new PrintableBytesHexFormatter();
    Formatter<HexPrintableBytes> SIMPLE_JSON_HEX_FORMATTER = new PrintableBytesJsonHexFormatter();


    @Nullable
    static String toHexString(@Nullable HexPrintableBytes bytes, @Nullable String defaultValue) {
        if (bytes == null) {
            return defaultValue;
        }
        return bytes.toHexString();
    }

    @Nullable
    static String toHexString(@Nullable HexPrintableBytes bytes) {
        return toHexString(bytes, null);
    }

    default String toPrintableString(@Nonnull Formatter<HexPrintableBytes> formatter, int off, int length) {
        return formatter.toFormattedString(this, off, length);
    }

    default String toPrintableString(@Nonnull Formatter<HexPrintableBytes> formatter) {
        return toPrintableString(formatter, 0, length());
    }

    @Override
    default String toPrintableString() {
        return toPrintableString(SIMPLE_HEX_FORMATTER);
    }

    default String toJsonHexFormattedString() {
        return toPrintableString(SIMPLE_JSON_HEX_FORMATTER);
    }

    String toHexString(int off, int length);

    String toHexString();
}

class PrintableBytesHexFormatter implements PrintableBytes.Formatter<HexPrintableBytes> {

    @Override
    public String toFormattedString(@Nonnull HexPrintableBytes printableBytes, int off, int length) {
        int bytesLen = Objects.requireNonNull(printableBytes).length();
        if (off + length > bytesLen) {
            throw new IndexOutOfBoundsException("invalid 'off' and/or 'length': " + off + "; " + length);
        }

        if (length > 32) {
            return printableBytes.toHexString(off, 15) + ".." + printableBytes.toHexString(off + length - 15, 15);
        }
        return printableBytes.toHexString(off, length);
    }
}

class PrintableBytesJsonHexFormatter extends PrintableBytesHexFormatter {

    @Override
    public String toFormattedString(@Nonnull HexPrintableBytes printableBytes, int off, int length) {
        return "0x" + super.toFormattedString(printableBytes, off, length);
    }
}
