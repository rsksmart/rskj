
package co.rsk.pcc.bls12dot381;

import co.rsk.bls12_381.BLS12_381;

public class BLS12G2MultiExpPrecompiledContract extends AbstractBLS12PrecompiledContract {

  @Override
  protected byte[] internalExecute(byte[] input) {
    return BLS12_381.g2MultiExp(input);
  }

  @Override
  public long getGasForData(byte[] data) {
    final int k = data.length / 288;
    return 55L * k * getDiscount(k);
  }
}
