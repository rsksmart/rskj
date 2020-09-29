package co.rsk.pcc.bls12dot381;

import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

public class BLS12PairingPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12PairingPrecompiledContract() {
    super("BLS12_PAIRING", LibEthPairings.BLS12_PAIR_OPERATION_RAW_VALUE);
  }

  @Override
  public long getGasForData(byte[] data) {
    final int k = data.length / 384;
    return 23_000L * k + 115_000;
  }
}
