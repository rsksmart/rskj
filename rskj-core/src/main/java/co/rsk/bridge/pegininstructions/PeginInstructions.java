package co.rsk.bridge.pegininstructions;

import co.rsk.core.RskAddress;

public interface PeginInstructions {

    RskAddress getRskDestinationAddress();

    int getProtocolVersion();
}
