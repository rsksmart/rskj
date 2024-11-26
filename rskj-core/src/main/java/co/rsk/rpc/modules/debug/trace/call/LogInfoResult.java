package co.rsk.rpc.modules.debug.trace.call;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LogInfoResult(@JsonProperty int index, @JsonProperty String address, @JsonProperty List<String> topics,
                            @JsonProperty String data) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int index;
        private String address;
        private List<String> topics;
        private String data;

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder topics(List<String> topics) {
            this.topics = topics;
            return this;
        }

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public LogInfoResult build() {
            return new LogInfoResult(index, address, topics, data);
        }
    }
}
