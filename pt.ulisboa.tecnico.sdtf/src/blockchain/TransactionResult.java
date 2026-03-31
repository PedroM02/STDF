package blockchain;

public class TransactionResult {

    private final boolean success;
    private final long gasUsed;
    private final String errorMessage;

    private TransactionResult(boolean success, long gasUsed, String errorMessage) {
        this.success = success;
        this.gasUsed = gasUsed;
        this.errorMessage = errorMessage;
    }

    public static TransactionResult success(long gasUsed) {
        return new TransactionResult(true, gasUsed, null);
    }

    public static TransactionResult fail(String reason) {
        return new TransactionResult(false, 0, reason);
    }

    public boolean isSuccess()       { return success; }
    public long getGasUsed()         { return gasUsed; }
    public String getErrorMessage()  { return errorMessage; }

    @Override
    public String toString() {
        return success ? "OK(gasUsed=" + gasUsed + ")" : "FAIL(" + errorMessage + ")";
    }
}