
package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12G2MulPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12G2MulPrecompiledContract() {
    super("BLS12_G2MUL", LibEthPairings.BLS12_G2MUL_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    return 55_000;
  }
}
