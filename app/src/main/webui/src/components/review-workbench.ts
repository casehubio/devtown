import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import { emitPagesEvent, onPagesEvent } from '@casehubio/blocks-ui-core';
import '@casehubio/pages-table';
import '@casehubio/blocks-ui-split-workbench';
import './review-detail.js';

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

interface SystemHealth {
  activeCases: number;
  fleetSize: number;
  openCommitments: number;
  pendingWorkItems: number;
}

interface Problem {
  category: string;
  severity: string;
  description: string;
  caseId: string | null;
}

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

@customElement('devtown-review-workbench')
export class ReviewWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _selectedCaseId = '';
  @state() private _listData: TypedDataSet | undefined;
  @state() private _entries: ReviewEntry[] = [];
  @state() private _health: SystemHealth = { activeCases: 0, fleetSize: 0, openCommitments: 0, pendingWorkItems: 0 };
  @state() private _problems: Problem[] = [];

  private _unsubs: Array<() => void> = [];

  static override styles = css`
    :host { display: block; height: 100%; font-family: var(--pages-font-family, system-ui); }
    blocks-split-workbench { height: 100%; }
    .list-panel { height: 100%; overflow: auto; padding: 0 12px; }
    .detail-panel { height: 100%; }
    .vitals {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px;
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
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchReviews();
    this._unsubs.push(
      onPagesEvent(document, 'review:deselected', () => { this._selectedCaseId = ''; }),
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

  private async _fetchReviews(): Promise<void> {
    try {
      const [qRes, hRes, pRes] = await Promise.all([
        fetch(`${this.endpoint}/queue-status`).catch(() => null),
        fetch(`${this.endpoint}/system-health`).catch(() => null),
        fetch(`${this.endpoint}/problems?threshold_minutes=0`).catch(() => null),
      ]);
      if (qRes?.ok) {
        const json = await qRes.json();
        this._entries = json.reviews ?? [];
        this._listData = fromRows([...this._entries], LIST_COLUMNS);
      }
      if (hRes?.ok) { this._health = await hRes.json(); }
      if (pRes?.ok) { this._problems = (await pRes.json()).items ?? []; }
    } catch (err) {
      console.warn('Failed to fetch reviews', err);
    }
  }

  private _handleRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (detail?.row) {
      const pr = detail.row.number(PR_COL);
      const entry = this._entries.find(r => r.prNumber === pr);
      if (entry) {
        this._selectedCaseId = entry.caseId;
        emitPagesEvent(document, 'review:selected', { caseId: entry.caseId });
      }
    }
  };

  private _handleProblemClick(p: Problem): void {
    if (p.caseId) {
      this._selectedCaseId = p.caseId;
      emitPagesEvent(document, 'review:selected', { caseId: p.caseId });
    }
  }

  override render() {
    const h = this._health;
    return html`
      <blocks-split-workbench selection-topic="review" title="Reviews">
        <div slot="list" class="list-panel">
          <h3 class="section-first">System</h3>
          <div class="vitals">
            <div class="vital"><div class="vital-value">${h.activeCases}</div><div class="vital-label">Active</div></div>
            <div class="vital"><div class="vital-value">${h.fleetSize}</div><div class="vital-label">Fleet</div></div>
            <div class="vital"><div class="vital-value">${h.openCommitments}</div><div class="vital-label">Commits</div></div>
            <div class="vital"><div class="vital-value">${h.pendingWorkItems}</div><div class="vital-label">Work Items</div></div>
          </div>

          ${this._problems.length > 0 ? html`
            <h3>Problems</h3>
            <div class="problems-banner">
              ${this._problems.map(p => html`
                <div class="problem-item" @click=${() => this._handleProblemClick(p)}>
                  <span class="problem-severity ${p.severity === 'error' ? 'error' : ''}">${p.severity}</span>
                  <span>${p.description}</span>
                </div>
              `)}
            </div>
          ` : nothing}

          <h3>Active Reviews</h3>
          ${this._listData ? html`
            <pages-table
              .dataSet=${this._listData}
              .columnConfig=${LIST_TABLE_CONFIG}
              @row-activate=${this._handleRowActivate}
            ></pages-table>
          ` : nothing}
        </div>
        <div slot="detail" class="detail-panel">
          <devtown-review-detail
            case-id=${this._selectedCaseId}
            endpoint="/api/devtown/reviews"
          ></devtown-review-detail>
        </div>
      </blocks-split-workbench>
    `;
  }
}
