package co.rsk.config;

import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

public class BridgeConstantsTest {
    @Test
    public void getCheckPoints() {

        BridgeRegTestConstants bridgeRegTestConstants = new BridgeRegTestConstants();
        InputStream regTestCheckpoints = bridgeRegTestConstants.getCheckPoints();
        Assert.assertNull(regTestCheckpoints);
        InputStream regTestCheckpointsSecondCall = bridgeRegTestConstants.getCheckPoints();
        Assert.assertNull(regTestCheckpointsSecondCall);

        BridgeDevNetConstants bridgeDevNetConstants = new BridgeDevNetConstants();
        InputStream devNetCheckpoints = bridgeDevNetConstants.getCheckPoints();
        Assert.assertNotNull(devNetCheckpoints);
        InputStream devNetCheckpointsSecondCall = bridgeDevNetConstants.getCheckPoints();
        Assert.assertNotNull(devNetCheckpointsSecondCall);
        Assert.assertEquals(devNetCheckpoints, devNetCheckpointsSecondCall);

        BridgeTestNetConstants bridgeTestNetConstants = new BridgeTestNetConstants();
        InputStream testNetCheckpoints = bridgeTestNetConstants.getCheckPoints();
        Assert.assertNotNull(testNetCheckpoints);
        InputStream testNetCheckpointsSecondCall = bridgeTestNetConstants.getCheckPoints();
        Assert.assertNotNull(testNetCheckpointsSecondCall);
        Assert.assertEquals(testNetCheckpoints, testNetCheckpointsSecondCall);

        BridgeMainNetConstants bridgeMainNetConstants = new BridgeMainNetConstants();
        InputStream mainNetCheckpoints = bridgeMainNetConstants.getCheckPoints();
        Assert.assertNotNull(mainNetCheckpoints);
        InputStream mainNetCheckpointsSecondCall = bridgeMainNetConstants.getCheckPoints();
        Assert.assertNotNull(mainNetCheckpoints);
        Assert.assertEquals(mainNetCheckpoints, mainNetCheckpointsSecondCall);
    }
}
