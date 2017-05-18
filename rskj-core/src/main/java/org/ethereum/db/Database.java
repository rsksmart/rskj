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

package org.ethereum.db;

/**
 * Ethereum generic database interface
 */
public interface Database {

    /**
     * Get value from database
     *
     * @param key for which to retrieve the value
     * @return the value for the given key
     */
    byte[] get(byte[] key);

    /**
     * Insert value into database
     *
     * @param key for the given value
     * @param value to insert
     */
    void put(byte[] key, byte[] value);

    /**
     * Delete key/value pair from database
     *
     * @param key for which to delete the value
     */
    void delete(byte[] key);

    void init();

    /**
     * Close the database connection
     */
    void close();
}
