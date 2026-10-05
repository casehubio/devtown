import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import '@casehubio/pages-table';

// ── Types ──────────────────────────────────────────────

interface ReviewEntry {
  caseId: string; prNumber: number; repo: string; contributor: string;
  status: string; linesChanged: number; startedAt: string; lastEventAt: string;
}

interface Problem {
  category: string; severity: string; description: string;
  actorId: string | null; since: string | null; caseId: string | null;
}

interface EventEntry {
  timestamp: string; caseId: string; repo: string; prNumber: number;
  eventType: string; actorId: string | null; caseStatus: string;
}

interface SystemHealth {
  activeCases: number; fleetSize: number;
  openCommitments: number; pendingWorkItems: number;
}

// ── Column definitions ─────────────────────────────────

const PR_COL = columnId('prNumber');
const REPO_COL = columnId('repo');
const CONTRIB_COL = columnId('contributor');
const STATUS_COL = columnId('status');
const LINES_COL = columnId('linesChanged');

const REVIEW_COLUMNS = [
  { id: PR_COL, name: 'PR', type: ColumnType.NUMBER, getValue: (r: ReviewEntry) => r.prNumber },
  { id: REPO_COL, name: 'Repo', type: ColumnType.TEXT, getValue: (r: ReviewEntry) => r.repo },
  { id: CONTRIB_COL, name: 'Author', type: ColumnType.TEXT, getValue: (r: ReviewEntry) => r.contributor },
  { id: STATUS_COL, name: 'Status', type: ColumnType.TEXT, getValue: (r: ReviewEntry) => r.status },
  { id: LINES_COL, name: 'Lines', type: ColumnType.NUMBER, getValue: (r: ReviewEntry) => r.linesChanged },
];
const REVIEW_CONFIG: readonly TableColumnConfig[] = [
  { id: PR_COL, sortable: true }, { id: REPO_COL, sortable: true },
  { id: CONTRIB_COL, sortable: true }, { id: STATUS_COL, sortable: true },
  { id: LINES_COL, sortable: true },
];

const SEV_COL = columnId('severity');
const DESC_COL = columnId('description');
const PROB_ACTOR_COL = columnId('actorId');
const SINCE_COL = columnId('since');

const PROBLEM_COLUMNS = [
  { id: SEV_COL, name: 'Severity', type: ColumnType.TEXT, getValue: (r: Problem) => r.severity },
  { id: DESC_COL, name: 'Description', type: ColumnType.TEXT, getValue: (r: Problem) => r.description },
  { id: PROB_ACTOR_COL, name: 'Actor', type: ColumnType.TEXT, getValue: (r: Problem) => r.actorId ?? '' },
  { id: SINCE_COL, name: 'Since', type: ColumnType.TEXT, getValue: (r: Problem) => r.since ?? '' },
];
const PROBLEM_CONFIG: readonly TableColumnConfig[] = [
  { id: SEV_COL, sortable: true }, { id: DESC_COL, sortable: true },
  { id: PROB_ACTOR_COL, sortable: true }, { id: SINCE_COL, sortable: true },
];

const TS_COL = columnId('timestamp');
const EVT_COL = columnId('eventType');
const ACTOR_COL = columnId('actorId');
const CS_COL = columnId('caseStatus');
const EVT_PR_COL = columnId('prNumber');

const EVENT_COLUMNS = [
  { id: TS_COL, name: 'Time', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.timestamp },
  { id: EVT_PR_COL, name: 'PR', type: ColumnType.NUMBER, getValue: (r: EventEntry) => r.prNumber },
  { id: EVT_COL, name: 'Event', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.eventType },
  { id: ACTOR_COL, name: 'Actor', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.actorId ?? '' },
  { id: CS_COL, name: 'Status', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.caseStatus },
];
const EVENT_CONFIG: readonly TableColumnConfig[] = [
  { id: TS_COL, sortable: true }, { id: EVT_PR_COL, sortable: true },
  { id: EVT_COL, sortable: true }, { id: ACTOR_COL, sortable: true },
  { id: CS_COL, sortable: true },
];

// Detail-pane event columns (filtered to one case)
const DTS_COL = columnId('dTimestamp');
const DEVT_COL = columnId('dEventType');
const DACTOR_COL = columnId('dActorId');
const DCS_COL = columnId('dCaseStatus');

const DETAIL_EVENT_COLUMNS = [
  { id: DTS_COL, name: 'Time', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.timestamp },
  { id: DEVT_COL, name: 'Event', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.eventType },
  { id: DACTOR_COL, name: 'Actor', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.actorId ?? '' },
  { id: DCS_COL, name: 'Status', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.caseStatus },
];
const DETAIL_EVENT_CONFIG: readonly TableColumnConfig[] = [
  { id: DTS_COL, sortable: true }, { id: DEVT_COL, sortable: true },
  { id: DACTOR_COL, sortable: true }, { id: DCS_COL, sortable: true },
];

@customElement('devtown-operations-workbench')
export class OperationsWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _reviews: ReviewEntry[] = [];
  @state() private _problems: Problem[] = [];
  @state() private _events: EventEntry[] = [];
  @state() private _health: SystemHealth = { activeCases: 0, fleetSize: 0, openCommitments: 0, pendingWorkItems: 0 };

  @state() private _reviewData: TypedDataSet | undefined;
  @state() private _problemData: TypedDataSet | undefined;
  @state() private _eventData: TypedDataSet | undefined;

  @state() private _selectedCaseId = '';
  @state() private _detailEvents: TypedDataSet | undefined;

  static override styles = css`
    :host { display: flex; height: 100%; font-family: var(--pages-font-family, system-ui); }
    .main { flex: 1; overflow: auto; padding: 16px; min-width: 0; }
    .detail {
      width: 40%; min-width: 320px; max-width: 500px;
      border-left: 1px solid var(--pages-neutral-4, #d4d4d4);
      overflow: auto; padding: 16px;
    }
    .detail.empty {
      display: flex; align-items: center; justify-content: center;
      color: var(--pages-neutral-7, #525252); font-size: 13px;
    }
    h3 { font-size: 14px; font-weight: 600; margin: 20px 0 8px; }
    h3:first-child { margin-top: 0; }
    .vitals {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 16px;
    }
    .vital {
      background: var(--pages-neutral-2, #f5f5f5); border-radius: 6px;
      padding: 10px 12px; text-align: center;
    }
    .vital-value { font-size: 24px; font-weight: 700; }
    .vital-label { font-size: 11px; color: var(--pages-neutral-7, #525252); text-transform: uppercase; }
    .detail-header { font-size: 16px; font-weight: 600; margin-bottom: 10px; }
    .detail-meta { display: grid; grid-template-columns: auto 1fr; gap: 3px 10px; font-size: 12px; margin-bottom: 14px; }
    .detail-meta dt { font-weight: 600; color: var(--pages-neutral-8, #404040); }
    .detail-meta dd { margin: 0; }
    .section-title { font-size: 13px; font-weight: 600; margin: 14px 0 6px; }
    .actions { display: flex; gap: 8px; margin: 10px 0; }
    .actions button {
      padding: 5px 12px; border-radius: 4px; font-size: 12px; font-weight: 500;
      cursor: pointer; border: 1px solid var(--pages-neutral-5, #a3a3a3);
      background: white; color: var(--pages-neutral-9, #171717);
    }
    .actions button:hover { background: var(--pages-neutral-2, #f5f5f5); }
    .actions button.primary {
      background: var(--pages-primary-9, #1d4ed8); color: white;
      border-color: var(--pages-primary-9, #1d4ed8);
    }
    .actions button.primary:hover { background: var(--pages-primary-10, #1e40af); }
    .actions button.warn { border-color: var(--pages-warning-7, #a16207); color: var(--pages-warning-9, #854d0e); }
    .action-result {
      font-size: 11px; padding: 4px 8px; margin: 4px 0 8px;
      background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px;
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchAll();
    this._interval = window.setInterval(() => this._fetchAll(), 10000);
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    if (this._interval) clearInterval(this._interval);
  }

  private _interval: number | undefined;

  configure(props: Record<string, unknown>): void {
    if (props.endpoint) this.endpoint = String(props.endpoint);
  }

  private async _fetchAll(): Promise<void> {
    const base = this.endpoint;
    const [qRes, pRes, eRes, hRes] = await Promise.all([
      fetch(`${base}/queue-status`).catch(() => null),
      fetch(`${base}/problems`).catch(() => null),
      fetch(`${base}/recent-events?limit=50`).catch(() => null),
      fetch(`${base}/system-health`).catch(() => null),
    ]);

    if (qRes?.ok) {
      const json = await qRes.json();
      this._reviews = json.reviews ?? [];
      this._reviewData = fromRows([...this._reviews], REVIEW_COLUMNS);
    }
    if (pRes?.ok) {
      const json = await pRes.json();
      this._problems = json.items ?? [];
      this._problemData = fromRows([...this._problems], PROBLEM_COLUMNS);
    }
    if (eRes?.ok) {
      this._events = await eRes.json();
      this._eventData = fromRows([...this._events], EVENT_COLUMNS);
    }
    if (hRes?.ok) {
      this._health = await hRes.json();
    }

    if (this._selectedCaseId) {
      this._updateDetailEvents();
    }
  }

  private _updateDetailEvents(): void {
    const filtered = this._events.filter(e => e.caseId === this._selectedCaseId);
    this._detailEvents = fromRows([...filtered], DETAIL_EVENT_COLUMNS);
  }

  private _handleReviewClick = (e: Event): void => {
    const row = (e as CustomEvent).detail?.row;
    if (!row) return;
    const pr = row.number(PR_COL);
    const entry = this._reviews.find(r => r.prNumber === pr);
    if (entry && entry.caseId !== this._selectedCaseId) {
      this._selectedCaseId = entry.caseId;
      this._detailEvents = undefined;
      this._updateDetailEvents();
    }
  };

  private _handleEventClick = (e: Event): void => {
    const row = (e as CustomEvent).detail?.row;
    if (!row) return;
    const pr = row.number(EVT_PR_COL);
    const entry = this._reviews.find(r => r.prNumber === pr);
    if (entry && entry.caseId !== this._selectedCaseId) {
      this._selectedCaseId = entry.caseId;
      this._detailEvents = undefined;
      this._updateDetailEvents();
    }
  };

  private _selectedReview(): ReviewEntry | undefined {
    return this._reviews.find(r => r.caseId === this._selectedCaseId);
  }

  override render() {
    const selected = this._selectedReview();
    return html`
      <div class="main">
        <div class="vitals">
          ${this._renderVital(this._health.activeCases, 'Active Cases')}
          ${this._renderVital(this._health.fleetSize, 'Fleet Size')}
          ${this._renderVital(this._health.openCommitments, 'Commitments')}
          ${this._renderVital(this._health.pendingWorkItems, 'Work Items')}
        </div>

        ${this._problems.length > 0 ? html`
          <h3>Problems</h3>
          ${this._problemData ? html`<pages-table .dataSet=${this._problemData} .columnConfig=${PROBLEM_CONFIG}></pages-table>` : nothing}
        ` : nothing}

        <h3>Active Reviews</h3>
        ${this._reviewData ? html`
          <pages-table .dataSet=${this._reviewData} .columnConfig=${REVIEW_CONFIG}
            @row-activate=${this._handleReviewClick}></pages-table>
        ` : html`<div>No active reviews</div>`}

        <h3>Event Stream</h3>
        ${this._eventData ? html`
          <pages-table .dataSet=${this._eventData} .columnConfig=${EVENT_CONFIG}
            @row-activate=${this._handleEventClick}></pages-table>
        ` : html`<div>No events</div>`}
      </div>

      ${selected ? html`
        <div class="detail">
          ${this._renderDetail(selected)}
        </div>
      ` : html`
        <div class="detail empty">Click a review or event to see details</div>
      `}
    `;
  }

  private _renderVital(value: number, label: string) {
    return html`
      <div class="vital">
        <div class="vital-value">${value}</div>
        <div class="vital-label">${label}</div>
      </div>
    `;
  }

  @state() private _actionResult = '';

  private async _doAction(action: string, entry: ReviewEntry): Promise<void> {
    try {
      const res = await fetch(`/api/actions/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ repo: entry.repo, prNumber: entry.prNumber, contributor: entry.contributor, headSha: 'dev-' + entry.prNumber }),
      });
      const json = await res.json();
      this._actionResult = `${json.action}: ${json.result}`;
      this._fetchAll();
    } catch (err) {
      this._actionResult = `Error: ${err}`;
    }
  }

  private _renderDetail(entry: ReviewEntry) {
    return html`
      <div class="detail-header">PR #${entry.prNumber} — ${entry.repo}</div>
      <dl class="detail-meta">
        <dt>Author</dt><dd>${entry.contributor}</dd>
        <dt>Status</dt><dd>${entry.status}</dd>
        <dt>Lines</dt><dd>${entry.linesChanged}</dd>
        <dt>Started</dt><dd>${entry.startedAt}</dd>
        <dt>Last Event</dt><dd>${entry.lastEventAt}</dd>
      </dl>
      <div class="actions">
        <button @click=${() => this._doAction('approve', entry)}>Approve</button>
        <button class="warn" @click=${() => this._doAction('request-changes', entry)}>Request Changes</button>
        <button class="primary" @click=${() => this._doAction('enqueue', entry)}>Merge Queue</button>
      </div>
      ${this._actionResult ? html`<div class="action-result">${this._actionResult}</div>` : nothing}
      <div class="section-title">Case Events</div>
      ${this._detailEvents ? html`
        <pages-table .dataSet=${this._detailEvents} .columnConfig=${DETAIL_EVENT_CONFIG}></pages-table>
      ` : html`<div>Loading...</div>`}
    `;
  }
}
