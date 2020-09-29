
package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12G2MultiExpPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12G2MultiExpPrecompiledContract() {
    super("BLS12_G2MULTIEXP", LibEthPairings.BLS12_G2MULTIEXP_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    final int k = data.length / 288;
    return 55L * k * getDiscount(k);
  }
}
