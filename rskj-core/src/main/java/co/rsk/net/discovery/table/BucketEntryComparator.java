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

import java.io.Serializable;
import java.util.Comparator;

/**
 * Created by mario on 21/02/17.
 */
public class BucketEntryComparator implements Comparator<BucketEntry>, Serializable {
    private static final long serialVersionUID = 2544618035650935907L;

    @Override
    public int compare(BucketEntry e1, BucketEntry e2) {
        long t1 = e1.lastSeen();
        long t2 = e2.lastSeen();
        return Long.compare(t1, t2);
    }
}
