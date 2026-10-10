import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import '@casehubio/pages-table';
import '@casehubio/blocks-ui-split-workbench';
import './merge-queue-detail.js';

interface QueuedPrEntry {
  number: number;
  repository: string;
  author: string;
  headSha: string;
  priorityLane: string;
  trustScore: number;
  waitMinutes: number;
  enqueuedAt: string;
  dependsOn: number[];
}

interface ActiveBatchEntry {
  caseId: string;
  batchId: string;
  prCount: number;
  riskLevel: string;
  ciStatus?: string;
  prNumbers?: number[];
  startedAt?: string;
  suspectedPr?: number;
}

interface MergeQueueMetrics {
  queueDepth: number;
  activeBatches: number;
  throughput24h: number;
  failureRate: number;
  oldestWaitMinutes: number;
  avgWaitMinutes: number;
}

// ── Queued PRs table columns ────────────────────────────────
const PR_NUM_COL = columnId('number');
const PR_REPO_COL = columnId('repository');
const PR_AUTHOR_COL = columnId('author');
const PR_LANE_COL = columnId('priorityLane');
const PR_TRUST_COL = columnId('trustScore');
const PR_WAIT_COL = columnId('waitMinutes');

const PR_COLUMNS = [
  { id: PR_NUM_COL, name: 'PR', type: ColumnType.NUMBER, getValue: (r: QueuedPrEntry) => r.number },
  { id: PR_REPO_COL, name: 'Repo', type: ColumnType.TEXT, getValue: (r: QueuedPrEntry) => r.repository },
  { id: PR_AUTHOR_COL, name: 'Author', type: ColumnType.TEXT, getValue: (r: QueuedPrEntry) => r.author },
  { id: PR_LANE_COL, name: 'Lane', type: ColumnType.TEXT, getValue: (r: QueuedPrEntry) => r.priorityLane },
  { id: PR_TRUST_COL, name: 'Trust', type: ColumnType.NUMBER, getValue: (r: QueuedPrEntry) => r.trustScore },
  { id: PR_WAIT_COL, name: 'Wait (min)', type: ColumnType.NUMBER, getValue: (r: QueuedPrEntry) => r.waitMinutes },
];

const PR_TABLE_CONFIG: readonly TableColumnConfig[] = [
  { id: PR_NUM_COL, sortable: true },
  { id: PR_REPO_COL, sortable: true },
  { id: PR_AUTHOR_COL, sortable: true },
  { id: PR_LANE_COL, sortable: true },
  { id: PR_TRUST_COL, sortable: true },
  { id: PR_WAIT_COL, sortable: true },
];

// ── Active Batches table columns ────────────────────────────
const BATCH_PRS_COL = columnId('prList');
const BATCH_CI_COL = columnId('ciStatus');
const BATCH_RISK_COL = columnId('riskLevel');
const BATCH_ID_COL = columnId('batchId');

const BATCH_COLUMNS = [
  { id: BATCH_PRS_COL, name: 'PRs', type: ColumnType.TEXT, getValue: (r: ActiveBatchEntry) => (r.prNumbers ?? []).map(n => `#${n}`).join(', ') || `${r.prCount} PRs` },
  { id: BATCH_CI_COL, name: 'CI', type: ColumnType.TEXT, getValue: (r: ActiveBatchEntry) => r.ciStatus ?? '—' },
  { id: BATCH_RISK_COL, name: 'Risk', type: ColumnType.TEXT, getValue: (r: ActiveBatchEntry) => r.riskLevel },
];

const BATCH_TABLE_CONFIG: readonly TableColumnConfig[] = [
  { id: BATCH_PRS_COL, sortable: false },
  { id: BATCH_CI_COL, sortable: true },
  { id: BATCH_RISK_COL, sortable: true },
];

type SelectionKind = 'pr' | 'batch';

@customElement('devtown-merge-queue-workbench')
export class MergeQueueWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _prEntries: QueuedPrEntry[] = [];
  @state() private _batchEntries: ActiveBatchEntry[] = [];
  @state() private _prData: TypedDataSet | undefined;
  @state() private _batchData: TypedDataSet | undefined;
  @state() private _metrics: MergeQueueMetrics = { queueDepth: 0, activeBatches: 0, throughput24h: 0, failureRate: 0, oldestWaitMinutes: 0, avgWaitMinutes: 0 };

  @state() private _reviews: Array<{ caseId: string; status: string; prNumber: number; repo: string; capabilities: Array<{ name: string; status: string; outcome: string | null }> }> = [];
  @state() private _selectionKind: SelectionKind | null = null;
  @state() private _selectedPr: QueuedPrEntry | null = null;
  @state() private _selectedBatch: ActiveBatchEntry | null = null;

  static override styles = css`
    :host { display: block; height: 100%; font-family: var(--pages-font-family, system-ui); }
    blocks-split-workbench { height: 100%; }
    .list-panel { height: 100%; overflow: auto; padding: 0 12px; }
    .detail-panel { height: 100%; }
    .vitals {
      display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px;
      padding: 10px 0; border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .vital { text-align: center; padding: 6px; }
    .vital-value { font-size: 20px; font-weight: 700; }
    .vital-label { font-size: 10px; color: var(--pages-neutral-7, #525252); text-transform: uppercase; }
    h3 { font-size: 13px; font-weight: 600; margin: 12px 0 6px; color: var(--pages-neutral-9, #404040); }
    h3.section-first { margin-top: 4px; }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchData();
  }

  configure(props: Record<string, unknown>): void {
    if (props.endpoint) this.endpoint = String(props.endpoint);
  }

  private async _fetchData(): Promise<void> {
    try {
      const [mqRes, metRes, qsRes] = await Promise.all([
        fetch(`${this.endpoint}/merge-queue`).catch(() => null),
        fetch(`${this.endpoint}/merge-queue/metrics`).catch(() => null),
        fetch(`${this.endpoint}/queue-status`).catch(() => null),
      ]);
      if (mqRes?.ok) {
        const json = await mqRes.json();
        this._prEntries = json.queuedPrs ?? [];
        this._batchEntries = json.activeBatches ?? [];
        this._prData = fromRows([...this._prEntries], PR_COLUMNS);
        this._batchData = fromRows([...this._batchEntries], BATCH_COLUMNS);
      }
      if (metRes?.ok) {
        this._metrics = await metRes.json();
      }
      if (qsRes?.ok) {
        const qsJson = await qsRes.json();
        this._reviews = (qsJson.reviews ?? []).map((r: Record<string, unknown>) => ({ caseId: r.caseId, status: r.status, prNumber: r.prNumber, repo: r.repo, capabilities: [] }));
      }
    } catch (err) {
      console.warn('Failed to fetch merge queue data', err);
    }
  }

  private _handlePrRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (detail?.row) {
      const prNum = detail.row.number(PR_NUM_COL);
      const entry = this._prEntries.find(r => r.number === prNum);
      if (entry) {
        this._selectionKind = 'pr';
        this._selectedPr = entry;
        this._selectedBatch = null;
      }
    }
  };

  private _handleBatchRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (detail?.row) {
      const prListText = detail.row.text(BATCH_PRS_COL);
      const entry = this._batchEntries.find(r => (r.prNumbers ?? []).map(n => `#${n}`).join(', ') === prListText);
      if (entry) {
        this._selectionKind = 'batch';
        this._selectedBatch = entry;
        this._selectedPr = null;
      }
    }
  };

  override render() {
    const m = this._metrics;
    return html`
      <blocks-split-workbench selection-topic="merge-queue" title="Merge Queue">
        <div slot="list" class="list-panel">
          <h3 class="section-first">Queue Vitals</h3>
          <div class="vitals">
            <div class="vital"><div class="vital-value">${m.queueDepth}</div><div class="vital-label">Depth</div></div>
            <div class="vital"><div class="vital-value">${m.activeBatches}</div><div class="vital-label">Batches</div></div>
            <div class="vital"><div class="vital-value">${m.throughput24h}</div><div class="vital-label">24h Thru</div></div>
            <div class="vital"><div class="vital-value">${Math.round(m.failureRate * 100)}%</div><div class="vital-label">Fail Rate</div></div>
            <div class="vital"><div class="vital-value">${m.oldestWaitMinutes}</div><div class="vital-label">Oldest (m)</div></div>
            <div class="vital"><div class="vital-value">${m.avgWaitMinutes}</div><div class="vital-label">Avg (m)</div></div>
          </div>

          <h3>Queued PRs</h3>
          ${this._prData ? html`
            <pages-table
              .dataSet=${this._prData}
              .columnConfig=${PR_TABLE_CONFIG}
              @row-activate=${this._handlePrRowActivate}
            ></pages-table>
          ` : nothing}

          <h3>Active Batches</h3>
          ${this._batchData ? html`
            <pages-table
              .dataSet=${this._batchData}
              .columnConfig=${BATCH_TABLE_CONFIG}
              @row-activate=${this._handleBatchRowActivate}
            ></pages-table>
          ` : nothing}
        </div>
        <div slot="detail" class="detail-panel">
          <devtown-merge-queue-detail
            endpoint=${this.endpoint}
            .queuedPr=${this._selectionKind === 'pr' ? this._selectedPr : null}
            .batch=${this._selectionKind === 'batch' ? this._selectedBatch : null}
            .reviews=${this._reviews}
          ></devtown-merge-queue-detail>
        </div>
      </blocks-split-workbench>
    `;
  }
}
