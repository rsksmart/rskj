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

package co.rsk.rpc.modules.eth;

import java.util.Optional;

/**
 * Created by ajlopez on 24/02/2021.
 */
public class GasFinder {
    private static final long DIFFERENCE = 1000L;
    private static final long UPWARD_STEP = 1_000_000L;
    private static final long TOP_GAS = 12_000_000L;

    private long lastGasUsed;
    private Optional<Long> lowerSuccess = Optional.empty();
    private Optional<Long> upperFailure = Optional.empty();

    public long nextTry() {
        if (this.upperFailure.isPresent() && this.lowerSuccess.isPresent()) {
            return (this.upperFailure.get() + this.lowerSuccess.get()) / 2;
        }

        if (!this.upperFailure.isPresent() && !this.lowerSuccess.isPresent() && this.lastGasUsed > 0) {
            long newGasToTry = this.lastGasUsed + UPWARD_STEP;

            if (newGasToTry > TOP_GAS) {
                throw new IllegalStateException("Too much gas to try");
            }

            return newGasToTry;
        }

        if (this.lastGasUsed > 0) {
            return this.lastGasUsed;
        }

        if (this.upperFailure.isPresent()) {
            return this.upperFailure.get() + UPWARD_STEP;
        }

        throw new IllegalStateException("No gas data");
    }

    public void registerSuccess(long gasLimit, long gasUsed) {
        this.lastGasUsed = gasUsed;

        if (!this.lowerSuccess.isPresent() || this.lowerSuccess.get() > gasLimit) {
            this.lowerSuccess = Optional.of(gasLimit);
        }
    }

    public void registerFailure(long gasLimit) {
        if (!this.upperFailure.isPresent() && !this.lowerSuccess.isPresent()) {
            this.lastGasUsed = gasLimit;
            return;
        }

        if (!this.upperFailure.isPresent() || this.upperFailure.get() < gasLimit) {
            this.upperFailure = Optional.of(gasLimit);
        }
    }

    public boolean wasFound() {
        if (!this.lowerSuccess.isPresent()) {
            return false;
        }

        if (this.lowerSuccess.get() == this.lastGasUsed) {
            return true;
        }

        if (!this.upperFailure.isPresent()) {
            return false;
        }

        long top = this.lowerSuccess.get();
        long bottom = this.upperFailure.get();

        return Math.abs(top - bottom) <= DIFFERENCE;
    }

    public long getGasFound() {
        if (!this.wasFound()) {
            throw new IllegalStateException("No gas found yet");
        }

        return this.lowerSuccess.get();
    }
}
