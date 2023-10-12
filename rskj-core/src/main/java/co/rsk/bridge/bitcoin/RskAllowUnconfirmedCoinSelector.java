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

package co.rsk.bridge.bitcoin;

import com.google.common.annotations.VisibleForTesting;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.wallet.CoinSelector;
import co.rsk.bitcoinj.wallet.CoinSelection;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Custom Coin selector :
 * All outputs are selectable (like AllowUnconfirmedCoinSelector). We don't know how many confirmations an output has, but we just add them to the wallet once they have a lot of confirmations
 * Sorts outputs just by hash number (ignores output value and number of confirmation) which produces a pseudo random order.
 * Created by mario on 05/10/2016.
 */
public class RskAllowUnconfirmedCoinSelector implements CoinSelector{

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        ArrayList<TransactionOutput> selected = new ArrayList<TransactionOutput>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        // TODO: Consider changing the wallets internal format to track just outputs and keep them ordered.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<TransactionOutput>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        // TODO: Take in network parameters when instanatiated, and then test against the current network. Or just have a boolean parameter for "give me everything"
        if (!target.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        for (TransactionOutput output : sortedOutputs) {
            if (total >= target.value) {
                break;
            }
            
            selected.add(output);
            total += output.getValue().value;
        }
        // Total may be lower than target here, if the given candidates were insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(total), selected);
    }

    @VisibleForTesting
    public void sortOutputs(List<TransactionOutput> outputs) {
        Collections.sort(outputs, new Comparator<TransactionOutput>() {
            @Override
            public int compare(TransactionOutput a, TransactionOutput b) {
                a.getIndex();
                BigInteger aHash = a.getParentTransactionHash().toBigInteger();
                BigInteger bHash = b.getParentTransactionHash().toBigInteger();
                int ret = aHash.compareTo(bHash);
                if(ret == 0) {
                    ret = Integer.compare(a.getIndex(), b.getIndex());
                }
                return ret;
            }
        });
    }
}
