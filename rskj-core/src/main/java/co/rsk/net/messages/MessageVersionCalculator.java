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

package co.rsk.net.messages;

import co.rsk.net.StatusResolver;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import java.util.function.IntSupplier;

public class MessageVersionCalculator {

    private final IntSupplier supplier;

    public MessageVersionCalculator(ActivationConfig activationConfig, StatusResolver statusResolver) {
        this.supplier = () -> {
            long bestBlock = statusResolver.currentStatusLenient().getBestBlockNumber();
            return activationConfig.getMessageVersionForHeight(bestBlock);
        };
    }

    public int get() {
        return this.supplier.getAsInt();
    }
}
