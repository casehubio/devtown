package io.casehub.devtown.app.spi;

import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.api.spi.routing.RoutingPromptSection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import java.util.List;

@ApplicationScoped
public class DevRoutingPromptAssembler {

    @Produces
    @ApplicationScoped
    RoutingPromptAssembler routingPromptAssembler(Instance<RoutingPromptSection> sections) {
        return new RoutingPromptAssembler(sections.stream().toList());
    }
}
