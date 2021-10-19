/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
package co.rsk;

/**
 * This interface extends {@link AutoCloseable} interface, so that implementations should properly release their resources
 * when {@link AutoCloseable#close()} is triggered.
 *
 * Note that implementers of this interface are encouraged to make their close method idempotent.
 */
public interface NodeContext extends AutoCloseable {
    /**
     * Returns {@code true} if this context is already closed, otherwise - {@code false}.
     */
    boolean isClosed();
}
