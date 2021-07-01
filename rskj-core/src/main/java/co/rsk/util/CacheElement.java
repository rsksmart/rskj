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

package co.rsk.util;

/**
 * Created by mario on 09/09/2016.
 */
public class CacheElement<T> {

    private T value;
    private Long lastAccess;
    private Long timeToLive;

    public CacheElement(T value, Long timeToLive) {
        this.value = value;
        this.lastAccess = System.currentTimeMillis();
        this.timeToLive = timeToLive;
    }

    public T value() {
        return value;
    }

    public void updateLastAccess() {
        this.lastAccess = System.currentTimeMillis();
    }

    public Boolean hasExpired() {
        long now = System.currentTimeMillis();
        return (now - this.lastAccess) >= this.timeToLive;
    }
}
