package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContributorTrustCapabilityTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "security-review", "architecture-review", "style-review",
        "test-coverage", "performance-analysis"
    })
    void forDomain_producesCompoundTag(String domain) {
        assertThat(ContributorTrustCapability.forDomain(domain))
            .isEqualTo("pr-contribution:" + domain);
    }

    @Test
    void forDomain_rejectsCodeAnalysis() {
        assertThatThrownBy(() -> ContributorTrustCapability.forDomain("code-analysis"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forDomain_rejectsUnknownDomain() {
        assertThatThrownBy(() -> ContributorTrustCapability.forDomain("unknown"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forDomain_rejectsNull() {
        assertThatThrownBy(() -> ContributorTrustCapability.forDomain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isDomainScoped_trueForCompoundTag() {
        assertThat(ContributorTrustCapability.isDomainScoped("pr-contribution:security-review"))
            .isTrue();
    }

    @Test
    void isDomainScoped_falseForAggregate() {
        assertThat(ContributorTrustCapability.isDomainScoped("pr-contribution"))
            .isFalse();
    }

    @Test
    void isDomainScoped_falseForUnrelatedCapability() {
        assertThat(ContributorTrustCapability.isDomainScoped("security-review"))
            .isFalse();
    }
}
