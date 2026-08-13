package io.casehub.devtown.app;

import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.api.spi.WorkItemStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
class WorkItemQueries {

    @Inject WorkItemStore store;

    @Transactional
    List<WorkItemEntity> scanAll() {
        return store.scanAll();
    }
}
