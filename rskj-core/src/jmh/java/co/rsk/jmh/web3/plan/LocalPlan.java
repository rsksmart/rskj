package co.rsk.jmh.web3.plan;

import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

@State(Scope.Benchmark)
public class LocalPlan extends BasePlan{

    public static final String ETH_SIGN_ADDRESS = "ethSign.address";
    public static final String ETH_SIGN_MESSAGE = "ethSign.message";

    private String ethSignMessage;
    private String ethSignAddress;


    @Setup(Level.Trial)
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);
        ethSignAddress = configuration.getString(ETH_SIGN_ADDRESS);
        ethSignMessage = configuration.getString(ETH_SIGN_MESSAGE);
    }

    public String getEthSignAddress() {
        return ethSignAddress;
    }

    public String getEthSignMessage() {
        return ethSignMessage;
    }
}
