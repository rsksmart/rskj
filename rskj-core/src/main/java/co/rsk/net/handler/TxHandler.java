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

package co.rsk.net.handler;

import org.ethereum.core.Transaction;

import java.util.List;

/**
 *  Filter, holds and deletes transactions when handling messages
 *
 */
public interface TxHandler {
    /**
     * Filters transactions that aren't ready to be added to a block or
     * relayed, but it keeps them and may retrieve them later.
     *
     * Will return only txs that immediately follow the next nonce of the account
     * Transaction will have to satisfy other requirements.
     *
     * @param txs New received transactions
     * @return It may return transactions that weren't passed as parameter
     *         and it may filter out some
     */
    List<Transaction> retrieveValidTxs(List<Transaction> txs);

}
