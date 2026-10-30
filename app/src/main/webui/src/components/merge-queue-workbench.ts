import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { emitPagesEvent, onPagesEvent } from '@casehubio/pages-data';
import { renderVitals } from './shared/vitals.js';
import { renderTrustBar } from './shared/trust-bar.js';
import { renderBadge } from './shared/badge.js';
import type { VitalEntry } from './shared/types.js';
import { trustBarStyles, badgeStyles } from './shared/shared-styles.js';
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

interface BatchPrEntry {
  number: number;
  repository: string;
  headSha: string;
  author: string;
  trustScore: number;
  lane: string;
}

interface BatchStatus {
  batchId: string;
  caseId: string;
  prs: BatchPrEntry[];
  riskLevel: string;
  bisectionStrategy: string;
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
  @state() private _batchDetail: BatchStatus | null = null;
  @state() private _batchesExpanded = true;

  private _unsubs: Array<() => void> = [];

  static override styles = [
    trustBarStyles, badgeStyles,
    css`
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
      .batch-detail { padding: 16px; }
      .batch-header { margin-bottom: 12px; }
      .batch-field { display: flex; gap: 8px; align-items: center; padding: 4px 0; font-size: 12px; }
      .batch-field-label { font-weight: 600; min-width: 100px; color: var(--pages-neutral-7, #525252); }
      .risk-badge { font-size: 10px; padding: 2px 8px; border-radius: 8px; font-weight: 600; }
      .risk-badge.high { background: var(--pages-danger-2, #fee2e2); color: var(--pages-danger-8, #991b1b); }
      .risk-badge.medium { background: var(--pages-warning-2, #fef3c7); color: var(--pages-warning-8, #92400e); }
      .risk-badge.low { background: var(--pages-success-2, #dcfce7); color: var(--pages-success-8, #166534); }
      .bisect-badge { font-size: 10px; padding: 2px 8px; border-radius: 8px; background: var(--pages-primary-2, #dbeafe); color: var(--pages-primary-8, #1e40af); }
      .suspect-row { background: var(--pages-danger-1, #fef2f2); border-left: 3px solid var(--pages-danger-6, #dc2626); }
      .suspect-warn { font-size: 12px; color: var(--pages-danger-8, #991b1b); font-weight: 600; padding: 6px 10px; margin: 8px 0; background: var(--pages-danger-1, #fef2f2); border-radius: 4px; }
      .member-row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border-bottom: 1px solid var(--pages-neutral-2, #f5f5f5); }
      .member-pr { font-size: 12px; font-weight: 600; min-width: 60px; }
      .member-author { font-size: 12px; min-width: 60px; }
      .member-trust { flex: 1; }
      .member-lane { min-width: 70px; }
    `,
  ];

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

  private async _selectBatch(batch: ActiveBatchEntry): Promise<void> {
    this._selectedBatch = batch;
    this._selectedPr = null;
    this._selectionKind = 'batch';
    this._batchDetail = null;
    try {
      const res = await fetch(`${this.endpoint}/merge-queue/batch/${batch.caseId}`);
      if (res.ok) this._batchDetail = await res.json();
    } catch { /* unavailable */ }
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
            ? html`
                <div class="batch-detail">
                  <div class="batch-header">
                    <div class="section-title">Batch ${this._selectedBatch.batchId.substring(0, 8)}</div>
                    <div class="batch-field">
                      <span class="batch-field-label">Risk Level</span>
                      <span class="risk-badge ${this._selectedBatch.riskLevel.toLowerCase()}">${this._selectedBatch.riskLevel}</span>
                    </div>
                    <div class="batch-field">
                      <span class="batch-field-label">CI Status</span>
                      <span>${this._selectedBatch.ciStatus}${this._selectedBatch.ciStatus === 'BISECTING' ? html` <span class="bisect-badge">bisecting</span>` : nothing}</span>
                    </div>
                    ${this._batchDetail ? html`
                      <div class="batch-field">
                        <span class="batch-field-label">Strategy</span>
                        <span class="bisect-badge">${this._batchDetail.bisectionStrategy}</span>
                      </div>
                    ` : nothing}
                    ${this._selectedBatch.suspectedPr != null ? html`
                      <div class="suspect-warn">Bisection suspect: PR #${this._selectedBatch.suspectedPr}</div>
                    ` : nothing}
                  </div>
                  ${this._batchDetail ? html`
                    <div class="section-title">Members (${this._batchDetail.prs.length})</div>
                    ${this._batchDetail.prs.map(pr => html`
                      <div class="member-row ${this._selectedBatch!.suspectedPr === pr.number ? 'suspect-row' : ''}">
                        <span class="member-pr">PR #${pr.number}</span>
                        <span class="member-author">${pr.author}</span>
                        <span class="member-trust">${renderTrustBar({ score: pr.trustScore })}</span>
                        <span class="member-lane">${renderBadge(pr.lane, pr.lane === 'FAST_TRACK' ? 'fast-track' : pr.lane === 'SLOW_LANE' ? 'triage' : 'standard')}</span>
                      </div>
                    `)}
                  ` : html`<div class="empty">Loading batch detail...</div>`}
                </div>
              `
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
