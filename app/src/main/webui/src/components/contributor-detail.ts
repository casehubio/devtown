import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

interface ContributorProfile {
  actorId: string;
  globalScore: number | null;
  capabilityScores: Record<string, number>;
  dimensionScores: Record<string, number>;
  intakeClassification: {
    lane: string; trustScore: number; observationCount: number;
    classificationReason: string; fastTrackThreshold: number; standardThreshold: number;
  };
  recentOutcomes: Array<{ caseId: string; capability: string; outcome: string; timestamp: string }>;
  githubIntelligence: {
    mergedCount: number; closedCount: number; mergeRatio: number;
    tier: string; maturity: string; lastRefreshAt: string; available: boolean; summary: string;
  } | null;
}

@customElement('devtown-contributor-detail')
export class ContributorDetail extends LitElement {
  @property({ type: String }) endpoint = '';
  @property({ type: String, attribute: 'actor-id' }) actorId = '';

  @state() private _data: ContributorProfile | null = null;
  @state() private _loading = false;
  @state() private _prevActorId = '';

  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; padding: 16px; }
    .header { font-size: 18px; font-weight: 600; margin-bottom: 16px; }
    .empty { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--pages-neutral-7, #525252); font-size: 13px; }

    .section-title { font-size: 13px; font-weight: 600; margin: 20px 0 6px; color: var(--pages-neutral-9, #404040); text-transform: uppercase; letter-spacing: 0.5px; }
    .narrative { font-size: 13px; line-height: 1.6; color: var(--pages-neutral-8, #404040); margin: 6px 0 12px; }

    .badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 11px; font-weight: 600; }
    .badge.fast-track { background: var(--pages-success-3, #dcfce7); color: var(--pages-success-9, #166534); }
    .badge.standard { background: var(--pages-primary-3, #dbeafe); color: var(--pages-primary-9, #1d4ed8); }
    .badge.enhanced { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-9, #92400e); }

    .trust-bar { display: flex; align-items: center; gap: 8px; margin: 8px 0 4px; }
    .trust-track { flex: 1; height: 8px; background: var(--pages-neutral-3, #e5e5e5); border-radius: 4px; overflow: hidden; position: relative; }
    .trust-fill { height: 100%; border-radius: 4px; transition: width 0.3s; }
    .trust-fill.high { background: var(--pages-success-9, #16a34a); }
    .trust-fill.mid { background: var(--pages-primary-9, #1d4ed8); }
    .trust-fill.low { background: var(--pages-warning-9, #d97706); }
    .trust-fill.very-low { background: var(--pages-danger-9, #dc2626); }
    .trust-threshold { position: absolute; top: -2px; bottom: -2px; width: 2px; background: var(--pages-neutral-7, #525252); }
    .trust-value { font-size: 13px; font-weight: 600; min-width: 40px; }
    .threshold-legend { display: flex; gap: 12px; font-size: 11px; color: var(--pages-neutral-6, #737373); margin-top: 4px; }

    .proximity-card {
      margin: 10px 0; padding: 10px 14px; border-radius: 6px; font-size: 13px; line-height: 1.5;
      border-left: 3px solid;
    }
    .proximity-card.stable { background: var(--pages-success-2, #f0fdf4); border-color: var(--pages-success-9, #16a34a); }
    .proximity-card.watch { background: var(--pages-warning-2, #fffbeb); border-color: var(--pages-warning-9, #d97706); }
    .proximity-card.risk { background: var(--pages-danger-2, #fef2f2); border-color: var(--pages-danger-9, #dc2626); }

    .dim-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 6px; }
    .dim-item { font-size: 12px; display: flex; justify-content: space-between; padding: 4px 8px; background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px; }
    .dim-label { color: var(--pages-neutral-8, #404040); }
    .dim-val { font-weight: 600; }

    .outcome-list { margin-top: 6px; }
    .outcome-item { display: flex; gap: 12px; align-items: baseline; padding: 4px 0; font-size: 12px; border-bottom: 1px solid var(--pages-neutral-2, #f5f5f5); }
    .outcome-item:last-child { border-bottom: none; }
    .outcome-result { font-weight: 600; min-width: 60px; }
    .outcome-result.merged { color: var(--pages-success-9, #166534); }
    .outcome-result.closed { color: var(--pages-danger-9, #dc2626); }
    .outcome-date { color: var(--pages-neutral-6, #737373); }
  `;

  override willUpdate(changed: Map<PropertyKey, unknown>): void {
    if (changed.has('actorId') && this.actorId && this.actorId !== this._prevActorId) {
      this._prevActorId = this.actorId;
      this._fetch();
    }
  }

  private async _fetch(): Promise<void> {
    if (!this.actorId) return;
    this._loading = true;
    try {
      const res = await fetch(`${this.endpoint}/contributors/${this.actorId}`);
      if (res.ok) this._data = await res.json();
    } catch { /* unavailable */ }
    this._loading = false;
  }

  private _trustFillClass(score: number): string {
    if (score >= 0.80) return 'high';
    if (score >= 0.60) return 'mid';
    if (score >= 0.40) return 'low';
    return 'very-low';
  }

  private _laneBadgeClass(lane: string): string {
    const l = lane.toLowerCase();
    if (l.includes('fast')) return 'fast-track';
    if (l.includes('enhanced')) return 'enhanced';
    return 'standard';
  }

  private _buildSummary(d: ContributorProfile): string {
    const ic = d.intakeClassification;
    const gh = d.githubIntelligence;
    const total = gh ? gh.mergedCount + gh.closedCount : ic.observationCount;
    const parts: string[] = [];

    if (gh && total > 0) {
      parts.push(`${d.actorId} has submitted ${total} PRs — ${gh.mergedCount} merged, ${gh.closedCount} closed (${Math.round(gh.mergeRatio * 100)}% merge rate).`);
    } else {
      parts.push(`${d.actorId} has ${ic.observationCount} observed PRs.`);
    }

    const mr = d.dimensionScores['merge-rate'];
    const faq = d.dimensionScores['first-attempt-quality'];
    if (mr != null && faq != null) {
      const mrPct = Math.round(mr * 100);
      const faPct = Math.round(faq * 100);
      if (mrPct >= 90 && faPct >= 80) {
        parts.push(`Strong contributor — ${mrPct}% merge rate with ${faPct}% passing on first attempt.`);
      } else if (mrPct >= 70) {
        parts.push(`Merge rate is ${mrPct}%, but only ${faPct}% pass on first attempt — some PRs need revision.`);
      } else {
        parts.push(`Merge rate (${mrPct}%) and first-attempt quality (${faPct}%) suggest PRs frequently need rework.`);
      }
    }

    return parts.join(' ');
  }

  private _buildProximity(d: ContributorProfile): { text: string; level: 'stable' | 'watch' | 'risk' } {
    const ic = d.intakeClassification;
    const score = ic.trustScore;
    const ft = ic.fastTrackThreshold;
    const std = ic.standardThreshold;
    const lane = ic.lane.toLowerCase();
    const scorePct = Math.round(score * 100);
    const ftPct = Math.round(ft * 100);
    const stdPct = Math.round(std * 100);

    if (lane.includes('fast')) {
      const margin = Math.round((score - ft) * 100);
      if (margin > 10) {
        return { text: `Comfortably in fast-track — ${margin} points above the ${ftPct}% threshold. No action needed.`, level: 'stable' };
      }
      return { text: `In fast-track but only ${margin} points above the ${ftPct}% threshold. A few rejected PRs could demote to standard review.`, level: 'watch' };
    }
    if (lane.includes('enhanced')) {
      const gap = Math.round((std - score) * 100);
      if (gap > 15) {
        return { text: `${gap} points below the standard threshold (${stdPct}%). Needs a sustained run of clean merges to move up.`, level: 'risk' };
      }
      return { text: `${gap} points below standard threshold (${stdPct}%). Close to promotion — ${Math.ceil(gap / 5)} more clean merges could move this contributor to standard lane.`, level: 'watch' };
    }
    // Standard lane
    const toFt = Math.round((ft - score) * 100);
    const aboveStd = Math.round((score - std) * 100);
    if (toFt <= 10) {
      return { text: `${toFt} points below fast-track (${ftPct}%). Close to promotion — a few more clean merges could earn fast-track status.`, level: 'watch' };
    }
    if (aboveStd <= 10) {
      return { text: `Only ${aboveStd} points above the enhanced-review threshold (${stdPct}%). At risk of demotion if quality drops.`, level: 'watch' };
    }
    return { text: `Solidly in standard lane — ${scorePct}% trust, ${toFt} points from fast-track, ${aboveStd} points above enhanced-review.`, level: 'stable' };
  }

  private _buildTrend(outcomes: Array<{ outcome: string }>): string {
    if (outcomes.length === 0) return 'No recent activity to assess trend.';
    const recent = outcomes.slice(0, 5);
    const merged = recent.filter(o => o.outcome === 'MERGED').length;
    const total = recent.length;

    if (merged === total) return `Last ${total} PRs all merged — consistent quality, trust score likely stable or rising.`;
    if (merged === 0) return `Last ${total} PRs all closed/rejected — trust score is declining. Consider whether this contributor needs support.`;
    const rate = Math.round((merged / total) * 100);
    return `Last ${total} PRs: ${merged} merged, ${total - merged} closed (${rate}% success). Mixed recent record — trust score may be shifting.`;
  }

  override render() {
    if (!this.actorId) return html`<div class="empty">Select a contributor to view details</div>`;
    if (this._loading && !this._data) return html`<div class="empty">Loading...</div>`;
    if (!this._data) return html`<div class="empty">No data for ${this.actorId}</div>`;

    const d = this._data;
    const ic = d.intakeClassification;
    const trustPct = Math.round(ic.trustScore * 100);
    const proximity = this._buildProximity(d);

    return html`
      <div class="header">${d.actorId} <span class="badge ${this._laneBadgeClass(ic.lane)}">${ic.lane}</span></div>

      <div class="narrative">${this._buildSummary(d)}</div>

      <div class="section-title">Lane Position</div>
      <div class="trust-bar">
        <div class="trust-track">
          <div class="trust-fill ${this._trustFillClass(ic.trustScore)}" style="width:${trustPct}%"></div>
          <div class="trust-threshold" style="left:${Math.round(ic.fastTrackThreshold * 100)}%" title="Fast-track (${Math.round(ic.fastTrackThreshold * 100)}%)"></div>
          <div class="trust-threshold" style="left:${Math.round(ic.standardThreshold * 100)}%" title="Standard (${Math.round(ic.standardThreshold * 100)}%)"></div>
        </div>
        <div class="trust-value">${trustPct}%</div>
      </div>
      <div class="threshold-legend">
        <span>▮ ${Math.round(ic.standardThreshold * 100)}% standard</span>
        <span>▮ ${Math.round(ic.fastTrackThreshold * 100)}% fast-track</span>
        <span>${ic.observationCount} observations</span>
      </div>

      <div class="proximity-card ${proximity.level}">${proximity.text}</div>

      ${Object.keys(d.dimensionScores).length > 0 ? html`
        <div class="section-title">Quality Dimensions</div>
        <div class="dim-grid">
          ${Object.entries(d.dimensionScores).map(([k, v]) => html`
            <div class="dim-item">
              <span class="dim-label">${k.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}</span>
              <span class="dim-val">${typeof v === 'number' ? Math.round(v * 100) + '%' : '—'}</span>
            </div>
          `)}
        </div>
      ` : nothing}

      ${d.recentOutcomes.length > 0 ? html`
        <div class="section-title">Trend</div>
        <div class="narrative">${this._buildTrend(d.recentOutcomes)}</div>
        <div class="outcome-list">
          ${d.recentOutcomes.slice(0, 5).map(o => html`
            <div class="outcome-item">
              <span class="outcome-result ${o.outcome === 'MERGED' ? 'merged' : 'closed'}">${o.outcome}</span>
              <span class="outcome-date">${new Date(o.timestamp).toLocaleDateString()}</span>
            </div>
          `)}
        </div>
      ` : html`
        <div class="section-title">Trend</div>
        <div class="narrative">No recent PR outcomes recorded yet.</div>
      `}
    `;
  }
}
