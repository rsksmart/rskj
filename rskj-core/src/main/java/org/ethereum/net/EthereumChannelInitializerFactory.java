package org.ethereum.net;

import org.ethereum.net.server.InitiatorHandshakeInitializer;
import org.ethereum.net.server.ReceiverHandshakeInitializer;

public interface EthereumChannelInitializerFactory {

    InitiatorHandshakeInitializer newInitiator(String remoteId);

    ReceiverHandshakeInitializer newReceiver(String remoteId);

}
