package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12G1MultiExpPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12G1MultiExpPrecompiledContract() {
    super("BLS12_G1MULTIEXP", LibEthPairings.BLS12_G1MULTIEXP_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    final int k = data.length / 160;
    return 12L * k * getDiscount(k);
  }
}
