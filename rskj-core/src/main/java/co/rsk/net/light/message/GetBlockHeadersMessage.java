/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageVisitor;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

public abstract class GetBlockHeadersMessage extends LightClientMessage {
    protected final long id;
    protected final boolean reverse;
    protected final int max;
    protected final int skip;

    public GetBlockHeadersMessage(long id, int max, int skip, boolean reverse) {
        this.id = id;
        this.max = max;
        this.skip = skip;
        this.reverse = reverse;
    }

    public GetBlockHeadersMessage(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        byte[] rlpMax = paramsList.get(2).getRLPData();
        this.max = rlpMax == null? 0 : BigIntegers.fromUnsignedByteArray(rlpMax).intValue();
        byte[] rlpSkip = paramsList.get(3).getRLPData();
        this.skip = rlpSkip == null? 0 : BigIntegers.fromUnsignedByteArray(rlpSkip).intValue();
        byte[] rlpReverse = paramsList.get(4).getRLPData();
        this.reverse = rlpReverse != null;
    }

    public long getId() {
        return this.id;
    }

    public int getSkip() {
        return skip;
    }

    public boolean isReverse() {
        return reverse;
    }

    public int getMax() {
        return max;
    }

    public abstract byte[] getEncoded();

    @Override
    public Class<?> getAnswerMessage() {
        return BlockHeadersMessage.class;
    }


    public abstract String toString();

    public abstract void accept(LightClientMessageVisitor v);
}
