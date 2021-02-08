package co.rsk.pcc;

import co.rsk.bls12_381.BLS12_381;
import co.rsk.config.TestSystemProperties;
import co.rsk.pcc.bls12dot381.AbstractBLS12PrecompiledContract;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.vm.PrecompiledContracts.*;
import static org.ethereum.vm.PrecompiledContracts.BLS12_MAP_FP2_TO_G2_ADDR_STR;


@RunWith(PowerMockRunner.class)
@PrepareForTest(BLS12_381.class)
public class BLS12_381PanicTest {
    // It's in another class because I want to run just this test with PowerMockRunner

    private TestSystemProperties config;
    private PrecompiledContracts precompiledContracts;

    @Before
    public void setup() {
        this.config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
        );
        this.precompiledContracts = new PrecompiledContracts(config, null);
    }

    @Test
    public void throwPanicIfNotInitializedTest() throws NoSuchFieldException, IllegalAccessException {
        Field field = BLS12_381.class.getDeclaredField("ENABLED");
        field.set(null, false);

        List<String> addresses = Arrays.asList(BLS12_G1ADD_ADDR_STR, BLS12_G1MUL_ADDR_STR, BLS12_G1MULTIEXP_ADDR_STR,
                BLS12_G2ADD_ADDR_STR, BLS12_G2MUL_ADDR_STR, BLS12_G2MULTIEXP_ADDR_STR,
                BLS12_MAP_FP_TO_G1_ADDR_STR, BLS12_MAP_FP2_TO_G2_ADDR_STR);

        addresses.forEach(address -> {
            DataWord addr = DataWord.valueFromHex(address);
            try {
                precompiledContracts.getContractForAddress(config.getActivationConfig().forBlock(0), addr);
                Assert.fail("should throw an exception");
            } catch (RuntimeException throwable) {
                Assert.assertEquals(AbstractBLS12PrecompiledContract.ERROR_MESSAGE, throwable.getMessage());
            }
        });
    }
}
