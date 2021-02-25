
package co.rsk.pcc.bls12dot381;

import co.rsk.bls12_381.BLS12_381;

public class BLS12G2AddPrecompiledContract extends AbstractBLS12PrecompiledContract {

  @Override
  protected byte[] internalExecute(byte[] input) {
    return BLS12_381.g2Add(input);
  }

  @Override
  public long getGasForData(byte[] data) {
    return 4_500;
  }

}
