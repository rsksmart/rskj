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

package co.rsk.net.discovery.table;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Created by mario on 21/02/17.
 */
public class OperationResult {
    private final boolean success;
    private final BucketEntry affectedEntry;


    public OperationResult(final boolean success, final BucketEntry affectedEntry) {
        this.success = success;
        this.affectedEntry = affectedEntry;
    }

    public boolean isSuccess() {
        return success;
    }

    public BucketEntry getAffectedEntry() {
        return affectedEntry;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("success", success)
                .append("affectedEntry", affectedEntry)
                .toString();
    }
}
