package io.casehub.devtown.app.spi;

import io.casehub.neocortex.cognitive.index.TemporalIndex;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class DevTemporalIndex {

    @Produces
    @ApplicationScoped
    TemporalIndex temporalIndex(Instance<MindMapStore> mindMaps,
                                Instance<CaseMemoryStore> memory,
                                Instance<CbrRecordStore> cbr) {
        return new TemporalIndex(mindMaps, memory, cbr);
    }
}
