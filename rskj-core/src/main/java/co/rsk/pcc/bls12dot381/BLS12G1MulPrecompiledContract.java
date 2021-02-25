package co.rsk.pcc.bls12dot381;

import co.rsk.bls12_381.BLS12_381;


public class BLS12G1MulPrecompiledContract extends AbstractBLS12PrecompiledContract {

  @Override
  protected byte[] internalExecute(byte[] input) {
    return BLS12_381.g1Mul(input);
  }

  @Override
  public long getGasForData(byte[] data) {
    return 12_000;
  }
}
