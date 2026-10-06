import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

interface RoutingDecision {
  capability: string;
  reason: string;
  confidence: number;
  bindingName: string;
}

interface RoutingSummary {
  decisions: RoutingDecision[];
  featureVector: string | null;
}

@customElement('devtown-routing-summary')
export class RoutingSummaryComponent extends LitElement {
  @property({ attribute: false }) routing: RoutingSummary | null = null;

  @state() private _showFeatures = false;

  static override styles = css`
    :host { display: block; margin-bottom: 16px; }
    .header { font-size: 13px; font-weight: 600; margin-bottom: 8px; color: var(--pages-neutral-11, #171717); }
    .decisions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
    .badge {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500;
      background: var(--pages-accent-3, #dbeafe); color: var(--pages-accent-11, #1e3a5f);
      border: 1px solid var(--pages-accent-5, #93c5fd);
    }
    .confidence {
      width: 40px; height: 4px; border-radius: 2px;
      background: var(--pages-neutral-4, #d4d4d4); overflow: hidden;
    }
    .confidence-fill { height: 100%; background: var(--pages-accent-9, #2563eb); border-radius: 2px; }
    .reason { font-size: 11px; color: var(--pages-neutral-8, #525252); margin-top: 2px; }
    .feature-toggle {
      font-size: 11px; color: var(--pages-accent-9, #2563eb); cursor: pointer;
      border: none; background: none; padding: 4px 0;
    }
    .feature-vector {
      font-size: 11px; padding: 8px; margin-top: 4px;
      background: var(--pages-neutral-2, #f5f5f5); border-radius: 4px;
    }
    .fv-row { display: flex; gap: 8px; margin: 2px 0; }
    .fv-key { font-weight: 600; min-width: 140px; }
  `;

  override render() {
    if (!this.routing || this.routing.decisions.length === 0) return nothing;

    return html`
      <div class="header">Routing Decisions</div>
      <div class="decisions">
        ${this.routing.decisions.map(d => html`
          <div class="badge">
            <span>${d.capability}</span>
            <div class="confidence">
              <div class="confidence-fill" style="width:${Math.round(d.confidence * 100)}%"></div>
            </div>
          </div>
        `)}
      </div>
      ${this.routing.decisions.map(d => d.reason
        ? html`<div class="reason">${d.capability}: ${d.reason}</div>`
        : nothing)}
      ${this.routing.featureVector ? html`
        <button class="feature-toggle" @click=${this._toggleFeatures}>
          ${this._showFeatures ? '▾ Hide' : '▸ Show'} code analysis
        </button>
        ${this._showFeatures ? html`
          <div class="feature-vector">
            <pre style="margin:0;white-space:pre-wrap;font-size:11px">${this.routing.featureVector}</pre>
          </div>
        ` : nothing}
      ` : nothing}
    `;
  }

  private _toggleFeatures = () => {
    this._showFeatures = !this._showFeatures;
  };
}
