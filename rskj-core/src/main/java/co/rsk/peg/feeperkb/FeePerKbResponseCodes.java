package co.rsk.peg.feeperkb;

public enum FeePerKbResponseCodes {
        FEE_PER_KB_GENERIC_ERROR(-10),
        NEGATIVE_FEE_PER_KB_ERROR(-1),
        EXCESSIVE_FEE_PER_KB_ERROR(-2);

        private final int codeResponse;
        FeePerKbResponseCodes(int codeResponse) {
            this.codeResponse = codeResponse;
        }

        public int getCodeResponse() {
            return codeResponse;
        }

        public static int getGenericErrorCode() {
            return FEE_PER_KB_GENERIC_ERROR.getCodeResponse();
        }
        public static Integer getNegativeFeeErrorCode() {
            return NEGATIVE_FEE_PER_KB_ERROR.getCodeResponse();
        }

        public static Integer getExcessiveFeeErrorCode() {
            return EXCESSIVE_FEE_PER_KB_ERROR.getCodeResponse();
        }
}
