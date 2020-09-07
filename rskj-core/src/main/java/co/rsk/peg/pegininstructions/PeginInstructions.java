package co.rsk.peg.pegininstructions;

import co.rsk.core.RskAddress;

public interface PeginInstructions {

    RskAddress getRskDestinationAddress();

    int getProtocolVersion();
}
