/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.rpc;

import org.ethereum.core.Block;

import co.rsk.util.HexUtils;

/**
 * Created by ajlopez on 17/01/2018.
 */

public class NewBlockFilter extends Filter {
    class NewBlockFilterEvent extends FilterEvent {
        public final Block b;

        NewBlockFilterEvent(Block b) {
            this.b = b;
        }

        @Override
        public String getJsonEventObject() {
            return HexUtils.toJsonHex(b.getHash().getBytes());
        }
    }

    @Override
    public void newBlockReceived(Block b) {
        add(new NewBlockFilterEvent(b));
    }
}

