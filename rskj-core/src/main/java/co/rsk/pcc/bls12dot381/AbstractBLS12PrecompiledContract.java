package co.rsk.pcc.bls12dot381;

import co.rsk.bls12_381.BLS12_381;
import co.rsk.bls12_381.BLS12_381Exception;
import co.rsk.panic.PanicProcessor;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBLS12PrecompiledContract extends PrecompiledContracts.PrecompiledContract {

    public static final String ERROR_MESSAGE = "couldn't load native library, stopping execution";
    private static final Logger logger = LoggerFactory.getLogger("precompiled");
    private final PanicProcessor panicProcessor = new PanicProcessor();

    public AbstractBLS12PrecompiledContract() {
        if(!BLS12_381.ENABLED) {
            panicProcessor.panic("bls12-381 precompiled", ERROR_MESSAGE);
            throw new RuntimeException(ERROR_MESSAGE);
        }
    }

    @Override
    public byte[] execute(byte[] input) throws VMException {
        try { // handles an external exception, then throws own BLS12Exception (extending VM exception)
            return internalExecute(input);
        } catch (BLS12_381Exception e) {
            logger.error("bls12_381 execution failed", e);
            throw new VMException(e.getMessage());
        }
    }

    protected abstract byte[] internalExecute(byte[] input);

    protected int getDiscount(final int k) {
        if (k >= discountTable().length) {
            return maxDiscount();
        }
        return discountTable()[k];
    }

    private int[] discountTable() {
        return new int[] {
                -1, 1_200, 888, 764, 641, 594, 547, 500, 453, 438, 423, 408, 394, 379, 364, 349,
                334, 330, 326, 322, 318, 314, 310, 306, 302, 298, 294, 289, 285, 281, 277, 273,
                269, 268, 266, 265, 263, 262, 260, 259, 257, 256, 254, 253, 251, 250, 248, 247,
                245, 244, 242, 241, 239, 238, 236, 235, 233, 232, 231, 229, 228, 226, 225, 223,
                222, 221, 220, 219, 219, 218, 217, 216, 216, 215, 214, 213, 213, 212, 211, 211,
                210, 209, 208, 208, 207, 206, 205, 205, 204, 203, 202, 202, 201, 200, 199, 199,
                198, 197, 196, 196, 195, 194, 193, 193, 192, 191, 191, 190, 189, 188, 188, 187,
                186, 185, 185, 184, 183, 182, 182, 181, 180, 179, 179, 178, 177, 176, 176, 175,
                174
        };
    }

    private int maxDiscount() {
        return 174;
    }
}
