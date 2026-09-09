// Proposed addition to exeris-sdk-source-model, package eu.exeris.sdk.sourcemodel.ast.
package eu.exeris.sdk.sourcemodel.ast;

import java.util.List;

/** Carries the retry policy declared on an action. */
public final class RetryMetadata {
    private final String strategy;
    private final int maxAttempts;
    private final List<String> retryOn;

    public RetryMetadata(String strategy, int maxAttempts, List<String> retryOn) {
        this.strategy = strategy;
        this.maxAttempts = maxAttempts;
        this.retryOn = retryOn;
    }

    public String strategy() { return strategy; }
    public int maxAttempts() { return maxAttempts; }
    public List<String> retryOn() { return retryOn; }
}
