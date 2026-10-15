import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import { emitPagesEvent, onPagesEvent } from '@casehubio/pages-data';
import { renderVitals } from './shared/vitals.js';
import type { VitalEntry } from './shared/vitals.js';
import '@casehubio/pages-table';
import { PagesSplitWorkbench } from '@casehubio/pages-ui-components/split-workbench';
if (!customElements.get('pages-split-workbench')) customElements.define('pages-split-workbench', PagesSplitWorkbench);
import './pr-detail.js';

interface ReviewEntry {
  caseId: string;
  prNumber: number;
  repo: string;
  contributor: string;
  status: string;
  linesChanged: number;
  startedAt: string;
  lastEventAt: string;
}

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

interface TriageItem {
  workItemId: string;
  prRef: string;
  decisionType: string;
  candidateGroup: string;
  expiresAt: string;
  escalationStage: string;
  status: string;
  createdAt: string;
  caseId: string;
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
}

export interface UnifiedPrRow {
  caseId: string;
  prNumber: number;
  repo: string;
  author: string;
  stage: string;
  status: string;
  linesChanged: number;
  priorityLane: string;
  trustScore: number;
  waitMinutes: number;
  headSha: string;
  dependsOn: number[];
  decisionType: string;
  candidateGroup: string;
  expiresAt: string;
  escalationStage: string;
}

type FilterKey = 'all' | 'review' | 'decision' | 'queued';

const COL_PR = columnId('prNumber');
const COL_REPO = columnId('repo');
const COL_AUTHOR = columnId('author');
const COL_STAGE = columnId('stage');
const COL_STATUS = columnId('status');
const COL_LINES = columnId('linesChanged');
const COL_LANE = columnId('priorityLane');
const COL_TRUST = columnId('trustScore');
const COL_WAIT = columnId('waitMinutes');
const COL_DECISION_TYPE = columnId('decisionType');
const COL_CANDIDATE = columnId('candidateGroup');
const COL_EXPIRES = columnId('expiresAt');
const COL_ESCALATION = columnId('escalationStage');

function formatEnum(v: string): string {
  if (!v) return '';
  return v.replace(/[_-]/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

function shortRepo(v: string): string {
  const parts = v.split('/');
  return parts.length > 1 ? parts[parts.length - 1] : v;
}

const DEF_PR = { id: COL_PR, name: 'PR', type: ColumnType.NUMBER, getValue: (r: UnifiedPrRow) => r.prNumber };
const DEF_REPO = { id: COL_REPO, name: 'Repo', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => shortRepo(r.repo) };
const DEF_AUTHOR = { id: COL_AUTHOR, name: 'Author', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => r.author };
const DEF_STAGE = { id: COL_STAGE, name: 'Stage', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => formatEnum(r.stage) };
const DEF_STATUS = { id: COL_STATUS, name: 'Status', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => formatEnum(r.status) };
const DEF_LINES = { id: COL_LINES, name: 'Lines', type: ColumnType.NUMBER, getValue: (r: UnifiedPrRow) => r.linesChanged };
const DEF_LANE = { id: COL_LANE, name: 'Lane', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => formatEnum(r.priorityLane) };
const DEF_TRUST = { id: COL_TRUST, name: 'Trust', type: ColumnType.NUMBER, getValue: (r: UnifiedPrRow) => r.trustScore };
const DEF_WAIT = { id: COL_WAIT, name: 'Wait', type: ColumnType.NUMBER, getValue: (r: UnifiedPrRow) => r.waitMinutes };
const DEF_DECISION = { id: COL_DECISION_TYPE, name: 'Decision', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => formatEnum(r.decisionType.replace(/^human-decision:/, '').replace(/^human-oversight:/, '')) };
const DEF_CANDIDATE = { id: COL_CANDIDATE, name: 'Group', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => r.candidateGroup };
const DEF_EXPIRES = { id: COL_EXPIRES, name: 'Expires', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => r.expiresAt ? new Date(r.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '' };
const DEF_ESCALATION = { id: COL_ESCALATION, name: 'Escalation', type: ColumnType.TEXT, getValue: (r: UnifiedPrRow) => formatEnum(r.escalationStage) };

const ALL_COLUMN_DEFS = [DEF_PR, DEF_REPO, DEF_AUTHOR, DEF_STAGE, DEF_STATUS, DEF_LINES, DEF_LANE, DEF_TRUST, DEF_WAIT, DEF_DECISION, DEF_CANDIDATE, DEF_EXPIRES, DEF_ESCALATION];

const FILTER_DEFS: Record<FilterKey, typeof ALL_COLUMN_DEFS> = {
  all: [DEF_PR, DEF_REPO, DEF_AUTHOR, DEF_STAGE, DEF_STATUS],
  review: [DEF_PR, DEF_REPO, DEF_AUTHOR, DEF_STATUS, DEF_LINES],
  decision: [DEF_PR, DEF_REPO, DEF_AUTHOR, DEF_DECISION, DEF_CANDIDATE, DEF_EXPIRES, DEF_ESCALATION],
  queued: [DEF_PR, DEF_REPO, DEF_AUTHOR, DEF_LANE, DEF_TRUST, DEF_WAIT],
};

const FILTER_COLUMNS: Record<FilterKey, readonly TableColumnConfig[]> = {
  all: [
    { id: COL_PR, sortable: true },
    { id: COL_REPO, sortable: true },
    { id: COL_AUTHOR, sortable: true },
    { id: COL_STAGE, sortable: true },
    { id: COL_STATUS, sortable: true },
  ],
  review: [
    { id: COL_PR, sortable: true },
    { id: COL_REPO, sortable: true },
    { id: COL_AUTHOR, sortable: true },
    { id: COL_STATUS, sortable: true },
    { id: COL_LINES, sortable: true },
  ],
  decision: [
    { id: COL_PR, sortable: true },
    { id: COL_REPO, sortable: true },
    { id: COL_AUTHOR, sortable: true },
    { id: COL_DECISION_TYPE, sortable: true },
    { id: COL_CANDIDATE, sortable: true },
    { id: COL_EXPIRES, sortable: true },
    { id: COL_ESCALATION, sortable: true },
  ],
  queued: [
    { id: COL_PR, sortable: true },
    { id: COL_REPO, sortable: true },
    { id: COL_AUTHOR, sortable: true },
    { id: COL_LANE, sortable: true },
    { id: COL_TRUST, sortable: true },
    { id: COL_WAIT, sortable: true },
  ],
};

const BATCH_ID_COL = columnId('batchId');
const BATCH_PRS_COL = columnId('prList');
const BATCH_CI_COL = columnId('ciStatus');
const BATCH_RISK_COL = columnId('riskLevel');

const BATCH_COLUMNS = [
  { id: BATCH_ID_COL, name: 'ID', type: ColumnType.TEXT, getValue: (r: ActiveBatchEntry) => r.batchId },
  { id: BATCH_PRS_COL, name: 'PRs', type: ColumnType.TEXT, getValue: (r: ActiveBatchEntry) => (r.prNumbers ?? []).map(n => `#${n}`).join(', ') || `${r.prCount} PRs` },
  { id: BATCH_CI_COL, name: 'CI', type: ColumnType.TEXT, getValue: (r: ActiveBatchEntry) => r.ciStatus ?? '—' },
  { id: BATCH_RISK_COL, name: 'Risk', type: ColumnType.TEXT, getValue: (r: ActiveBatchEntry) => r.riskLevel },
];

const BATCH_TABLE_CONFIG: readonly TableColumnConfig[] = [
  { id: BATCH_ID_COL, sortable: false, visible: false },
  { id: BATCH_PRS_COL, sortable: false },
  { id: BATCH_CI_COL, sortable: true },
  { id: BATCH_RISK_COL, sortable: true },
];

@customElement('devtown-pr-cases-workbench')
export class PrCasesWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _filter: FilterKey = 'all';
  @state() private _selectedCaseId = '';
  @state() private _selectionKind: 'pr' | 'batch' = 'pr';

  @state() private _reviews: ReviewEntry[] = [];
  @state() private _queuedPrs: QueuedPrEntry[] = [];
  @state() private _triageItems: TriageItem[] = [];
  @state() private _batches: ActiveBatchEntry[] = [];
  @state() private _health: SystemHealth = { activeCases: 0, fleetSize: 0, openCommitments: 0, pendingWorkItems: 0 };
  @state() private _metrics: MergeQueueMetrics = { queueDepth: 0, activeBatches: 0, throughput24h: 0, failureRate: 0, oldestWaitMinutes: 0, avgWaitMinutes: 0 };
  @state() private _problems: Problem[] = [];

  @state() private _unified: UnifiedPrRow[] = [];
  @state() private _listData: TypedDataSet | undefined;
  @state() private _batchData: TypedDataSet | undefined;
  @state() private _batchesExpanded = true;

  @state() private _selectedBatch: ActiveBatchEntry | null = null;

  private _unsubs: Array<() => void> = [];

  static override styles = css`
    :host { display: block; height: 100%; font-family: var(--pages-font-family, system-ui); }
    pages-split-workbench { height: 100%; }
    .list-panel { height: 100%; overflow: auto; padding: 0 12px; }
    .detail-panel { height: 100%; }
    .filter-chips {
      display: flex; gap: 6px; padding: 10px 0;
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .filter-chip {
      padding: 4px 12px; border-radius: 16px; font-size: 12px; font-weight: 500;
      cursor: pointer; border: 1px solid var(--pages-neutral-4, #d4d4d4);
      background: white; color: var(--pages-neutral-9, #171717);
      transition: all 0.15s;
    }
    .filter-chip:hover { background: var(--pages-neutral-2, #f5f5f5); }
    .filter-chip.active {
      background: var(--pages-primary-9, #1d4ed8); color: white;
      border-color: var(--pages-primary-9, #1d4ed8);
    }
    .filter-count { margin-left: 4px; font-size: 11px; opacity: 0.8; }
    .vitals {
      display: grid; gap: 6px;
      padding: 10px 0; border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .vital { text-align: center; padding: 6px; }
    .vital-value { font-size: 20px; font-weight: 700; }
    .vital-label { font-size: 10px; color: var(--pages-neutral-7, #525252); text-transform: uppercase; }
    .problems-banner {
      padding: 8px 12px; margin: 8px 0;
      background: var(--pages-warning-3, #fef3c7); border: 1px solid var(--pages-warning-6, #d97706);
      border-radius: 6px; font-size: 12px;
    }
    .problem-item { padding: 3px 0; display: flex; gap: 8px; align-items: center; cursor: pointer; }
    .problem-item:hover { text-decoration: underline; }
    .problem-severity {
      font-size: 10px; font-weight: 600; padding: 1px 5px; border-radius: 3px;
      background: var(--pages-warning-9, #92400e); color: white;
    }
    .problem-severity.error { background: var(--pages-danger-9, #dc2626); }
    h3 { font-size: 13px; font-weight: 600; margin: 12px 0 6px; color: var(--pages-neutral-9, #404040); }
    h3.section-first { margin-top: 4px; }
    .batch-toggle {
      font-size: 12px; cursor: pointer; border: none; background: none;
      color: var(--pages-neutral-9, #404040); font-weight: 600; padding: 0;
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchAll();
    this._unsubs.push(
      onPagesEvent(document, 'review:deselected', () => {
        this._selectedCaseId = '';
        this._selectedBatch = null;
      }),
      onPagesEvent(document, 'review:selected', (detail: Record<string, unknown>) => {
        const caseId = detail?.caseId as string;
        if (caseId) {
          this._selectionKind = 'pr';
          this._selectedCaseId = caseId;
          this._selectedBatch = null;
        }
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

  private async _fetchAll(): Promise<void> {
    try {
      const [qsRes, mqRes, trRes, hRes, metRes, pRes] = await Promise.all([
        fetch(`${this.endpoint}/queue-status`).catch(() => null),
        fetch(`${this.endpoint}/merge-queue`).catch(() => null),
        fetch(`${this.endpoint}/triage`).catch(() => null),
        fetch(`${this.endpoint}/system-health`).catch(() => null),
        fetch(`${this.endpoint}/merge-queue/metrics`).catch(() => null),
        fetch(`${this.endpoint}/problems?threshold_minutes=0`).catch(() => null),
      ]);

      if (qsRes?.ok) {
        const json = await qsRes.json();
        this._reviews = json.reviews ?? [];
      }
      if (mqRes?.ok) {
        const json = await mqRes.json();
        this._queuedPrs = json.queuedPrs ?? [];
        this._batches = json.activeBatches ?? [];
        this._batchData = fromRows([...this._batches], BATCH_COLUMNS);
      }
      if (trRes?.ok) {
        const json = await trRes.json();
        this._triageItems = json.items ?? [];
      }
      if (hRes?.ok) this._health = await hRes.json();
      if (metRes?.ok) this._metrics = await metRes.json();
      if (pRes?.ok) this._problems = (await pRes.json()).items ?? [];

      this._mergeData();
    } catch (err) {
      console.warn('Failed to fetch PR cases data', err);
    }
  }

  private _mergeData(): void {
    const byCase = new Map<string, UnifiedPrRow>();
    const triageByCaseId = new Map<string, TriageItem>();
    for (const t of this._triageItems) {
      triageByCaseId.set(t.caseId, t);
    }

    for (const r of this._reviews) {
      const triage = triageByCaseId.get(r.caseId);
      const isQueued = this._queuedPrs.some(q => q.number === r.prNumber && q.repository === r.repo);
      byCase.set(r.caseId, {
        caseId: r.caseId,
        prNumber: r.prNumber,
        repo: r.repo,
        author: r.contributor,
        stage: triage ? 'HUMAN_GATE' : isQueued ? 'QUEUED' : 'REVIEW',
        status: r.status,
        linesChanged: r.linesChanged,
        priorityLane: '',
        trustScore: 0,
        waitMinutes: 0,
        headSha: '',
        dependsOn: [],
        decisionType: triage?.decisionType ?? '',
        candidateGroup: triage?.candidateGroup ?? '',
        expiresAt: triage?.expiresAt ?? '',
        escalationStage: triage?.escalationStage ?? '',
      });
    }

    for (const q of this._queuedPrs) {
      const existing = [...byCase.values()].find(r => r.prNumber === q.number && r.repo === q.repository);
      if (existing) {
        existing.stage = 'QUEUED';
        existing.priorityLane = q.priorityLane;
        existing.trustScore = q.trustScore;
        existing.waitMinutes = q.waitMinutes;
        existing.headSha = q.headSha;
        existing.dependsOn = q.dependsOn;
      } else {
        const fakeId = `queue-${q.repository}-${q.number}`;
        byCase.set(fakeId, {
          caseId: fakeId,
          prNumber: q.number,
          repo: q.repository,
          author: q.author,
          stage: 'QUEUED',
          status: 'QUEUED',
          linesChanged: 0,
          priorityLane: q.priorityLane,
          trustScore: q.trustScore,
          waitMinutes: q.waitMinutes,
          headSha: q.headSha,
          dependsOn: q.dependsOn,
          decisionType: '',
          candidateGroup: '',
          expiresAt: '',
          escalationStage: '',
        });
      }
    }

    const batchedPrs = new Set<number>();
    for (const b of this._batches) {
      for (const n of (b.prNumbers ?? [])) batchedPrs.add(n);
    }
    for (const row of byCase.values()) {
      if (batchedPrs.has(row.prNumber) && row.stage === 'QUEUED') {
        row.stage = 'BATCHED';
      }
    }

    for (const t of this._triageItems) {
      if (!byCase.has(t.caseId)) {
        const parts = t.prRef.split('#');
        const repo = parts[0] ?? '';
        const prNum = parseInt(parts[1] ?? '0', 10);
        const existing = [...byCase.values()].find(r => r.prNumber === prNum && r.repo === repo);
        if (existing) {
          existing.stage = 'HUMAN_GATE';
          existing.decisionType = t.decisionType;
          existing.candidateGroup = t.candidateGroup;
          existing.expiresAt = t.expiresAt;
          existing.escalationStage = t.escalationStage;
        } else {
          byCase.set(t.caseId, {
            caseId: t.caseId,
            prNumber: prNum,
            repo,
            author: '',
            stage: 'HUMAN_GATE',
            status: 'WAITING',
            linesChanged: 0,
            priorityLane: '',
            trustScore: 0,
            waitMinutes: 0,
            headSha: '',
            dependsOn: [],
            decisionType: t.decisionType,
            candidateGroup: t.candidateGroup,
            expiresAt: t.expiresAt,
            escalationStage: t.escalationStage,
          });
        }
      }
    }

    this._unified = [...byCase.values()];
    this._rebuildTable();
  }

  private _filteredRows(): UnifiedPrRow[] {
    switch (this._filter) {
      case 'review': return this._unified.filter(r => r.stage === 'REVIEW' || r.stage === 'HUMAN_GATE');
      case 'decision': return this._unified.filter(r => r.stage === 'HUMAN_GATE');
      case 'queued': return this._unified.filter(r => r.stage === 'QUEUED' || r.stage === 'BATCHED');
      default: return this._unified;
    }
  }

  private _rebuildTable(): void {
    const rows = this._filteredRows();
    this._listData = fromRows([...rows], FILTER_DEFS[this._filter]);
  }

  private _filterCounts(): Record<FilterKey, number> {
    return {
      all: this._unified.length,
      review: this._unified.filter(r => r.stage === 'REVIEW' || r.stage === 'HUMAN_GATE').length,
      decision: this._unified.filter(r => r.stage === 'HUMAN_GATE').length,
      queued: this._unified.filter(r => r.stage === 'QUEUED' || r.stage === 'BATCHED').length,
    };
  }

  private _setFilter(f: FilterKey): void {
    this._filter = f;
    this._rebuildTable();
  }

  private _vitals(): VitalEntry[] {
    const h = this._health;
    const m = this._metrics;
    switch (this._filter) {
      case 'review':
        return [
          { label: 'Active', value: h.activeCases },
          { label: 'Fleet', value: h.fleetSize },
          { label: 'Obligations', value: h.openCommitments },
          { label: 'Work Items', value: h.pendingWorkItems },
        ];
      case 'decision': {
        const escalated = this._triageItems.filter(t => t.escalationStage !== 'initial').length;
        return [
          { label: 'Pending', value: this._triageItems.length },
          { label: 'Escalated', value: escalated },
        ];
      }
      case 'queued':
        return [
          { label: 'Depth', value: m.queueDepth },
          { label: 'Batches', value: m.activeBatches },
          { label: '24h Thru', value: m.throughput24h },
          { label: 'Fail Rate', value: `${Math.round(m.failureRate * 100)}%` },
          { label: 'Oldest (m)', value: m.oldestWaitMinutes },
          { label: 'Avg (m)', value: m.avgWaitMinutes },
        ];
      default:
        return [
          { label: 'Active', value: h.activeCases },
          { label: 'Queued', value: m.queueDepth },
          { label: 'Decisions', value: this._triageItems.length },
          { label: 'Fleet', value: h.fleetSize },
        ];
    }
  }

  private _handleRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (detail?.row) {
      const prNum = detail.row.number(COL_PR);
      const entry = this._filteredRows().find(r => r.prNumber === prNum);
      if (entry) {
        this._selectionKind = 'pr';
        this._selectedCaseId = entry.caseId;
        this._selectedBatch = null;
        emitPagesEvent(document, 'review:selected', { caseId: entry.caseId });
      }
    }
  };

  private _handleBatchRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (detail?.row) {
      const batchId = detail.row.text(BATCH_ID_COL);
      const entry = this._batches.find(b => b.batchId === batchId);
      if (entry) {
        this._selectionKind = 'batch';
        this._selectedBatch = entry;
        this._selectedCaseId = '';
      }
    }
  };

  private _handleProblemClick(p: Problem): void {
    if (p.caseId) {
      this._selectionKind = 'pr';
      this._selectedCaseId = p.caseId;
      this._selectedBatch = null;
      emitPagesEvent(document, 'review:selected', { caseId: p.caseId });
    }
  }

  override render() {
    const counts = this._filterCounts();
    const vitals = this._vitals();
    const selectedRow = this._unified.find(r => r.caseId === this._selectedCaseId) ?? null;

    return html`
      <pages-split-workbench selection-topic="review" title="PR Cases">
        <div slot="list" class="list-panel">
          <div class="filter-chips">
            ${this._renderChip('all', 'All', counts.all)}
            ${this._renderChip('review', 'In Review', counts.review)}
            ${this._renderChip('decision', 'Needs Decision', counts.decision)}
            ${this._renderChip('queued', 'Queued', counts.queued)}
          </div>

          ${renderVitals(vitals)}

          ${this._problems.length > 0 ? html`
            <div class="problems-banner">
              ${this._problems.map(p => html`
                <div class="problem-item" @click=${() => this._handleProblemClick(p)}>
                  <span class="problem-severity ${p.severity === 'error' ? 'error' : ''}">${p.severity}</span>
                  <span>${p.description}</span>
                </div>
              `)}
            </div>
          ` : nothing}

          <h3>${this._filterLabel()} (${this._filteredRows().length})</h3>
          ${this._listData ? html`
            <pages-table
              .dataSet=${this._listData}
              .columnConfig=${FILTER_COLUMNS[this._filter]}
              @row-activate=${this._handleRowActivate}
            ></pages-table>
          ` : nothing}

          ${this._filter === 'queued' && this._batches.length > 0 ? html`
            <h3>
              <button class="batch-toggle" @click=${() => { this._batchesExpanded = !this._batchesExpanded; }}>
                ${this._batchesExpanded ? '▾' : '▸'} Active Batches (${this._batches.length})
              </button>
            </h3>
            ${this._batchesExpanded && this._batchData ? html`
              <pages-table
                .dataSet=${this._batchData}
                .columnConfig=${BATCH_TABLE_CONFIG}
                @row-activate=${this._handleBatchRowActivate}
              ></pages-table>
            ` : nothing}
          ` : nothing}
        </div>
        <div slot="detail" class="detail-panel">
          ${this._selectionKind === 'batch' && this._selectedBatch
            ? html`<devtown-pr-detail
                mode="batch"
                endpoint="${this.endpoint}"
                .batch=${this._selectedBatch}
              ></devtown-pr-detail>`
            : html`<devtown-pr-detail
                case-id=${this._selectedCaseId}
                endpoint="/api/devtown/reviews"
                .stage=${selectedRow?.stage ?? ''}
                .queueContext=${{ priorityLane: selectedRow?.priorityLane ?? '', trustScore: selectedRow?.trustScore ?? 0, waitMinutes: selectedRow?.waitMinutes ?? 0, headSha: selectedRow?.headSha ?? '', dependsOn: selectedRow?.dependsOn ?? [] }}
                .triageContext=${{ caseId: selectedRow?.caseId ?? '', decisionType: selectedRow?.decisionType ?? '', candidateGroup: selectedRow?.candidateGroup ?? '', expiresAt: selectedRow?.expiresAt ?? '', escalationStage: selectedRow?.escalationStage ?? '' }}
              ></devtown-pr-detail>`
          }
        </div>
      </pages-split-workbench>
    `;
  }

  private _renderChip(key: FilterKey, label: string, count: number) {
    return html`
      <button class="filter-chip ${this._filter === key ? 'active' : ''}"
              @click=${() => this._setFilter(key)}>
        ${label}<span class="filter-count">(${count})</span>
      </button>
    `;
  }

  private _filterLabel(): string {
    switch (this._filter) {
      case 'review': return 'In Review';
      case 'decision': return 'Needs Decision';
      case 'queued': return 'Queued PRs';
      default: return 'All PR Cases';
    }
  }
}
