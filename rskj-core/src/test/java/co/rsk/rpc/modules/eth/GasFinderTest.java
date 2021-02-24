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

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 24/02/2021.
 */
public class GasFinderTest {
    @Test
    public void throwsIllegalOperationExceptionOnNextTryWithoutData() {
        GasFinder gasFinder = new GasFinder();

        try {
            gasFinder.nextTry();
            Assert.fail();
        }
        catch (IllegalStateException ex) {
            Assert.assertEquals("No gas data", ex.getMessage());
        }
    }

    @Test
    public void registerFirstSuccessfulGasAndGetNextTry() {
        GasFinder gasFinder = new GasFinder();

        gasFinder.registerSuccess(1000000L, 100000L);

        Assert.assertEquals(100000L, gasFinder.nextTry());
        Assert.assertFalse(gasFinder.wasFound());
    }

    @Test
    public void registerSuccessAndFailure() {
        GasFinder gasFinder = new GasFinder();

        gasFinder.registerSuccess(1000000L, 100000L);
        gasFinder.registerFailure(100000L);

        Assert.assertEquals((1000000L + 100000L) / 2, gasFinder.nextTry());
        Assert.assertFalse(gasFinder.wasFound());
    }

    @Test
    public void registerSuccessAndSuccessWithGasUsedFirstTime() {
        GasFinder gasFinder = new GasFinder();

        gasFinder.registerSuccess(1000000L, 100000L);
        gasFinder.registerSuccess(100000L, 100000L);

        Assert.assertTrue(gasFinder.wasFound());
        Assert.assertEquals(100000L, gasFinder.getGasFound());
    }

    @Test
    public void wasNotFoundAtTheBeginning() {
        GasFinder gasFinder = new GasFinder();

        Assert.assertFalse(gasFinder.wasFound());
    }

    @Test
    public void getGasFoundWithoutData() {
        GasFinder gasFinder = new GasFinder();

        try {
            gasFinder.getGasFound();
            Assert.fail();
        }
        catch (IllegalStateException ex) {
            Assert.assertEquals("No gas found yet", ex.getMessage());
        }
    }

    @Test
    public void wasFound() {
        GasFinder gasFinder = new GasFinder();

        gasFinder.registerSuccess(2000, 1000);
        gasFinder.registerFailure(1000);

        Assert.assertTrue(gasFinder.wasFound());
        Assert.assertEquals(2000L, gasFinder.getGasFound());
    }

    @Test
    public void firstFailureNextTry() {
        GasFinder gasFinder = new GasFinder();

        gasFinder.registerFailure(1000L);

        Assert.assertFalse(gasFinder.wasFound());
        Assert.assertTrue(gasFinder.nextTry() > 1000L);
    }

    @Test
    public void firstFailureThenSuccess() {
        GasFinder gasFinder = new GasFinder();

        gasFinder.registerFailure(1000L);
        gasFinder.registerSuccess(1001000L, 2000L);

        Assert.assertFalse(gasFinder.wasFound());
        Assert.assertEquals(2000L, gasFinder.nextTry());
    }
}
