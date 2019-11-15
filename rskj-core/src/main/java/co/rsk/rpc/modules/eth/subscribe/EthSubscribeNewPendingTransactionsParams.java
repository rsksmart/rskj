package co.rsk.rpc.modules.eth.subscribe;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.channel.Channel;

@JsonDeserialize
public class EthSubscribeNewPendingTransactionsParams implements EthSubscribeParams {
    @Override
    public SubscriptionId accept(EthSubscribeParamsVisitor visitor, Channel channel) {
        return visitor.visit(this, channel);
    }
}
