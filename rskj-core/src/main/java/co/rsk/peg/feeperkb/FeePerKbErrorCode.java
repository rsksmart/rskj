package co.rsk.peg.feeperkb;

public enum FeePerKbErrorCode {
        GENERIC(-10),
        NEGATIVE(-1),
        EXCESSIVE(-2);

        private final int code;

        FeePerKbErrorCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
}
