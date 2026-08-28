package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.pegout.HistoricalPegoutSelectionsConstants;
import java.util.Map;

/**
 * Mainnet constants recording the one historic pegout selection a test scenario needs, with every other
 * mainnet value left as it ships.
 *
 * <p>The real mainnet table cannot drive a scenario: its values are the btc tx hashes of the unsigned
 * pegouts mainnet really confirmed, and reproducing those transactions would need the bridge state of an
 * archive node.</p>
 */
public class BridgeMainNetConstantsWithHistoricalPegoutSelection extends BridgeMainNetConstants {

    public BridgeMainNetConstantsWithHistoricalPegoutSelection(
        Keccak256 updateCollectionsRskTxHash,
        Sha256Hash selectedPegoutBtcTxHash
    ) {
        historicalPegoutSelectionsConstants = new HistoricalPegoutSelectionsConstants() {
            {
                selections = Map.of(updateCollectionsRskTxHash, selectedPegoutBtcTxHash);
            }
        };
    }
}
