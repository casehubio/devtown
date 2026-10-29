import { CATEGORY_ICONS, CATEGORY_STATUS, type TimelineEvent } from './review-timeline-strategy.js';

export type { TimelineEvent };

export interface SpineNode {
  key: string;
  label: string;
  icon: string;
  status: 'completed' | 'active' | 'pending';
  timestamp: string;
  nodeType: 'case-header' | 'dispatch' | 'findings' | 'human-gate' | 'lifecycle';
  nodeData: unknown;
}

function formatCapability(cap: string): string {
  return cap.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).replace(/^(\w)/, c => c.toUpperCase());
}

function parseMeta(metadata: Record<string, unknown> | null): Record<string, unknown> {
  if (!metadata) return {};
  if (typeof metadata === 'string') {
    try { return JSON.parse(metadata); } catch { return {}; }
  }
  return metadata;
}

export function synthesizeSpineNodes(events: TimelineEvent[]): SpineNode[] {
  return events.map((e, i) => {
    const meta = parseMeta(e.metadata);
    const cap = (meta.capabilityName as string) ?? '';
    const capLabel = cap ? formatCapability(cap) : '';
    const icon = CATEGORY_ICONS[e.category] ?? '●';
    const baseStatus = CATEGORY_STATUS[e.category] ?? 'completed';

    let label: string;
    let nodeType: SpineNode['nodeType'] = 'lifecycle';
    let status = baseStatus;

    if (e.eventType === 'CASE_CREATED') {
      label = 'Opened';
    } else if (e.eventType === 'AGENT_ROUTED') {
      label = `${capLabel} → ${e.actor}`;
      nodeType = 'dispatch';
      status = 'active';
    } else if (e.eventType === 'WORKER_EXECUTION_COMPLETED') {
      const outcome = meta.outcome as string | undefined;
      const findingCount = meta.findingCount as number | undefined;
      if (outcome === 'FINDINGS' && findingCount) {
        label = `${capLabel}: ${findingCount} findings`;
        nodeType = 'findings';
      } else if (outcome === 'APPROVED') {
        label = `${capLabel}: APPROVED`;
      } else if (cap === 'code-analysis') {
        label = meta.securitySensitive ? 'Code analysis: security-sensitive' : 'Code analysis: standard';
      } else {
        label = `${capLabel}: ${outcome ?? 'DONE'}`;
      }
      status = 'completed';
    } else if (e.eventType === 'WORKITEM_CREATED') {
      label = 'Human gate';
      nodeType = 'human-gate';
      status = 'pending';
    } else if (e.eventType === 'WORKITEM_COMPLETED') {
      label = 'Human gate: decided';
      nodeType = 'human-gate';
      status = 'completed';
    } else if (e.category === 'CI') {
      label = `CI: ${e.summary.toLowerCase().includes('pass') ? 'passed' : e.summary.toLowerCase().includes('fail') ? 'failed' : 'pending'}`;
      status = e.summary.toLowerCase().includes('pass') ? 'completed' : e.summary.toLowerCase().includes('fail') ? 'completed' : 'pending';
    } else {
      label = e.summary || e.eventType;
    }

    return {
      key: `${e.timestamp}-${i}`,
      label,
      icon,
      status,
      timestamp: e.timestamp,
      nodeType,
      nodeData: { ...meta, actor: e.actor, eventType: e.eventType, caseId: meta.caseId },
    };
  });
}
