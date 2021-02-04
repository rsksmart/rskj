package co.rsk.pcc.bls12dot381;

import co.rsk.bls12_381.BLS12_381;

public class BLS12PairingPrecompiledContract extends AbstractBLS12PrecompiledContract {

  @Override
  protected byte[] internalExecute(byte[] input) {
    return BLS12_381.pair(input);
  }

  @Override
  public long getGasForData(byte[] data) {
    final int k = data.length / 384;
    return 23_000L * k + 115_000;
  }
}
