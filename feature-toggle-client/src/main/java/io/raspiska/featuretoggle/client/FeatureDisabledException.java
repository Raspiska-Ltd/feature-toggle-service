package io.raspiska.featuretoggle.client;

public class FeatureDisabledException extends RuntimeException {

    private final String featureName;
    private final String reason;

    public FeatureDisabledException(String featureName, String reason) {
        super("Feature '" + featureName + "' is disabled: " + reason);
        this.featureName = featureName;
        this.reason = reason;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getReason() {
        return reason;
    }
}
