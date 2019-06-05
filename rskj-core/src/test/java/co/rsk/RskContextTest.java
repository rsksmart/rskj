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

import co.rsk.config.NodeCliFlags;
import org.ethereum.util.RskTestContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(RskContext.class)
public class RskContextTest {

    @Test
    public void getNodeRunnerSmokeTest() {
        RskTestContext rskContext = new RskTestContext(new String[0]);
        assertThat(rskContext.getNodeRunner(), notNullValue());
    }

    @Test
    public void getCliArgsSmokeTest() {
        RskTestContext rskContext = new RskTestContext(new String[] { "--devnet" });
        assertThat(rskContext.getCliArgs(), notNullValue());
        assertThat(rskContext.getCliArgs().getFlags(), contains(NodeCliFlags.NETWORK_DEVNET));
    }

    @Test
    public void getBuildInfoSmokeTest() {
        RskTestContext rskContext = new RskTestContext(new String[0]);
        mockBuildInfoResource(new ByteArrayInputStream("build.hash=c0ffee\nbuild.branch=HEAD".getBytes()));
        assertThat(rskContext.getBuildInfo(), notNullValue());
        assertThat(rskContext.getBuildInfo().getBuildHash(), is("c0ffee"));
    }

    @Test
    public void getBuildInfoMissingPropertiesSmokeTest() {
        RskTestContext rskContext = new RskTestContext(new String[0]);
        mockBuildInfoResource(null);
        assertThat(rskContext.getBuildInfo(), notNullValue());
        assertThat(rskContext.getBuildInfo().getBuildHash(), is("dev"));
    }

    private void mockBuildInfoResource(InputStream buildInfoStream) {
        mockStatic(RskContext.class);
        ClassLoader classLoader = mock(ClassLoader.class);
        when(classLoader.getResourceAsStream("build-info.properties")).thenReturn(buildInfoStream);
        when(RskContext.class.getClassLoader()).thenReturn(classLoader);
    }
}