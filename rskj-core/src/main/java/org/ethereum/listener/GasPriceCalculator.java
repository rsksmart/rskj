/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package org.ethereum.listener;

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;

import java.util.List;
import java.util.Optional;

public interface GasPriceCalculator {
    public enum GasCalculatorType {
        PLAIN_PERCENTILE,
        WEIGHTED_PERCENTILE;

        public static GasCalculatorType fromString(String type) {
            if (type == null) {
                return null;
            }
            switch (type.toLowerCase()) {
                case "weighted_percentile":
                    return WEIGHTED_PERCENTILE;
                case "plain_percentile":
                    return PLAIN_PERCENTILE;
                default:
                    return null;
            }
        }
    }

    Optional<Coin> getGasPrice();
    void onBlock(Block block, List<TransactionReceipt> receipts);

    GasCalculatorType getType();
}
