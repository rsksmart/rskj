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

package co.rsk.rpc;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 20/04/2017.
 */
public class CorsConfigurationTest {

    public static final String EXPECTED_CORS_CONFIG = "*.rsk.co";

    @Test
    public void hasNoHeaderIfHeaderIsNull() {
        CorsConfiguration config = new CorsConfiguration(null);

        Assert.assertNull(config.getHeader());
        Assert.assertFalse(config.hasHeader());
    }

    @Test
    public void hasNoHeaderIfHeaderIsEmpty() {
        CorsConfiguration config = new CorsConfiguration("");

        Assert.assertEquals("", config.getHeader());
        Assert.assertFalse(config.hasHeader());
    }

    @Test
    public void hasHeaderFromTestConfig() {
        CorsConfiguration config = new CorsConfiguration();

        Assert.assertNotNull(config.getHeader());
        Assert.assertEquals(EXPECTED_CORS_CONFIG, config.getHeader());
        Assert.assertTrue(config.hasHeader());
    }

    @Test
    public void raisedIfHeaderContainsCarriageReturn() {
        try {
            new CorsConfiguration("host1\rhost2");
            Assert.fail();
        }
        catch (IllegalArgumentException ex) {
            Assert.assertEquals("corsheader", ex.getMessage());
        }
    }

    @Test
    public void raisedIfHeaderContainsNewLine() {
        try {
            new CorsConfiguration("host1\nhost2");
            Assert.fail();
        }
        catch (IllegalArgumentException ex) {
            Assert.assertEquals("corsheader", ex.getMessage());
        }
    }
}
