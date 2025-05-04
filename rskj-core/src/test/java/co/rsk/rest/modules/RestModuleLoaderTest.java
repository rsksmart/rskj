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
package co.rsk.rest.modules;

import co.rsk.rest.dto.RestModuleConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestModuleLoaderTest {

    @Test
    void testConstructor_nullRestModuleConfiguration_throwsException() {
        try {
            new RestModuleLoader(null);
            fail("Null Pointer Exception should be thrown");
        } catch (NullPointerException npe) {
            assertEquals("REST Module Config can not be null", npe.getMessage());
        }
    }

    @Test
    void testModuleMapping_activeConfiguration_OK() {
        // Given
        RestModuleConfigDTO restModuleConfigDTO = new RestModuleConfigDTO(true);

        // When
        RestModuleLoader restModuleLoader = new RestModuleLoader(restModuleConfigDTO);

        // Then

        // Health-Check Module
        RestModule healthCheckModule = restModuleLoader.getRestModules().get(0);
        assertTrue(healthCheckModule.isActive());
        assertEquals("/health-check", healthCheckModule.getUri());

        // Other Modules
        // ...
    }

    @Test
    void testModuleMapping_inactiveConfiguration_OK() {
        // Given
        // Health-Check Module
        RestModuleConfigDTO restModuleConfigDTO = new RestModuleConfigDTO(false);

        // Other Modules
        // ...

        // When
        RestModuleLoader restModuleLoader = new RestModuleLoader(restModuleConfigDTO);

        // Then
        // Health-Check Module
        RestModule healthCheckModule = restModuleLoader.getRestModules().get(0);
        assertFalse(healthCheckModule.isActive());
        assertEquals("/health-check", healthCheckModule.getUri());

        // Other Modules
        // ...
    }

}
