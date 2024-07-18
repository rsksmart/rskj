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

/**
 * A {@link PrintableBytes} is a sequence of <code>byte</code> values that
 * can be represented as a {@link String} value.
 */
public interface PrintableBytes extends ByteSequence {

    interface Formatter<T extends PrintableBytes> {
        String toFormattedString(@Nonnull T printableBytes, int off, int length);
    }

    String toPrintableString();

}
