import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import '@casehubio/pages-table';

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

interface EventEntry {
  timestamp: string;
  eventType: string;
  actorId: string;
  caseStatus: string;
}

const CASE_COL = columnId('caseId');
const PR_COL = columnId('prNumber');
const REPO_COL = columnId('repo');
const CONTRIB_COL = columnId('contributor');
const STATUS_COL = columnId('status');
const LINES_COL = columnId('linesChanged');

const LIST_COLUMNS = [
  { id: PR_COL, name: 'PR', type: ColumnType.NUMBER, getValue: (r: ReviewEntry) => r.prNumber },
  { id: REPO_COL, name: 'Repo', type: ColumnType.TEXT, getValue: (r: ReviewEntry) => r.repo },
  { id: CONTRIB_COL, name: 'Author', type: ColumnType.TEXT, getValue: (r: ReviewEntry) => r.contributor },
  { id: STATUS_COL, name: 'Status', type: ColumnType.TEXT, getValue: (r: ReviewEntry) => r.status },
  { id: LINES_COL, name: 'Lines', type: ColumnType.NUMBER, getValue: (r: ReviewEntry) => r.linesChanged },
];

const LIST_TABLE_CONFIG: readonly TableColumnConfig[] = [
  { id: PR_COL, sortable: true },
  { id: REPO_COL, sortable: true },
  { id: CONTRIB_COL, sortable: true },
  { id: STATUS_COL, sortable: true },
  { id: LINES_COL, sortable: true },
];

const TS_COL = columnId('timestamp');
const EVT_COL = columnId('eventType');
const ACTOR_COL = columnId('actorId');
const CS_COL = columnId('caseStatus');

const EVENT_COLUMNS = [
  { id: TS_COL, name: 'Time', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.timestamp },
  { id: EVT_COL, name: 'Event', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.eventType },
  { id: ACTOR_COL, name: 'Actor', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.actorId ?? '' },
  { id: CS_COL, name: 'Status', type: ColumnType.TEXT, getValue: (r: EventEntry) => r.caseStatus },
];

const EVENT_TABLE_CONFIG: readonly TableColumnConfig[] = [
  { id: TS_COL, sortable: true },
  { id: EVT_COL, sortable: true },
  { id: ACTOR_COL, sortable: true },
  { id: CS_COL, sortable: true },
];

@customElement('devtown-review-workbench')
export class ReviewWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _selectedCaseId = '';
  @state() private _listData: TypedDataSet | undefined;
  @state() private _eventData: TypedDataSet | undefined;
  @state() private _entries: ReviewEntry[] = [];
  @state() private _loading = true;

  static override styles = css`
    :host { display: flex; height: 100%; font-family: var(--pages-font-family, system-ui); }
    .list-panel {
      width: 40%; min-width: 320px;
      border-right: 1px solid var(--pages-neutral-4, #d4d4d4);
      display: flex; flex-direction: column; overflow: hidden;
    }
    .list-header {
      padding: 12px 16px; font-size: 14px; font-weight: 600;
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .list-table { flex: 1; overflow: auto; }
    .detail-panel { flex: 1; overflow: auto; padding: 16px; }
    .detail-header { font-size: 18px; font-weight: 600; margin-bottom: 12px; }
    .detail-meta { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; margin-bottom: 16px; font-size: 13px; }
    .detail-meta dt { font-weight: 600; color: var(--pages-neutral-8, #404040); }
    .detail-meta dd { margin: 0; }
    .section-title { font-size: 14px; font-weight: 600; margin: 16px 0 8px; }
    .empty-detail {
      display: flex; align-items: center; justify-content: center;
      height: 100%; color: var(--pages-neutral-7, #525252); font-size: 13px;
    }
    .error { color: var(--pages-danger-9, #dc2626); padding: 16px; }
    .actions { display: flex; gap: 8px; margin: 12px 0; }
    .actions button {
      padding: 6px 14px; border-radius: 4px; font-size: 13px; font-weight: 500;
      cursor: pointer; border: 1px solid var(--pages-neutral-5, #a3a3a3);
      background: white; color: var(--pages-neutral-9, #171717);
    }
    .actions button:hover { background: var(--pages-neutral-2, #f5f5f5); }
    .actions button.primary {
      background: var(--pages-primary-9, #1d4ed8); color: white;
      border-color: var(--pages-primary-9, #1d4ed8);
    }
    .actions button.primary:hover { background: var(--pages-primary-10, #1e40af); }
    .actions button.secondary { border-color: var(--pages-warning-7, #a16207); color: var(--pages-warning-9, #854d0e); }
    .action-result {
      font-size: 12px; padding: 6px 10px; margin: 4px 0 8px;
      background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px;
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchReviews();
  }

  configure(props: Record<string, unknown>): void {
    if (props.endpoint) this.endpoint = String(props.endpoint);
  }

  private async _fetchReviews(): Promise<void> {
    this._loading = true;
    try {
      const res = await fetch(`${this.endpoint}/queue-status`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      this._entries = json.reviews ?? [];
      this._listData = fromRows([...this._entries], LIST_COLUMNS);
    } finally {
      this._loading = false;
    }
  }

  private async _fetchEvents(caseId: string): Promise<void> {
    this._eventData = undefined;
    try {
      const res = await fetch(`${this.endpoint}/recent-events?limit=100`);
      if (!res.ok) return;
      const events: EventEntry[] = await res.json();
      const filtered = events.filter((e: any) => e.caseId === caseId);
      this._eventData = fromRows([...filtered], EVENT_COLUMNS);
    } catch (err) {
      console.warn('Failed to fetch events', err);
    }
  }

  private _handleRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (detail?.row) {
      const pr = detail.row.number(PR_COL);
      const entry = this._entries.find(r => r.prNumber === pr);
      if (entry && entry.caseId !== this._selectedCaseId) {
        this._selectedCaseId = entry.caseId;
        this._eventData = undefined;
        this._fetchEvents(entry.caseId);
      }
    }
  };

  private _selectedEntry(): ReviewEntry | undefined {
    return this._entries.find(r => r.caseId === this._selectedCaseId);
  }

  override render() {
    const selected = this._selectedEntry();
    return html`
      <div class="list-panel">
        <div class="list-header">Reviews</div>
        <div class="list-table">
          ${this._listData ? html`
            <pages-table
              .dataSet=${this._listData}
              .columnConfig=${LIST_TABLE_CONFIG}
              @row-activate=${this._handleRowActivate}
            ></pages-table>
          ` : nothing}
        </div>
      </div>
      <div class="detail-panel">
        ${selected ? this._renderDetail(selected) :
          html`<div class="empty-detail">Select a review to see details</div>`}
      </div>
    `;
  }

  private async _doAction(action: string, entry: ReviewEntry): Promise<void> {
    try {
      const res = await fetch(`/api/actions/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ repo: entry.repo, prNumber: entry.prNumber, contributor: entry.contributor, headSha: 'dev-' + entry.prNumber }),
      });
      const json = await res.json();
      this._actionResult = `${json.action}: ${json.result}`;
      this._fetchReviews();
      this._fetchEvents(entry.caseId);
    } catch (err) {
      this._actionResult = `Error: ${err}`;
    }
  }

  @state() private _actionResult = '';

  private _renderDetail(entry: ReviewEntry) {
    return html`
      <div class="detail-header">PR #${entry.prNumber} — ${entry.repo}</div>
      <dl class="detail-meta">
        <dt>Author</dt><dd>${entry.contributor}</dd>
        <dt>Status</dt><dd>${entry.status}</dd>
        <dt>Lines Changed</dt><dd>${entry.linesChanged}</dd>
        <dt>Started</dt><dd>${entry.startedAt}</dd>
        <dt>Last Event</dt><dd>${entry.lastEventAt}</dd>
        <dt>Case ID</dt><dd style="font-size:11px">${entry.caseId}</dd>
      </dl>
      <div class="actions">
        <button @click=${() => this._doAction('approve', entry)}>Approve</button>
        <button class="secondary" @click=${() => this._doAction('request-changes', entry)}>Request Changes</button>
        <button class="primary" @click=${() => this._doAction('enqueue', entry)}>Add to Merge Queue</button>
      </div>
      ${this._actionResult ? html`<div class="action-result">${this._actionResult}</div>` : nothing}
      <div class="section-title">Event Timeline</div>
      ${this._eventData ? html`
        <pages-table
          .dataSet=${this._eventData}
          .columnConfig=${EVENT_TABLE_CONFIG}
        ></pages-table>
      ` : html`<div>Loading events...</div>`}
    `;
  }
}
