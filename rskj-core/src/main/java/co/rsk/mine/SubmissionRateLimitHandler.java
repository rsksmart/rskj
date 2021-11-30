/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
package co.rsk.mine;

import co.rsk.config.MiningConfig;
import co.rsk.util.TimeProvider;
import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nonnull;
import java.util.Objects;

class SubmissionRateLimitHandler {

    private static final TimeProvider ZERO_TIME_PROVIDER = () -> 0L;
    private static final TimeProvider SYSTEM_TIME_PROVIDER = System::currentTimeMillis;

    private static final SubmissionRateLimitHandler DISABLED_HANDLER = new SubmissionRateLimitHandler(0L, ZERO_TIME_PROVIDER);

    private final TimeProvider timeProvider;

    private final long workSubmissionRateLimitInMills;

    private volatile long lastSubmittedAt;

    @Nonnull
    static SubmissionRateLimitHandler ofMiningConfig(@Nonnull MiningConfig miningConfig) {
        long workSubmissionRateLimitInMills = miningConfig.getWorkSubmissionRateLimitInMills();

        if (workSubmissionRateLimitInMills > 0) {
            return new SubmissionRateLimitHandler(workSubmissionRateLimitInMills, SYSTEM_TIME_PROVIDER);
        }
        return DISABLED_HANDLER;
    }

    @VisibleForTesting
    SubmissionRateLimitHandler(long workSubmissionRateLimitInMills, @Nonnull TimeProvider timeProvider) {
        this.workSubmissionRateLimitInMills = workSubmissionRateLimitInMills;
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    boolean isEnabled() {
        return workSubmissionRateLimitInMills > 0;
    }

    boolean isSubmissionAllowed() {
        if (!isEnabled()) {
            return true;
        }

        long now = timeProvider.currentTimeMillis();
        return now - lastSubmittedAt >= workSubmissionRateLimitInMills;
    }

    void onSubmit() {
        lastSubmittedAt = timeProvider.currentTimeMillis();
    }
}
