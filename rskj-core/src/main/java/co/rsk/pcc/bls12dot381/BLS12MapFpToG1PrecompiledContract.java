
package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12MapFpToG1PrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12MapFpToG1PrecompiledContract() {
    super("BLS12_MAP_FIELD_TO_CURVE", LibEthPairings.BLS12_MAP_FP_TO_G1_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    return 5_500;
  }
}
