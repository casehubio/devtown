import { html, type TemplateResult } from 'lit';
import type { TimelineStrategy, TimelineNode } from '@casehubio/blocks-ui-blocks-timeline';

interface TimelineEvent {
  timestamp: string;
  category: string;
  eventType: string;
  actor: string;
  summary: string;
  metadata: Record<string, unknown> | null;
}

const CATEGORY_ICONS: Record<string, string> = {
  LIFECYCLE: '●',
  ORCHESTRATION: '◆',
  AGENT: '▶',
  WORKITEM: '■',
  TRUST: '★',
  CI: '○',
  SIGNAL: '→',
};

const CATEGORY_STATUS: Record<string, 'completed' | 'active' | 'pending'> = {
  LIFECYCLE: 'completed',
  ORCHESTRATION: 'active',
  AGENT: 'completed',
  WORKITEM: 'pending',
  TRUST: 'completed',
  CI: 'completed',
  SIGNAL: 'active',
};

function tryParse(v: unknown): unknown {
  if (typeof v === 'string') {
    try { return JSON.parse(v); } catch { return v; }
  }
  return v;
}

function renderValue(v: unknown, depth = 0): TemplateResult | string {
  v = tryParse(v);

  if (v == null) return html`<span style="color:#999">null</span>`;
  if (typeof v === 'boolean') return html`<span style="color:${v ? '#16a34a' : '#dc2626'}">${String(v)}</span>`;
  if (typeof v === 'number') return html`<span style="color:#2563eb">${v}</span>`;
  if (typeof v === 'string') return html`<span>${v}</span>`;

  if (Array.isArray(v)) {
    if (v.length === 0) return html`<span style="color:#999">[]</span>`;
    return html`
      <div style="padding-left:${depth > 0 ? 12 : 0}px">
        ${v.map((item, i) => html`
          <div style="display:flex;gap:4px;padding:1px 0">
            <span style="color:#999;min-width:16px">${i}.</span>
            ${renderValue(item, depth + 1)}
          </div>
        `)}
      </div>`;
  }

  if (typeof v === 'object') {
    const entries = Object.entries(v as Record<string, unknown>);
    if (entries.length === 0) return html`<span style="color:#999">{}</span>`;
    return html`
      <div style="padding-left:${depth > 0 ? 12 : 0}px">
        ${entries.map(([k, val]) => {
          const parsed = tryParse(val);
          const isComplex = typeof parsed === 'object' && parsed !== null;
          return html`
            <div style="padding:2px 0">
              <span style="font-weight:600;color:var(--pages-neutral-8,#525252)">${k}</span>${isComplex
                ? html`${renderValue(parsed, depth + 1)}`
                : html`<span style="margin-left:8px">${renderValue(parsed, depth + 1)}</span>`}
            </div>`;
        })}
      </div>`;
  }

  return String(v);
}

export const reviewTimelineStrategy: TimelineStrategy<TimelineEvent[]> = {
  defaultLayout: 'vertical',
  filterCategories: ['LIFECYCLE', 'ORCHESTRATION', 'AGENT', 'WORKITEM', 'SIGNAL'],
  toNodes(events: TimelineEvent[]): TimelineNode[] {
    return events.map((e, i) => ({
      key: `${e.timestamp}-${i}`,
      label: e.summary,
      status: CATEGORY_STATUS[e.category] ?? 'completed',
      timestamp: e.timestamp,
      actor: e.actor,
      category: e.category,
      detail: e.metadata,
    }));
  },
  renderNode(node: TimelineNode) {
    const icon = CATEGORY_ICONS[node.category ?? ''] ?? '●';
    return html`
      <span style="margin-right:6px;opacity:0.6">${icon}</span>
      <span>${node.label}</span>
      ${node.actor && node.actor !== 'system'
        ? html`<span style="margin-left:8px;opacity:0.5;font-size:0.9em">— ${node.actor}</span>`
        : ''}
    `;
  },
  renderDetail(node: TimelineNode) {
    let meta = node.detail;
    if (!meta) return html`<div style="padding:8px;font-size:12px;color:#666">No additional details</div>`;

    if (typeof meta === 'string') {
      try { meta = JSON.parse(meta); } catch { /* keep as string */ }
    }

    return html`<div style="padding:8px 12px;font-size:12px">${renderValue(meta)}</div>`;
  },
};
