package com.domain.exceptions;

public class BusinessRuleException extends DomainException {
    public BusinessRuleException(String message) {
        super(message, "BUSINESS_RULE_VIOLATION", 422);
    }

    public BusinessRuleException(String rule, String message) {
        super(rule + ": " + message, "BUSINESS_RULE_VIOLATION", 422);
    }
}
