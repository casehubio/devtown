import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import { emitPagesEvent } from '@casehubio/pages-data';
import { renderVitals } from './shared/vitals.js';
import type { VitalEntry } from './shared/vitals.js';
import '@casehubio/pages-table';

interface SystemHealth {
  activeCases: number;
  fleetSize: number;
  openCommitments: number;
  pendingWorkItems: number;
}

interface MergeQueueMetrics {
  queueDepth: number;
  activeBatches: number;
  throughput24h: number;
  failureRate: number;
  oldestWaitMinutes: number;
  avgWaitMinutes: number;
}

interface Problem {
  category: string;
  severity: string;
  description: string;
  caseId: string | null;
  actorId: string;
  since: string;
}

interface SlaEntry {
  capability: string;
  configuredSeconds: number;
  estimatedMedianSeconds: number;
  estimatedMinSeconds: number;
  estimatedMaxSeconds: number;
  sampleCount: number;
  deviationPercent: number;
}

// --- Problems table ---
const P_CASE = columnId('caseId');
const P_CAT = columnId('category');
const P_SEV = columnId('severity');
const P_DESC = columnId('description');
const P_ACTOR = columnId('actorId');
const P_SINCE = columnId('since');

const PROBLEM_DEFS = [
  { id: P_CASE, name: 'Case ID', type: ColumnType.TEXT, getValue: (r: Problem) => r.caseId ?? '' },
  { id: P_CAT, name: 'Category', type: ColumnType.TEXT, getValue: (r: Problem) => r.category.replace(/_/g, ' ') },
  { id: P_SEV, name: 'Severity', type: ColumnType.TEXT, getValue: (r: Problem) => r.severity },
  { id: P_DESC, name: 'Description', type: ColumnType.TEXT, getValue: (r: Problem) => r.description },
  { id: P_ACTOR, name: 'Actor', type: ColumnType.TEXT, getValue: (r: Problem) => r.actorId ?? '' },
  { id: P_SINCE, name: 'Since', type: ColumnType.TEXT, getValue: (r: Problem) => r.since ? new Date(r.since).toLocaleDateString() : '' },
];

const PROBLEM_COLS: readonly TableColumnConfig[] = [
  { id: P_CASE, sortable: false, visible: false },
  { id: P_CAT, sortable: true },
  { id: P_SEV, sortable: true },
  { id: P_DESC, sortable: false },
  { id: P_ACTOR, sortable: true },
  { id: P_SINCE, sortable: true },
];

// --- SLA table ---
const S_CAP = columnId('capability');
const S_CONF = columnId('configured');
const S_MED = columnId('median');
const S_MIN = columnId('min');
const S_MAX = columnId('max');
const S_SAMPLES = columnId('samples');
const S_DEV = columnId('deviation');

function fmtTime(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const m = Math.round(seconds / 60);
  return `${m}m`;
}

const SLA_DEFS = [
  { id: S_CAP, name: 'Capability', type: ColumnType.TEXT, getValue: (r: SlaEntry) => r.capability },
  { id: S_CONF, name: 'Configured', type: ColumnType.TEXT, getValue: (r: SlaEntry) => fmtTime(r.configuredSeconds) },
  { id: S_MED, name: 'Median', type: ColumnType.TEXT, getValue: (r: SlaEntry) => fmtTime(r.estimatedMedianSeconds) },
  { id: S_MIN, name: 'Min', type: ColumnType.TEXT, getValue: (r: SlaEntry) => fmtTime(r.estimatedMinSeconds) },
  { id: S_MAX, name: 'Max', type: ColumnType.TEXT, getValue: (r: SlaEntry) => fmtTime(r.estimatedMaxSeconds) },
  { id: S_SAMPLES, name: 'Samples', type: ColumnType.NUMBER, getValue: (r: SlaEntry) => r.sampleCount },
  { id: S_DEV, name: 'Deviation', type: ColumnType.TEXT, getValue: (r: SlaEntry) => `${r.deviationPercent > 0 ? '+' : ''}${r.deviationPercent.toFixed(0)}%` },
];

const SLA_COLS: readonly TableColumnConfig[] = [
  { id: S_CAP, sortable: true },
  { id: S_CONF, sortable: true },
  { id: S_MED, sortable: true },
  { id: S_MIN, sortable: true },
  { id: S_MAX, sortable: true },
  { id: S_SAMPLES, sortable: true },
  { id: S_DEV, sortable: true },
];

@customElement('devtown-system-workbench')
export class SystemWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _health: SystemHealth = { activeCases: 0, fleetSize: 0, openCommitments: 0, pendingWorkItems: 0 };
  @state() private _metrics: MergeQueueMetrics = { queueDepth: 0, activeBatches: 0, throughput24h: 0, failureRate: 0, oldestWaitMinutes: 0, avgWaitMinutes: 0 };
  @state() private _problems: Problem[] = [];
  @state() private _sla: SlaEntry[] = [];
  @state() private _problemData: TypedDataSet | undefined;
  @state() private _slaData: TypedDataSet | undefined;

  static override styles = css`
    :host { display: block; padding: 16px 24px; font-family: var(--pages-font-family, system-ui); overflow: auto; }
    .vitals {
      display: grid; gap: 6px;
      padding: 10px 0; border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .vital { text-align: center; padding: 6px; }
    .vital-value { font-size: 20px; font-weight: 700; }
    .vital-label { font-size: 10px; color: var(--pages-neutral-7, #525252); text-transform: uppercase; }
    h2 { font-size: 18px; font-weight: 700; margin: 0 0 8px; }
    h3 { font-size: 14px; font-weight: 600; margin: 20px 0 8px; color: var(--pages-neutral-9, #404040); }
    .health-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 8px;
    }
    .health-card {
      background: var(--pages-neutral-1, #fafafa); border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px; padding: 12px 16px;
    }
    .health-card h4 { margin: 0 0 8px; font-size: 12px; font-weight: 600; text-transform: uppercase; color: var(--pages-neutral-7, #525252); }
    .health-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; }
    .health-label { color: var(--pages-neutral-8, #404040); }
    .health-value { font-weight: 600; }
    .sev-error { color: var(--pages-danger-9, #dc2626); font-weight: 600; }
    .sev-warning { color: var(--pages-warning-9, #d97706); font-weight: 600; }
    .dev-over { color: var(--pages-danger-9, #dc2626); }
    .dev-under { color: var(--pages-success-9, #16a34a); }
    pages-table { cursor: pointer; }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchAll();
  }

  configure(props: Record<string, unknown>): void {
    if (props.endpoint) this.endpoint = String(props.endpoint);
  }

  private async _fetchAll(): Promise<void> {
    try {
      const [hRes, mRes, pRes, sRes] = await Promise.all([
        fetch(`${this.endpoint}/system-health`).catch(() => null),
        fetch(`${this.endpoint}/merge-queue/metrics`).catch(() => null),
        fetch(`${this.endpoint}/problems?threshold_minutes=0`).catch(() => null),
        fetch(`${this.endpoint}/sla-comparison`).catch(() => null),
      ]);

      if (hRes?.ok) this._health = await hRes.json();
      if (mRes?.ok) this._metrics = await mRes.json();
      if (pRes?.ok) {
        this._problems = (await pRes.json()).items ?? [];
        this._problemData = fromRows([...this._problems], PROBLEM_DEFS);
      }
      if (sRes?.ok) {
        this._sla = (await sRes.json()).entries ?? [];
        this._slaData = fromRows([...this._sla], SLA_DEFS);
      }
    } catch (err) {
      console.warn('Failed to fetch system data', err);
    }
  }

  private _handleProblemRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (!detail?.row) return;
    const caseId = detail.row.text(P_CASE);
    if (!caseId) return;
    const tabButton = document.querySelector('button[data-slot="PR Cases"]') as HTMLElement | null;
    if (tabButton) tabButton.click();
    requestAnimationFrame(() => {
      emitPagesEvent(document, 'review:selected', { caseId });
    });
  };

  override render() {
    const h = this._health;
    const m = this._metrics;

    const vitals: VitalEntry[] = [
      { label: 'Active Cases', value: h.activeCases },
      { label: 'Fleet Size', value: h.fleetSize },
      { label: 'Queue Depth', value: m.queueDepth },
      { label: 'Open Commitments', value: h.openCommitments },
      { label: 'Pending Work Items', value: h.pendingWorkItems },
      { label: 'Failure Rate', value: `${Math.round(m.failureRate * 100)}%` },
    ];

    return html`
      <h2>System Health</h2>
      ${renderVitals(vitals)}

      <div class="health-grid">
        <div class="health-card">
          <h4>Review Pipeline</h4>
          <div class="health-row"><span class="health-label">Active cases</span><span class="health-value">${h.activeCases}</span></div>
          <div class="health-row"><span class="health-label">Open commitments</span><span class="health-value">${h.openCommitments}</span></div>
          <div class="health-row"><span class="health-label">Pending work items</span><span class="health-value">${h.pendingWorkItems}</span></div>
          <div class="health-row"><span class="health-label">Fleet size</span><span class="health-value">${h.fleetSize}</span></div>
        </div>
        <div class="health-card">
          <h4>Merge Queue</h4>
          <div class="health-row"><span class="health-label">Queue depth</span><span class="health-value">${m.queueDepth}</span></div>
          <div class="health-row"><span class="health-label">Active batches</span><span class="health-value">${m.activeBatches}</span></div>
          <div class="health-row"><span class="health-label">Oldest wait</span><span class="health-value">${m.oldestWaitMinutes}m</span></div>
          <div class="health-row"><span class="health-label">Avg wait</span><span class="health-value">${m.avgWaitMinutes}m</span></div>
          <div class="health-row"><span class="health-label">24h throughput</span><span class="health-value">${m.throughput24h}</span></div>
          <div class="health-row"><span class="health-label">Failure rate</span><span class="health-value">${Math.round(m.failureRate * 100)}%</span></div>
        </div>
      </div>

      <h3>Problems (${this._problems.length})</h3>
      ${this._problemData ? html`
        <pages-table .dataSet=${this._problemData} .columnConfig=${PROBLEM_COLS} @row-activate=${this._handleProblemRowActivate}></pages-table>
      ` : html`<p>No problems detected.</p>`}

      <h3>SLA Calibration</h3>
      ${this._slaData ? html`
        <pages-table .dataSet=${this._slaData} .columnConfig=${SLA_COLS}></pages-table>
      ` : nothing}
    `;
  }
}
