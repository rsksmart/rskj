package co.rsk.pcc.bls12dot381;

import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.util.Strings;
import org.ethereum.vm.PrecompiledContracts;
import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public abstract class AbstractBLS12PrecompiledContract extends PrecompiledContracts.PrecompiledContract {

    public class BLS12FailureException extends RuntimeException {

        public BLS12FailureException(String format) {
            super(format);
        }
    }

    public static final Logger logger = LoggerFactory.getLogger("precompiled");

    static final int[] DISCOUNT_TABLE =
            new int[] {
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

    static final int MAX_DISCOUNT = 174;

    private final String name;
    private final byte operationId;
    private String failureReason; //field only for testing purposes

    AbstractBLS12PrecompiledContract(final String name, final byte operationId) {
        this.name = name;
        this.operationId = operationId;
    }


    @Override
    public byte[] execute(byte[] input) {

        final byte[] result = new byte[LibEthPairings.EIP2537_PREALLOCATE_FOR_RESULT_BYTES];
        final byte[] error = new byte[LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES];

        final IntByReference o_len =
                new IntByReference(LibEthPairings.EIP2537_PREALLOCATE_FOR_RESULT_BYTES);
        final IntByReference err_len =
                new IntByReference(LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES);
        final int errorNo =
                LibEthPairings.eip2537_perform_operation(
                        operationId, Arrays.copyOf(input, input.length), input.length, result, o_len, error, err_len);
        if (errorNo == 0) {
            //we need to truncate the result array to the actual length
            return Arrays.copyOfRange(result, 0, o_len.getValue());
        } else {
            String reason = Strings.fromByteArray(Arrays.copyOfRange(error, 0, err_len.getValue()));
            setFailureReason(reason);
            logger.trace(String.format("precompiled failed: {}", reason));
            throw new BLS12FailureException(String.format("precompiled failed: {}", reason));
        }
    }

    private void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    @VisibleForTesting
    public String getFailureReason() {
        return failureReason;
    }

    protected int getDiscount(final int k) {
        if (k >= DISCOUNT_TABLE.length) {
            return MAX_DISCOUNT;
        }
        return DISCOUNT_TABLE[k];
    }
}
