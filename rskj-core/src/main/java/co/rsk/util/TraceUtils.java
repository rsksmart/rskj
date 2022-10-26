/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

public class TraceUtils {
    public static final String MSG_ID = "peerMsgId";
    public static final String SESSION_ID = "peerSID";
    public static final String JSON_RPC_REQ_ID = "rpcReqID";
    public static final int MAX_ID_LENGTH = 15;

    private TraceUtils() {}

    public static String getRandomId(){
        return RandomStringUtils.randomAlphanumeric(MAX_ID_LENGTH);
    }

    public static String toId(String id) {
        return StringUtils.substring(id,0,MAX_ID_LENGTH);
    }
}
