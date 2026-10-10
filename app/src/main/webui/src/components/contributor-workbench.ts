import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { columnId, ColumnType } from '@casehubio/pages-data/dist/dataset/types.js';
import type { TypedDataSet } from '@casehubio/pages-data/dist/dataset/types.js';
import { fromRows } from '@casehubio/pages-data/dist/dataset/conversion.js';
import type { TableColumnConfig } from '@casehubio/pages-table';
import '@casehubio/pages-table';
import './contributor-detail.js';

interface ContributorEntry {
  actorId: string;
  trustScore: number | null;
  intakeLane: string;
  observationCount: number;
  mergeRate: number | null;
  firstAttemptQuality: number | null;
}

const ACTOR_COL = columnId('actorId');
const TRUST_COL = columnId('trustScore');
const LANE_COL = columnId('intakeLane');
const OBS_COL = columnId('observationCount');
const MERGE_COL = columnId('mergeRate');

const FLEET_COLUMNS = [
  { id: ACTOR_COL, name: 'Contributor', type: ColumnType.TEXT, getValue: (r: ContributorEntry) => r.actorId },
  { id: TRUST_COL, name: 'Trust', type: ColumnType.NUMBER, getValue: (r: ContributorEntry) => r.trustScore ?? 0 },
  { id: LANE_COL, name: 'Lane', type: ColumnType.TEXT, getValue: (r: ContributorEntry) => r.intakeLane },
  { id: OBS_COL, name: 'Obs', type: ColumnType.NUMBER, getValue: (r: ContributorEntry) => r.observationCount },
  { id: MERGE_COL, name: 'Merge Rate', type: ColumnType.NUMBER, getValue: (r: ContributorEntry) => r.mergeRate ?? 0 },
];

const FLEET_TABLE_CONFIG: readonly TableColumnConfig[] = [
  { id: ACTOR_COL, sortable: true },
  { id: TRUST_COL, sortable: true },
  { id: LANE_COL, sortable: true },
  { id: OBS_COL, sortable: true },
  { id: MERGE_COL, sortable: true },
];

@customElement('devtown-contributor-workbench')
export class ContributorWorkbench extends LitElement {
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
      const res = await fetch(`${this.endpoint}/contributors`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      const items: ContributorEntry[] = json.items ?? json;
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
        <div class="fleet-header">Contributor Fleet</div>
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
          <devtown-contributor-detail
            endpoint=${this.endpoint}
            actor-id=${this._actorId}
          ></devtown-contributor-detail>
        ` : html`<div class="empty-detail">Select a contributor to view details</div>`}
      </div>
    `;
  }
}
