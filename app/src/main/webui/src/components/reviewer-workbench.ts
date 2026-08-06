import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import '@casehubio/pages-table';
import '@casehubio/blocks-ui-trust-workbench';

interface ReviewerEntry {
  actorId: string;
  maturityPhase: string;
  openCommitments: number;
  totalDecisions: number;
  trustByCapability: Record<string, number>;
}

const ACTOR_COL = columnId('actorId');
const PHASE_COL = columnId('maturityPhase');
const COMMITMENTS_COL = columnId('openCommitments');
const DECISIONS_COL = columnId('totalDecisions');

const FLEET_COLUMNS = [
  { id: ACTOR_COL, name: 'Reviewer', type: ColumnType.TEXT, getValue: (r: ReviewerEntry) => r.actorId },
  { id: PHASE_COL, name: 'Phase', type: ColumnType.TEXT, getValue: (r: ReviewerEntry) => r.maturityPhase },
  { id: COMMITMENTS_COL, name: 'Open', type: ColumnType.NUMBER, getValue: (r: ReviewerEntry) => r.openCommitments },
  { id: DECISIONS_COL, name: 'Decisions', type: ColumnType.NUMBER, getValue: (r: ReviewerEntry) => r.totalDecisions },
];

const FLEET_TABLE_CONFIG: readonly TableColumnConfig[] = [
  { id: ACTOR_COL, sortable: true },
  { id: PHASE_COL, sortable: true },
  { id: COMMITMENTS_COL, sortable: true },
  { id: DECISIONS_COL, sortable: true },
];

@customElement('devtown-reviewer-workbench')
export class ReviewerWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _actorId = '';
  @state() private _fleetData: TypedDataSet | undefined;
  @state() private _loading = true;
  @state() private _error: string | null = null;

  static override styles = css`
    :host { display: flex; height: 100%; font-family: var(--pages-font-family, system-ui); }
    .fleet-panel {
      width: 35%; min-width: 280px;
      border-right: 1px solid var(--pages-neutral-4, #d4d4d4);
      display: flex; flex-direction: column; overflow: hidden;
    }
    .fleet-header {
      padding: 12px 16px; font-size: 14px; font-weight: 600;
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .fleet-table { flex: 1; overflow: auto; }
    .detail-panel { flex: 1; overflow: hidden; }
    .empty-detail {
      display: flex; align-items: center; justify-content: center;
      height: 100%; color: var(--pages-neutral-7, #525252); font-size: 13px;
    }
    .error { color: var(--pages-danger-9, #dc2626); padding: 16px; }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchFleet();
  }

  configure(props: Record<string, unknown>): void {
    if (props.endpoint) this.endpoint = String(props.endpoint);
  }

  private async _fetchFleet(): Promise<void> {
    this._loading = true;
    this._error = null;
    try {
      const res = await fetch(`${this.endpoint}/reviewers`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      const items: ReviewerEntry[] = json.items ?? json;
      this._fleetData = fromRows([...items], FLEET_COLUMNS);
    } catch (err) {
      this._error = err instanceof Error ? err.message : String(err);
    } finally {
      this._loading = false;
    }
  }

  private _handleRowActivate = (e: Event): void => {
    const detail = (e as CustomEvent).detail;
    if (detail?.row) {
      const id = detail.row.text(ACTOR_COL);
      if (id) this._actorId = id;
    }
  };

  override render() {
    return html`
      <div class="fleet-panel">
        <div class="fleet-header">Reviewer Fleet</div>
        <div class="fleet-table">
          ${this._error ? html`<div class="error">${this._error}</div>` :
            this._fleetData ? html`
              <pages-table
                .dataSet=${this._fleetData}
                .columnConfig=${FLEET_TABLE_CONFIG}
                @row-activate=${this._handleRowActivate}
              ></pages-table>
            ` : nothing}
        </div>
      </div>
      <div class="detail-panel">
        ${this._actorId ? html`
          <blocks-trust-workbench
            endpoint=${this.endpoint}
            actor-id=${this._actorId}
          ></blocks-trust-workbench>
        ` : html`<div class="empty-detail">Select a reviewer to view trust details</div>`}
      </div>
    `;
  }
}
