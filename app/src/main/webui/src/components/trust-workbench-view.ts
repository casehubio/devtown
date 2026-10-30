import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { onPagesEvent } from '@casehubio/pages-data';
import '@casehubio/mosaic-trust-workbench';

interface ReviewerEntry {
  actorId: string;
  maturityPhase: string;
}

@customElement('devtown-trust-workbench-view')
export class TrustWorkbenchView extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _actors: ReviewerEntry[] = [];
  @state() private _selectedActorId = '';

  private _unsubs: Array<() => void> = [];

  static override styles = css`
    :host { display: block; height: 100%; font-family: var(--pages-font-family, system-ui); }
    .container { display: flex; flex-direction: column; height: 100%; }
    .actor-bar { padding: 8px 16px; border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5); display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
    .actor-bar label { font-size: 13px; font-weight: 600; color: var(--pages-neutral-7, #525252); }
    .actor-select { font-size: 13px; padding: 4px 8px; border: 1px solid var(--pages-neutral-4, #d4d4d4); border-radius: 4px; background: var(--pages-neutral-1, #fafafa); min-width: 200px; }
    .phase-badge { font-size: 10px; padding: 2px 6px; border-radius: 8px; background: var(--pages-neutral-3, #e5e5e5); }
    .workbench-host { flex: 1; overflow: hidden; }
    .empty { padding: 40px; text-align: center; color: var(--pages-neutral-6, #737373); }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchActors();
    this._unsubs.push(
      onPagesEvent(document, 'trust:show-detail', (e: CustomEvent) => {
        const actorId = e.detail?.actorId;
        if (actorId) this._selectedActorId = actorId;
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

  private async _fetchActors(): Promise<void> {
    try {
      const res = await fetch(`${this.endpoint}/reviews/reviewers`);
      if (res.ok) {
        const data = await res.json();
        this._actors = (data.items ?? []).map((r: Record<string, unknown>) => ({
          actorId: String(r.actorId ?? ''),
          maturityPhase: String(r.maturityPhase ?? ''),
        }));
        if (this._actors.length > 0 && !this._selectedActorId) {
          this._selectedActorId = this._actors[0].actorId;
        }
      }
    } catch { /* unavailable */ }
  }

  private _onActorChange(e: Event): void {
    this._selectedActorId = (e.target as HTMLSelectElement).value;
  }

  override render() {
    const selected = this._actors.find(a => a.actorId === this._selectedActorId);

    return html`
      <div class="container">
        <div class="actor-bar">
          <label>Actor</label>
          <select class="actor-select" @change=${this._onActorChange}>
            ${this._actors.map(a => html`
              <option value=${a.actorId} ?selected=${a.actorId === this._selectedActorId}>
                ${a.actorId} (${a.maturityPhase})
              </option>
            `)}
          </select>
          ${selected ? html`<span class="phase-badge">${selected.maturityPhase}</span>` : nothing}
        </div>
        <div class="workbench-host">
          ${this._selectedActorId
            ? html`<agency-trust-workbench endpoint="${this.endpoint}" actor-id="${this._selectedActorId}"></agency-trust-workbench>`
            : html`<div class="empty">Select an actor to view trust profile</div>`
          }
        </div>
      </div>
    `;
  }
}
