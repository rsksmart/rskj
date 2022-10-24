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

import co.rsk.config.TestSystemProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 20/04/2017.
 */
class CorsConfigurationTest {

    public static final String EXPECTED_CORS_CONFIG = "*.rsk.co";

    @Test
    void hasNoHeaderIfHeaderIsNull() {
        CorsConfiguration config = new CorsConfiguration(null);

        Assertions.assertNull(config.getHeader());
        Assertions.assertFalse(config.hasHeader());
    }

    @Test
    void hasNoHeaderIfHeaderIsEmpty() {
        CorsConfiguration config = new CorsConfiguration("");

        Assertions.assertEquals("", config.getHeader());
        Assertions.assertFalse(config.hasHeader());
    }

    @Test
    void hasHeaderFromTestConfig() {
        CorsConfiguration config = new CorsConfiguration(new TestSystemProperties().corsDomains());

        Assertions.assertNotNull(config.getHeader());
        Assertions.assertEquals(EXPECTED_CORS_CONFIG, config.getHeader());
        Assertions.assertTrue(config.hasHeader());
    }

    @Test
    void raisedIfHeaderContainsCarriageReturn() {
        try {
            new CorsConfiguration("host1\rhost2");
            Assertions.fail();
        }
        catch (IllegalArgumentException ex) {
            Assertions.assertEquals("corsheader", ex.getMessage());
        }
    }

    @Test
    void raisedIfHeaderContainsNewLine() {
        try {
            new CorsConfiguration("host1\nhost2");
            Assertions.fail();
        }
        catch (IllegalArgumentException ex) {
            Assertions.assertEquals("corsheader", ex.getMessage());
        }
    }
}
