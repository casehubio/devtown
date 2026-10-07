package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DevtownEvolutionCaseResolverTest {

  private CaseInstanceRepository repository;
  private DevtownEvolutionCaseResolver resolver;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(CaseInstanceRepository.class);
    resolver = new DevtownEvolutionCaseResolver();
    resolver.caseInstanceRepository = repository;
  }

  @Test
  void resolves_existing_evolution_case() {
    UUID expectedId = UUID.randomUUID();
    var instance = new CaseInstance();
    instance.setUuid(expectedId);
    when(repository.findByNamespaceAndName("devtown", "evolution", "test-tenant"))
        .thenReturn(List.of(instance));

    UUID result = resolver.resolve("test-tenant");
    assertThat(result).isEqualTo(expectedId);
  }

  @Test
  void throws_when_no_evolution_case_found() {
    when(repository.findByNamespaceAndName("devtown", "evolution", "test-tenant"))
        .thenReturn(List.of());

    assertThatThrownBy(() -> resolver.resolve("test-tenant"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No evolution case found");
  }
}
