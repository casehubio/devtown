import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import { emitPagesEvent } from '@casehubio/blocks-ui-core';
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

// Detail pane removed — clicking reviews/events navigates to Reviews tab

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

  static override styles = css`
    :host { display: block; height: 100%; font-family: var(--pages-font-family, system-ui); overflow: auto; padding: 16px; }
    h3 { font-size: 14px; font-weight: 600; margin: 20px 0 8px; }
    h3:first-child { margin-top: 0; }
    .vitals {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 16px;
    }
    .vital {
      background: var(--pages-neutral-2, #f5f5f5); border-radius: 6px;
      padding: 10px 12px; text-align: center; cursor: pointer;
    }
    .vital:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .vital-value { font-size: 24px; font-weight: 700; }
    .vital-label { font-size: 11px; color: var(--pages-neutral-7, #525252); text-transform: uppercase; }
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

  }

  private _navigateToReview(caseId: string): void {
    emitPagesEvent(document, 'review:selected', { caseId });
    const reviewTab = document.querySelector('button[data-tab="Reviews"], [role="tab"]');
    const allTabs = document.querySelectorAll('[role="tab"]');
    for (const tab of allTabs) {
      if (tab.textContent?.trim() === 'Reviews') {
        (tab as HTMLElement).click();
        break;
      }
    }
  }

  private _handleReviewClick = (e: Event): void => {
    const row = (e as CustomEvent).detail?.row;
    if (!row) return;
    const pr = row.number(PR_COL);
    const entry = this._reviews.find(r => r.prNumber === pr);
    if (entry) this._navigateToReview(entry.caseId);
  };

  private _handleEventClick = (e: Event): void => {
    const row = (e as CustomEvent).detail?.row;
    if (!row) return;
    const pr = row.number(EVT_PR_COL);
    const entry = this._reviews.find(r => r.prNumber === pr);
    if (entry) this._navigateToReview(entry.caseId);
  };

  private _handleProblemClick = (e: Event): void => {
    const row = (e as CustomEvent).detail?.row;
    if (!row) return;
    const caseId = row.text(columnId('caseId'));
    if (caseId) this._navigateToReview(caseId);
  };

  override render() {
    return html`
      <div class="vitals">
        ${this._renderVital(this._health.activeCases, 'Active Cases')}
        ${this._renderVital(this._health.fleetSize, 'Fleet Size')}
        ${this._renderVital(this._health.openCommitments, 'Commitments')}
        ${this._renderVital(this._health.pendingWorkItems, 'Work Items')}
      </div>

      ${this._problems.length > 0 ? html`
        <h3>Problems</h3>
        ${this._problemData ? html`<pages-table .dataSet=${this._problemData} .columnConfig=${PROBLEM_CONFIG}
          @row-activate=${this._handleProblemClick}></pages-table>` : nothing}
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
}
