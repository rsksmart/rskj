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

package co.rsk.rpc;

import co.rsk.rpc.modules.txpool.TxPoolModule;

public interface Web3TxPoolModule {

    default String txpool_content() {
        return getTxPoolModule().content();
    }

    default String txpool_inspect() {
        return getTxPoolModule().inspect();
    }

    default String txpool_status() {
        return getTxPoolModule().status();
    }

    TxPoolModule getTxPoolModule();
}
