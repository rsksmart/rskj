package co.rsk.peg.pegininstructions;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

public class PeginInstructionsBaseTest {

    @Test
    public void extractProtocolVersion() throws PeginInstructionsParseException {
        int protocolVersion = PeginInstructionsBase.extractProtocolVersion(
                Hex.decode("52534b54010e537aad84447a2c2a7590d5f2665ef5cf9b667a"));

        Assert.assertEquals(1, protocolVersion);
    }

    @Test
    public void extractProtocolVersion_version_bigger_than_9() throws PeginInstructionsParseException {
        int protocolVersion = PeginInstructionsBase.extractProtocolVersion(
            Hex.decode("52534b540a0e537aad84447a2c2a7590d5f2665ef5cf9b667a"));

        Assert.assertEquals(10, protocolVersion);
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void extractProtocolVersion_null_data() throws PeginInstructionsParseException {
        PeginInstructionsBase.extractProtocolVersion(null);
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void extractProtocolVersion_empty_data() throws PeginInstructionsParseException {
        PeginInstructionsBase.extractProtocolVersion(new byte[]{});
    }
}
