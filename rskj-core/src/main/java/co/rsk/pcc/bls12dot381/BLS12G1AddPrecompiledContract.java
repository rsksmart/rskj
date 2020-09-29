package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12G1AddPrecompiledContract extends AbstractBLS12PrecompiledContract {

    public BLS12G1AddPrecompiledContract() {
        super("BLS12_G1ADD", LibEthPairings.BLS12_G1ADD_OPERATION_RAW_VALUE);
    }

    @Override
    public long getGasForData(byte[] data) {
        return 600;
    }
}