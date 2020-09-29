
package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12G2AddPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12G2AddPrecompiledContract() {
    super("BLS12_G2ADD", LibEthPairings.BLS12_G2ADD_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    return 4_500;
  }
}
