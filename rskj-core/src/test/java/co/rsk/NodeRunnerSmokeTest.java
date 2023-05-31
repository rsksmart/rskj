/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk;

import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRunnerSmokeTest {

    @TempDir
    public Path tempDir;

    @Test
    void mainnetSmokeTest() {
        RskTestContext rskContext = new RskTestContext(makeDbArg(tempDir, "mainnet"));
        assertThat(rskContext.getNodeRunner(), notNullValue());
        rskContext.close();
    }

    @Test
    void testnetSmokeTest() {
        RskTestContext rskContext = new RskTestContext(makeDbArg(tempDir, "testnet"), "--testnet");
        assertThat(rskContext.getNodeRunner(), notNullValue());
        rskContext.close();
    }

    @Test
    void regtestSmokeTest() {
        RskTestContext rskContext = new RskTestContext(makeDbArg(tempDir, "regtest"), "--regtest");
        assertThat(rskContext.getNodeRunner(), notNullValue());
        rskContext.close();
    }

    @Test
    void contextRecreationSmokeTest() {
        RskTestContext rskContext = new RskTestContext(makeDbArg(tempDir, "regtest"), "--regtest");
        assertThat(rskContext.getNodeRunner(), notNullValue());
        rskContext.close();
        assertTrue(rskContext.isClosed());

        // re-create context
        rskContext = new RskTestContext(makeDbArg(tempDir, "regtest"), "--regtest");
        assertThat(rskContext.getNodeRunner(), notNullValue());
        rskContext.close();
        assertTrue(rskContext.isClosed());
    }

    private static String makeDbArg(Path dbPath, String dbName) {
        return "-Xdatabase.dir=" + dbPath.resolve(dbName);
    }
}
