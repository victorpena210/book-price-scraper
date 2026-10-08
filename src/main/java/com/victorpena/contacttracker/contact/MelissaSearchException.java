package com.victorpena.contacttracker.contact;

import java.util.List;

/** An upstream failure, kept separate from a completed search with no matches. */
public class MelissaSearchException extends RuntimeException {
    private final String error;
    private final List<String> resultCodes;

    public MelissaSearchException(String error, String message, List<String> resultCodes) {
        super(message);
        this.error = error;
        this.resultCodes = List.copyOf(resultCodes);
    }

    public String getError() {
        return error;
    }

    public List<String> getResultCodes() {
        return resultCodes;
    }
}
