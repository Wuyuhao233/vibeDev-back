package com.vibedev.common;

public enum ErrorCode {

    OK(0),

    // General
    UNKNOWN(10000),

    // Auth (10xxx)
    UNAUTHORIZED(10001),
    TOKEN_EXPIRED(10002),
    ACCOUNT_LOCKED(10003),
    ACCOUNT_NOT_ACTIVATED(10004),
    PASSWORD_WRONG(10005),
    USERNAME_TAKEN(10006),
    EMAIL_TAKEN(10007),
    INVALID_VERIFY_CODE(10008),

    // Permission (20xxx)
    FORBIDDEN(20001),
    BANNED(20002),
    INSUFFICIENT_LEVEL(20003),
    NOT_MODERATOR_OF_BOARD(20004),

    // Resource (30xxx)
    NOT_FOUND(30001),
    POST_DELETED(30002),

    // Rate limit (40xxx)
    RATE_LIMITED(40001),
    DUPLICATE_SUBMIT(40002),

    // Validation (4001x)
    VALIDATION_TITLE(40010),
    VALIDATION_TAGS(40011),
    VALIDATION_CONTENT_EMPTY(40012),
    VALIDATION_SENSITIVE_WORD(40013),
    VALIDATION_TITLE_DUPLICATE(40014),
    VALIDATION_FILE_SIZE(40015),
    VALIDATION_FILE_FORMAT(40016),

    // Concurrency (50xxx)
    VERSION_CONFLICT(50001),
    AUDIT_LOCKED(50002);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
