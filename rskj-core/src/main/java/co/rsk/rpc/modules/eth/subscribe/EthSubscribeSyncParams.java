package co.rsk.rpc.modules.eth.subscribe;

import io.netty.channel.Channel;

public class EthSubscribeSyncParams implements EthSubscribeParams {
    @Override
    public SubscriptionId accept(EthSubscribeParamsVisitor visitor, Channel channel) {
        return visitor.visit(this, channel);
    }
}