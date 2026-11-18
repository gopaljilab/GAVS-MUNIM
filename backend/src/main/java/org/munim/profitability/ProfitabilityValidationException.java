package org.munim.profitability;

public class ProfitabilityValidationException extends IllegalArgumentException {
    private final String errorCode;

    public ProfitabilityValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ProfitabilityValidationException(String message) {
        this("VALIDATION_ERROR", message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
