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
package co.rsk.net.discovery.upnp;

import com.google.common.collect.ImmutableMap;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import static org.mockito.Mockito.*;

public final class UpnpServiceTest {

    // test data
    private static final String LOCATION_1 = "192.168.0.1";
    private static final String LOCATION_2 = "192.168.1.1";
    private static final int PORT_1 = 5000;
    private static final int PORT_2 = 6000;
    private static final String LOCAL_ADDRESS_VALID_1 = "192.168.0.100";
    private static final String LOCAL_ADDRESS_VALID_2 = "192.168.1.100";
    private static final String LOCAL_ADDRESS_INVALID = "192.168.0.101";
    private static final String VALID_GATEWAY_NAME_1 = "Valid Mock Gateway 1";
    private static final String VALID_GATEWAY_NAME_2 = "Valid Mock Gateway 2";
    private static final String INVALID_GATEWAY_NAME = "Invalid Mock Gateway";
    private static final String PORT_MAPPING_DESCRIPTION_1 = "Unit test port mapping";
    private static final String PORT_MAPPING_DESCRIPTION_2 = "Unit test port mapping 2";
    private static final int DEFAULT_UPNP_TIMEOUT = 5000;
    private static final long STOP_UPNP_SERVICE_TIMEOUT = 10000;
    private static final InetAddress wildcardAddress = new InetSocketAddress(0).getAddress();

    private InetAddress localAddressValid1;
    private InetAddress localAddressValid2;
    private InetAddress localAddressInvalid;

    // mock objects
    private GatewayDevice mockGatewayValid1;
    private GatewayDevice mockGatewayValid2;
    private GatewayDevice mockGatewayInvalid;
    private GatewayDiscover mockWildcardDiscoverValid;
    private GatewayDiscover mockWildcardDiscoverInvalid;
    private GatewayDiscover mockWildcardDiscoverNone;
    private GatewayDiscover mockWildcardDiscoverExceptional;
    private GatewayDiscover mockSpecificDiscoverValid;
    private GatewayDiscover mockSpecificDiscoverValidMultiple;
    private GatewayDiscover mockSpecificDiscoverInvalid;
    private GatewayDiscover mockSpecificDiscoverNone;

    public UpnpServiceTest() throws Exception {
        try {
            // just parses the IPs; no hostname lookup
            this.localAddressValid1 = InetAddress.getByName(LOCAL_ADDRESS_VALID_1);
            this.localAddressValid2 = InetAddress.getByName(LOCAL_ADDRESS_VALID_2);
            this.localAddressInvalid = InetAddress.getByName(LOCAL_ADDRESS_INVALID);
        } catch (UnknownHostException e) {
            // this should never happen
            throw new Exception("Failed to parse IP address.", e);
        }
    }

    @Before
    public void initialize() throws Exception {
        // initialize mocks
        mockGatewayValid1 = mock(GatewayDevice.class);
        mockGatewayValid2 = mock(GatewayDevice.class);
        mockGatewayInvalid = mock(GatewayDevice.class);
        mockWildcardDiscoverValid = mock(GatewayDiscover.class);
        mockWildcardDiscoverInvalid = mock(GatewayDiscover.class);
        mockWildcardDiscoverNone = mock(GatewayDiscover.class);
        mockWildcardDiscoverExceptional = mock(GatewayDiscover.class);
        mockSpecificDiscoverValid = mock(GatewayDiscover.class);
        mockSpecificDiscoverValidMultiple = mock(GatewayDiscover.class);
        mockSpecificDiscoverInvalid = mock(GatewayDiscover.class);
        mockSpecificDiscoverNone = mock(GatewayDiscover.class);

        // setup mock stubs
        when(mockGatewayValid1.isConnected()).thenReturn(true);
        when(mockGatewayValid1.getLocalAddress()).thenReturn(localAddressValid1);
        when(mockGatewayValid1.getFriendlyName()).thenReturn(VALID_GATEWAY_NAME_1);
        when(mockGatewayValid1.addPortMapping(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(mockGatewayValid1.deletePortMapping(anyInt(), anyString())).thenReturn(true);
        when(mockGatewayValid1.getLocation()).thenReturn(LOCATION_1);

        when(mockGatewayValid2.isConnected()).thenReturn(true);
        when(mockGatewayValid2.getLocalAddress()).thenReturn(localAddressValid2);
        when(mockGatewayValid2.getFriendlyName()).thenReturn(VALID_GATEWAY_NAME_2);
        when(mockGatewayValid2.addPortMapping(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(mockGatewayValid2.deletePortMapping(anyInt(), anyString())).thenReturn(true);
        when(mockGatewayValid2.getLocation()).thenReturn(LOCATION_2);

        when(mockGatewayInvalid.isConnected()).thenReturn(false);
        when(mockGatewayInvalid.getLocalAddress()).thenReturn(localAddressInvalid);
        when(mockGatewayInvalid.getFriendlyName()).thenReturn(INVALID_GATEWAY_NAME);
        when(mockGatewayInvalid.getLocation()).thenReturn(LOCATION_1);

        when(mockWildcardDiscoverValid.discover()).thenReturn(ImmutableMap.of(
                localAddressValid1, mockGatewayValid1,
                localAddressInvalid, mockGatewayInvalid
        ));
        when(mockWildcardDiscoverValid.getValidGateway()).thenReturn(mockGatewayValid1);

        when(mockWildcardDiscoverInvalid.discover()).thenReturn(ImmutableMap.of(
                localAddressValid1, mockGatewayValid1
        ));
        when(mockWildcardDiscoverInvalid.getValidGateway()).thenReturn(null);

        when(mockWildcardDiscoverNone.discover()).thenReturn(ImmutableMap.of());
        when(mockWildcardDiscoverNone.getValidGateway()).thenReturn(null);

        when(mockWildcardDiscoverExceptional.discover()).thenThrow(Exception.class);

        when(mockSpecificDiscoverValid.discover()).thenReturn(ImmutableMap.of(
                localAddressValid1, mockGatewayValid1
        ));

        when(mockSpecificDiscoverValidMultiple.discover()).thenReturn(ImmutableMap.of(
                localAddressValid1, mockGatewayValid1,
                localAddressValid2, mockGatewayValid2
        ));

        when(mockSpecificDiscoverInvalid.discover()).thenReturn(ImmutableMap.of(
                localAddressInvalid, mockGatewayInvalid
        ));

        when(mockSpecificDiscoverNone.discover()).thenReturn(ImmutableMap.of());
    }

    private static UpnpService createAndStartUpnpService(GatewayDiscover query) {
        UpnpService upnpService = new UpnpService(query, DEFAULT_UPNP_TIMEOUT);
        upnpService.start();
        return upnpService;
    }

    @Test
    public void testStopWithoutValidSearchResults() {
        try {
            createAndStartUpnpService(mockWildcardDiscoverInvalid).stop();
        } catch (Exception e) {
            Assert.fail("Should not throw Exception when service is stopped in initial state.");
        }
    }

    @Test
    public void testGatewayManagerCaching() {
        UpnpService testService = createAndStartUpnpService(mockSpecificDiscoverValid);
        Optional<UpnpGatewayManager> gm1 = testService.findGateway(localAddressValid1);
        Optional<UpnpGatewayManager> gm2 = testService.findGateway(localAddressValid1);
        Assert.assertTrue("Expected gateway to be present.", gm1.isPresent());
        Assert.assertTrue("Expected gateway to be present.", gm2.isPresent());
        Assert.assertSame("Expected gateway managers to be cached.", gm1.get(), gm2.get());
    }

    @Test
    public void testStopClearsPortMappings() throws IOException, SAXException {
        UpnpService testService = createAndStartUpnpService(mockSpecificDiscoverValidMultiple);
        Optional<UpnpGatewayManager> gm1 = testService.findGateway(localAddressValid1);
        Optional<UpnpGatewayManager> gm2 = testService.findGateway(localAddressValid2);
        Assert.assertTrue("Expected a valid gateway manager.", gm1.isPresent());
        Assert.assertTrue("Expected a valid gateway manager.", gm2.isPresent());

        // add 4 port mappings to each gateway
        int numPortsEach = 4;
        gm1.get().addPortMapping(PORT_1, PORT_1, UpnpProtocol.UDP, PORT_MAPPING_DESCRIPTION_1);
        gm1.get().addPortMapping(PORT_1, PORT_1, UpnpProtocol.TCP, PORT_MAPPING_DESCRIPTION_1);
        gm1.get().addPortMapping(PORT_2, PORT_2, UpnpProtocol.UDP, PORT_MAPPING_DESCRIPTION_2);
        gm1.get().addPortMapping(PORT_2, PORT_2, UpnpProtocol.TCP, PORT_MAPPING_DESCRIPTION_2);

        gm2.get().addPortMapping(PORT_1, PORT_1, UpnpProtocol.UDP, PORT_MAPPING_DESCRIPTION_1);
        gm2.get().addPortMapping(PORT_1, PORT_1, UpnpProtocol.TCP, PORT_MAPPING_DESCRIPTION_1);
        gm2.get().addPortMapping(PORT_2, PORT_2, UpnpProtocol.UDP, PORT_MAPPING_DESCRIPTION_2);
        gm2.get().addPortMapping(PORT_2, PORT_2, UpnpProtocol.TCP, PORT_MAPPING_DESCRIPTION_2);

        // delete the port mappings and verify
        testService.stop();

        verify(mockGatewayValid1, timeout(STOP_UPNP_SERVICE_TIMEOUT).times(numPortsEach))
                .deletePortMapping(anyInt(), anyString());
        verify(mockGatewayValid2, timeout(STOP_UPNP_SERVICE_TIMEOUT).times(numPortsEach))
                .deletePortMapping(anyInt(), anyString());
    }

    @Test
    public void testExceptionalQuery() {
        UpnpService testService = createAndStartUpnpService(mockWildcardDiscoverExceptional);
        Optional<UpnpGatewayManager> gm = testService.findGateway(wildcardAddress);
        Assert.assertFalse(
                "A query which throws exceptions should not return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testWildcardValid() {
        UpnpService testService = createAndStartUpnpService(mockWildcardDiscoverValid);
        Optional<UpnpGatewayManager> gm = testService.findGateway(wildcardAddress);
        Assert.assertTrue(
                "A query with valid results should return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testWildcardDefault() {
        UpnpService testService = createAndStartUpnpService(mockWildcardDiscoverValid);
        Optional<UpnpGatewayManager> gm = testService.findGateway();
        Assert.assertTrue(
                "A query with valid results should return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testWildcardInvalid() {
        UpnpService testService = createAndStartUpnpService(mockWildcardDiscoverInvalid);
        Optional<UpnpGatewayManager> gm = testService.findGateway(wildcardAddress);
        Assert.assertFalse(
                "A query with no valid results should not return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testWildcardNone() {
        UpnpService testService = createAndStartUpnpService(mockWildcardDiscoverNone);
        Optional<UpnpGatewayManager> gm = testService.findGateway(wildcardAddress);
        Assert.assertFalse(
                "A query with no results should not return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testStringAddressValid() {
        UpnpService testService = createAndStartUpnpService(mockSpecificDiscoverValid);
        Optional<UpnpGatewayManager> gm = testService.findGateway(LOCAL_ADDRESS_VALID_1);
        Assert.assertTrue(
                "A valid local address String should return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testSpecificValid() throws IOException, SAXException {
        when(mockGatewayValid1.addPortMapping(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        UpnpService testService = createAndStartUpnpService(mockSpecificDiscoverValid);
        Optional<UpnpGatewayManager> gm = testService.findGateway(localAddressValid1);
        Assert.assertTrue(
                "A query with valid results should return a gateway manager.",
                gm.isPresent()
        );
        // assert that we used the network of the given local address by verifying the expected gateway
        gm.get().addPortMapping(0, 0, UpnpProtocol.UDP, "unit test");

        verify(mockGatewayValid1, description("Wrong local address connection was used."))
                .addPortMapping(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    public void testSpecificInvalid() {
        UpnpService testService = createAndStartUpnpService(mockSpecificDiscoverInvalid);
        Optional<UpnpGatewayManager> gm = testService.findGateway(localAddressInvalid);
        Assert.assertFalse(
                "A query with no valid results should not return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testSpecificNone() {
        UpnpService testService = createAndStartUpnpService(mockSpecificDiscoverNone);
        Optional<UpnpGatewayManager> gm = testService.findGateway(localAddressValid1);
        Assert.assertFalse(
                "A query with no results should not return a gateway manager.",
                gm.isPresent()
        );
    }

    @Test
    public void testSpecificThrowsExceptionOnValidation() throws IOException, SAXException {
        doThrow(Exception.class).when(mockGatewayValid1).isConnected();

        UpnpService testService = createAndStartUpnpService(mockSpecificDiscoverValid);
        Optional<UpnpGatewayManager> gm = testService.findGateway(localAddressValid1);
        Assert.assertFalse(
                "An Exception thrown on status check should not result in a gateway manager being returned.",
                gm.isPresent()
        );
    }
}
