package co.rsk.peg.pegininstructions;

import co.rsk.peg.utils.OpReturnUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

public class PeginInstructionsBaseTest {

    @Test
    public void extractProtocolVersion() throws PeginInstructionsParseException {
        StringBuilder opReturnData = new StringBuilder();
        opReturnData.append(Hex.toHexString(OpReturnUtils.PEGIN_OUTPUT_IDENTIFIER))
            .append("01") // protocol version
            .append("0e537aad84447a2c2a7590d5f2665ef5cf9b667a"); // data

        int protocolVersion = PeginInstructionsBase.extractProtocolVersion(Hex.decode(opReturnData.toString()));

        Assert.assertEquals(1, protocolVersion);
    }

    @Test
    public void extractProtocolVersion_version_bigger_than_9() throws PeginInstructionsParseException {
        StringBuilder opReturnData = new StringBuilder();
        opReturnData.append(Hex.toHexString(OpReturnUtils.PEGIN_OUTPUT_IDENTIFIER))
            .append("0a") // protocol version
            .append("0e537aad84447a2c2a7590d5f2665ef5cf9b667a"); // data

        int protocolVersion = PeginInstructionsBase.extractProtocolVersion(Hex.decode(opReturnData.toString()));

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
