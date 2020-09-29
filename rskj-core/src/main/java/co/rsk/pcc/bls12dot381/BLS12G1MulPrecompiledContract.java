package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;


public class BLS12G1MulPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12G1MulPrecompiledContract() {
    super("BLS12_G1MUL", LibEthPairings.BLS12_G1MUL_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    return 12_000;
  }

}
