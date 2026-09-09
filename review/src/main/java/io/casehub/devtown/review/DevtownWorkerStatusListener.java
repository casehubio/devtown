/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.devtown.review;

import io.casehub.api.model.WorkResult;
import io.casehub.api.spi.WorkerStatusListener;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Devtown-specific worker lifecycle listener. Logs worker state transitions and can be extended to
 * update PR check status, notify maintainers, or record metrics for review SLA tracking.
 */
@ApplicationScoped
public class DevtownWorkerStatusListener implements WorkerStatusListener {

  private static final Logger LOG = Logger.getLogger(DevtownWorkerStatusListener.class);

  @Override
  public void onWorkerStarted(String workerId, Map<String, String> sessionMeta) {
    LOG.infof("Review worker started: %s", workerId);
  }

  @Override
  public void onWorkerCompleted(String workerId, WorkResult result) {
    LOG.infof("Review worker completed: %s status=%s", workerId,
        result != null ? result.status() : "null");
  }

  @Override
  public void onWorkerStalled(String workerId) {
    LOG.warnf("Review worker stalled: %s — may require manual intervention", workerId);
  }
}
