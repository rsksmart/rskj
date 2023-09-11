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

package co.rsk.rpc.modules.trace;

import org.ethereum.vm.MessageCall;

public enum CallType {
    NONE,
    CALL,
    CALLCODE,
    DELEGATECALL,
    STATICCALL,
    DELEGATECALL2;

    public static CallType fromMsgType(MessageCall.MsgType msgType) {
        switch (msgType) {
            case CALL:
                return CALL;
            case CALLCODE:
                return CALLCODE;
            case DELEGATECALL:
                return DELEGATECALL;
            case STATICCALL:
                return STATICCALL;
            case DELEGATECALL2:
                return DELEGATECALL2;
            default:
                return NONE;
        }
    }
}
