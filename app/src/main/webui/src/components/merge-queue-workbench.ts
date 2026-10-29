import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { emitPagesEvent, onPagesEvent } from '@casehubio/pages-data';
import { renderVitals } from './shared/vitals.js';
import type { VitalEntry } from './shared/types.js';
import './pr-detail.js';

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
  ciStatus: string;
  prNumbers: number[];
  startedAt: string;
  suspectedPr: number | null;
}

interface MergeQueueMetrics {
  queueDepth: number;
  activeBatches: number;
  throughput24h: number;
  failureRate: number;
  oldestWaitMinutes: number;
  avgWaitMinutes: number;
}

@customElement('devtown-merge-queue-workbench')
export class MergeQueueWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _queuedPrs: QueuedPrEntry[] = [];
  @state() private _batches: ActiveBatchEntry[] = [];
  @state() private _metrics: MergeQueueMetrics = { queueDepth: 0, activeBatches: 0, throughput24h: 0, failureRate: 0, oldestWaitMinutes: 0, avgWaitMinutes: 0 };
  @state() private _selectedPr: QueuedPrEntry | null = null;
  @state() private _selectedBatch: ActiveBatchEntry | null = null;
  @state() private _selectionKind: 'pr' | 'batch' | '' = '';
  @state() private _batchesExpanded = true;

  private _unsubs: Array<() => void> = [];

  static override styles = css`
    :host { display: block; height: 100%; font-family: var(--pages-font-family, system-ui); }
    .list-panel { height: 100%; overflow: auto; padding: 0 12px; }
    .detail-panel { height: 100%; overflow: auto; }
    .section-title { font-size: 13px; font-weight: 600; margin: 16px 0 8px; color: var(--pages-neutral-9, #404040); text-transform: uppercase; }
    .pr-row { padding: 8px 10px; border-radius: 4px; margin-bottom: 4px; cursor: pointer; background: var(--pages-neutral-2, #f5f5f5); display: flex; justify-content: space-between; align-items: center; }
    .pr-row:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .pr-row.selected { background: var(--pages-primary-2, #dbeafe); }
    .pr-title { font-size: 12px; font-weight: 600; }
    .pr-meta { font-size: 10px; color: var(--pages-neutral-7, #525252); }
    .pr-wait { font-size: 10px; color: var(--pages-neutral-6, #737373); }
    .batch-row { padding: 8px 10px; border-radius: 4px; margin-bottom: 4px; cursor: pointer; background: var(--pages-neutral-2, #f5f5f5); }
    .batch-row:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .batch-row.selected { background: var(--pages-primary-2, #dbeafe); }
    .batch-toggle { all: unset; cursor: pointer; font-size: 13px; font-weight: 600; }
    .empty { padding: 40px; text-align: center; color: var(--pages-neutral-6, #737373); }
    .lane-badge { font-size: 9px; padding: 2px 6px; border-radius: 8px; background: var(--pages-neutral-3, #e5e5e5); }
    .lane-badge.fast-track { background: var(--pages-success-2, #dcfce7); color: var(--pages-success-8, #166534); }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchData();
    this._unsubs.push(
      onPagesEvent(document, 'review:deselected', () => {
        this._selectedPr = null;
        this._selectedBatch = null;
        this._selectionKind = '';
      }),
    );
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    this._unsubs.forEach(u => u());
    this._unsubs = [];
  }

  configure(props: Record<string, unknown>): void {
    if (props.endpoint) this.endpoint = String(props.endpoint);
  }

  private async _fetchData(): Promise<void> {
    try {
      const [mqRes, metRes] = await Promise.all([
        fetch(`${this.endpoint}/merge-queue`),
        fetch(`${this.endpoint}/merge-queue/metrics`),
      ]);
      if (mqRes.ok) {
        const mq = await mqRes.json();
        this._queuedPrs = mq.queuedPrs ?? [];
        this._batches = mq.activeBatches ?? [];
      }
      if (metRes.ok) {
        this._metrics = await metRes.json();
      }
    } catch (e) {
      console.warn('Merge queue fetch error', e);
    }
  }

  private _selectPr(pr: QueuedPrEntry): void {
    this._selectedPr = pr;
    this._selectedBatch = null;
    this._selectionKind = 'pr';
  }

  private _selectBatch(batch: ActiveBatchEntry): void {
    this._selectedBatch = batch;
    this._selectedPr = null;
    this._selectionKind = 'batch';
  }

  override render() {
    const vitals: VitalEntry[] = [
      { label: 'Queue Depth', value: String(this._metrics.queueDepth) },
      { label: 'Active Batches', value: String(this._metrics.activeBatches) },
      { label: 'Throughput (24h)', value: String(this._metrics.throughput24h) },
      { label: 'Failure Rate', value: `${Math.round(this._metrics.failureRate * 100)}%` },
    ];

    return html`
      <pages-split-workbench selection-topic="merge-queue" title="Merge Queue">
        <div slot="list" class="list-panel">
          ${renderVitals(vitals)}

          <div class="section-title">Queued PRs (${this._queuedPrs.length})</div>
          ${this._queuedPrs.length === 0 ? html`<div style="font-size:11px;color:var(--pages-neutral-6,#737373)">No PRs in queue</div>` : nothing}
          ${this._queuedPrs.map(pr => html`
            <div class="pr-row ${this._selectedPr?.number === pr.number ? 'selected' : ''}"
                 @click=${() => this._selectPr(pr)}>
              <div>
                <div class="pr-title">PR #${pr.number} <span class="lane-badge ${pr.priorityLane === 'fast-track' ? 'fast-track' : ''}">${pr.priorityLane}</span></div>
                <div class="pr-meta">${pr.author} · ${pr.repository.split('/').pop()}</div>
              </div>
              <div class="pr-wait">${pr.waitMinutes}m</div>
            </div>
          `)}

          ${this._batches.length > 0 ? html`
            <button class="batch-toggle" @click=${() => { this._batchesExpanded = !this._batchesExpanded; }}>
              ${this._batchesExpanded ? '▾' : '▸'} Active Batches (${this._batches.length})
            </button>
            ${this._batchesExpanded ? this._batches.map(b => html`
              <div class="batch-row ${this._selectedBatch?.batchId === b.batchId ? 'selected' : ''}"
                   @click=${() => this._selectBatch(b)}>
                <div class="pr-title">Batch ${b.batchId.substring(0, 8)} · ${b.prCount} PRs</div>
                <div class="pr-meta">Risk: ${b.riskLevel} · CI: ${b.ciStatus}</div>
              </div>
            `) : nothing}
          ` : nothing}
        </div>
        <div slot="detail" class="detail-panel">
          ${this._selectionKind === 'batch' && this._selectedBatch
            ? html`<devtown-pr-detail
                mode="batch"
                endpoint="${this.endpoint}"
                .batch=${this._selectedBatch}
              ></devtown-pr-detail>`
            : this._selectionKind === 'pr' && this._selectedPr
              ? html`<devtown-pr-detail
                  endpoint="/api/devtown/reviews"
                  stage="QUEUED"
                  .queueContext=${{ priorityLane: this._selectedPr.priorityLane, trustScore: this._selectedPr.trustScore, waitMinutes: this._selectedPr.waitMinutes, headSha: this._selectedPr.headSha, dependsOn: this._selectedPr.dependsOn }}
                ></devtown-pr-detail>`
              : html`<div class="empty">Select a queued PR or active batch</div>`
          }
        </div>
      </pages-split-workbench>
    `;
  }
}
