package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevtownEvolutionBootstrapTest {

  private CaseInstanceRepository repository;
  private DevtownEvolutionCaseHub caseHub;
  private DevtownEvolutionBootstrap bootstrap;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(CaseInstanceRepository.class);
    caseHub = Mockito.mock(DevtownEvolutionCaseHub.class);
    bootstrap = new DevtownEvolutionBootstrap();
    bootstrap.caseInstanceRepository = repository;
    bootstrap.caseHub = caseHub;
  }

  @Test
  void creates_case_when_none_exists() {
    when(repository.findByNamespaceAndName("devtown", "evolution", "default"))
        .thenReturn(List.of());

    bootstrap.onStartup(Mockito.mock(StartupEvent.class));

    verify(caseHub).startCase();
  }

  @Test
  void skips_creation_when_case_already_exists() {
    var existing = new CaseInstance();
    when(repository.findByNamespaceAndName("devtown", "evolution", "default"))
        .thenReturn(List.of(existing));

    bootstrap.onStartup(Mockito.mock(StartupEvent.class));

    verify(caseHub, never()).startCase();
  }

    @Test
    void startup_survives_when_startCase_fails() {
        when(repository.findByNamespaceAndName("devtown", "evolution", "default"))
                .thenReturn(List.of());
        when(caseHub.startCase()).thenThrow(new RuntimeException("engine unavailable"));

        assertDoesNotThrow(() -> bootstrap.onStartup(Mockito.mock(StartupEvent.class)));
    }

}
