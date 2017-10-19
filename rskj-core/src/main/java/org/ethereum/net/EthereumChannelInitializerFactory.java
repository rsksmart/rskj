package org.ethereum.net;

import org.ethereum.net.server.EthereumChannelInitializer;

public interface EthereumChannelInitializerFactory {

    EthereumChannelInitializer newInstance(String remoteId);

}
