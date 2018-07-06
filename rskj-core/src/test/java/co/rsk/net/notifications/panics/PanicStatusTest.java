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

package co.rsk.net.notifications.panics;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PanicStatusTest {
    private PanicStatus status;

    @Before
    public void setup() {
        status = new PanicStatus();
    }

    @Test
    public void notInPanic() {
        Assert.assertFalse(status.inPanic());
        Assert.assertEquals(0, status.getFlags().size());
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FROZEN));
    }

    @Test
    public void setUnset_1() {
        status.set(PanicFlag.NodeEclipsed(123));
        Assert.assertTrue(status.inPanic());
        Assert.assertTrue(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FROZEN));
        Assert.assertEquals(1, status.getFlags().size());
        Assert.assertEquals(123, status.get(PanicFlag.Reason.NODE_ECLIPSED).getSinceBlockNumber());

        status.unset(PanicFlag.of(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertFalse(status.inPanic());
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FROZEN));
        Assert.assertEquals(0, status.getFlags().size());
    }

    @Test
    public void setUnset_2() {
        status.set(PanicFlag.NodeEclipsed(123));
        status.set(PanicFlag.FederationBlockchainForked(456));
        Assert.assertTrue(status.inPanic());
        Assert.assertTrue(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertTrue(status.has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FROZEN));
        Assert.assertEquals(2, status.getFlags().size());
        Assert.assertEquals(123, status.get(PanicFlag.Reason.NODE_ECLIPSED).getSinceBlockNumber());
        Assert.assertEquals(456, status.get(PanicFlag.Reason.FEDERATION_FORKED).getSinceBlockNumber());

        status.unset(PanicFlag.of(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertTrue(status.inPanic());
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertTrue(status.has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FROZEN));
        Assert.assertEquals(1, status.getFlags().size());

        status.unset(PanicFlag.of(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(status.inPanic());
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.NODE_FORKED));
        Assert.assertFalse(status.has(PanicFlag.Reason.FEDERATION_FROZEN));
        Assert.assertEquals(0, status.getFlags().size());
    }

    @Test
    public void replace() {
        status.set(PanicFlag.NodeEclipsed(123));
        Assert.assertTrue(status.inPanic());
        Assert.assertTrue(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertEquals(1, status.getFlags().size());
        Assert.assertEquals(123, status.get(PanicFlag.Reason.NODE_ECLIPSED).getSinceBlockNumber());

        status.set(PanicFlag.NodeEclipsed(456));
        Assert.assertTrue(status.inPanic());
        Assert.assertTrue(status.has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertEquals(1, status.getFlags().size());
        Assert.assertEquals(456, status.get(PanicFlag.Reason.NODE_ECLIPSED).getSinceBlockNumber());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getNotSet() {
        status.get(PanicFlag.Reason.FEDERATION_FROZEN);
    }
}
