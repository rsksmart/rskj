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

package co.rsk.net;

import com.google.common.annotations.VisibleForTesting;

public class NodeMsgTraceInfo {
    private final String messageId;
    private final String sessionId;
    private final long creationTime;

    public NodeMsgTraceInfo(String messageId, String sessionId) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.creationTime = System.currentTimeMillis();
    }

    @VisibleForTesting
    NodeMsgTraceInfo(String messageId, String sessionId, long creationTimeMillis) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.creationTime = creationTimeMillis;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getLifeTimeInSeconds() {
        return (System.currentTimeMillis() - this.creationTime) / 1000;
    }

}
