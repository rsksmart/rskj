
package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12MapFp2ToG2PrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12MapFp2ToG2PrecompiledContract() {
    super("BLS12_MAP_FIELD_TO_CURVE", LibEthPairings.BLS12_MAP_FP2_TO_G2_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    return 110_000;
  }
}
