package co.rsk.peg.performance;

import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeMethods;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@Disabled
public class PowpegRedeemScriptTest extends BridgePerformanceTestCase {

    @BeforeAll
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    public void getActivePowpegRedeemScriptTest() throws VMException {
        ExecutionStats stats = new ExecutionStats("getActivePowpegRedeemScript");
        ABIEncoder abiEncoder = (int executionIndex) -> BridgeMethods.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.getFunction().encode();
        executeAndAverage(
            "getActivePowpegRedeemScriptTest",
            10,
            abiEncoder,
            Helper.buildNoopInitializer(),
            Helper.getZeroValueRandomSenderTxBuilder(),
            Helper.getRandomHeightProvider(10),
            stats,
            (environment, executionResult) -> {
                assertArrayEquals(
                    constants.getBridgeConstants().getGenesisFederation().getRedeemScript().getProgram(),
                    getByteFromResult(executionResult)
                );
            }
        );
        BridgePerformanceTest.addStats(stats);
    }

    private byte[] getByteFromResult(byte[] result) {
        return (byte[]) Bridge.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.decodeResult(result)[0];
    }
}
