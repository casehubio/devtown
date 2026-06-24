package io.casehub.devtown.app;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.ClassificationContext;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.worker.api.PlannedAction;
import io.casehub.devtown.review.DevtownActionRiskClassifier;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@RiskClassifier
@ApplicationScoped
public class DevtownRiskClassifierProducer implements ActionRiskClassifier {

    private final PreferenceProvider preferenceProvider;
    private final DevtownActionRiskClassifier classifier;

    @Inject
    public DevtownRiskClassifierProducer(final PreferenceProvider preferenceProvider) {
        this.preferenceProvider = preferenceProvider;
        this.classifier = new DevtownActionRiskClassifier();
    }

    @Override
    public RiskDecision classify(final PlannedAction action, final ClassificationContext context) {
        final Preferences prefs = preferenceProvider.resolve(
                SettingsScope.of("casehubio", "devtown", "risk", action.actionType()));
        return classifier.classify(action, prefs);
    }
}
