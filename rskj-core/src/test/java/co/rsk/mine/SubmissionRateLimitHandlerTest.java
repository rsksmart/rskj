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
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class SubmissionRateLimitHandlerTest {

    @Test
    public void ofMiningConfig_WhenRateLimitIsZero_ThenHandlerShouldBeDisabled() {
        MiningConfig miningConfig = mock(MiningConfig.class);
        doReturn(0L).when(miningConfig).getWorkSubmissionRateLimitInMills();
        assertFalse(SubmissionRateLimitHandler.ofMiningConfig(miningConfig).isEnabled());
    }

    @Test
    public void ofMiningConfig_WhenRateLimitIsNegative_ThenHandlerShouldBeDisabled() {
        MiningConfig miningConfig = mock(MiningConfig.class);
        doReturn(-1L).when(miningConfig).getWorkSubmissionRateLimitInMills();
        assertFalse(SubmissionRateLimitHandler.ofMiningConfig(miningConfig).isEnabled());
    }

    @Test
    public void ofMiningConfig_WhenRateLimitIsPositive_ThenHandlerShouldBeEnabled() {
        MiningConfig miningConfig = mock(MiningConfig.class);
        doReturn(1L).when(miningConfig).getWorkSubmissionRateLimitInMills();
        assertTrue(SubmissionRateLimitHandler.ofMiningConfig(miningConfig).isEnabled());
    }

    @Test
    public void isSubmissionAllowed_WhenDisabled_ThenShouldReturnTrue() {
        TimeProvider timeProvider = mock(TimeProvider.class);
        SubmissionRateLimitHandler handler = new SubmissionRateLimitHandler(0L, timeProvider);
        assertTrue(handler.isSubmissionAllowed());
        verify(timeProvider, never()).currentTimeMillis();
    }

    @Test
    public void isSubmissionAllowed_WhenEnabledAndLimitNotExceeded_ThenShouldReturnTrue() {
        TimeProvider timeProvider = mock(TimeProvider.class);
        SubmissionRateLimitHandler handler = new SubmissionRateLimitHandler(1L, timeProvider);
        doReturn(1L).when(timeProvider).currentTimeMillis();
        assertTrue(handler.isSubmissionAllowed());

        doReturn(2L).when(timeProvider).currentTimeMillis();
        handler.onSubmit();
        doReturn(3L).when(timeProvider).currentTimeMillis();
        assertTrue(handler.isSubmissionAllowed());
    }

    @Test
    public void isSubmissionAllowed_WhenEnabledAndLimitExceeded_ThenShouldReturnFalse() {
        TimeProvider timeProvider = mock(TimeProvider.class);
        SubmissionRateLimitHandler handler = new SubmissionRateLimitHandler(1L, timeProvider);
        doReturn(1L).when(timeProvider).currentTimeMillis();
        assertTrue(handler.isSubmissionAllowed());

        doReturn(2L).when(timeProvider).currentTimeMillis();
        handler.onSubmit();
        doReturn(2L).when(timeProvider).currentTimeMillis();
        assertFalse(handler.isSubmissionAllowed());
    }
}
