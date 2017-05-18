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

package co.rsk.config;

/**
 * Created by ajlopez on 3/3/2016.
 */
public class RskMiningConstants {
    public static final byte[] RSK_TAG = {'R','S','K','B','L','O','C','K',':'};
    public static final int MAX_BYTES_AFTER_MERGED_MINING_HASH = 128;

    public static final int BLOCK_HEADER_HASH_SIZE = 32;

    public static final int MIDSTATE_SIZE  = 52;
    public static final int MIDSTATE_SIZE_TRIMMED = 40;

    public static final int NOTIFY_FEES_PERCENTAGE_INCREASE = 10;

    private RskMiningConstants(){
    }
}
