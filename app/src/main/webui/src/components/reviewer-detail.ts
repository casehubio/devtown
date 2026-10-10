import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

interface ReviewOutcome {
  caseId: string;
  capability: string;
  outcome: string;
  timestamp: string;
  pr?: { repo: string; prNumber: number; contributor: string };
  findingCount?: number;
  findingSummary?: string | null;
  feedbackOutcome?: string | null;
  declineReason?: string | null;
}

interface ReviewerHealth {
  reviewerId: string;
  openCommitments: number;
  trustByCapability: Record<string, number>;
  trustByDimension: Record<string, number>;
  totalDecisions: number;
  recentOutcomes: ReviewOutcome[];
}

@customElement('devtown-reviewer-detail')
export class ReviewerDetail extends LitElement {
  @property({ type: String }) endpoint = '';
  @property({ type: String, attribute: 'actor-id' }) actorId = '';
  @property({ type: String, attribute: 'maturity-phase' }) maturityPhase = '';

  @state() private _data: ReviewerHealth | null = null;
  @state() private _loading = false;
  @state() private _prevActorId = '';

  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; padding: 16px; }
    .header { font-size: 18px; font-weight: 600; margin-bottom: 4px; }
    .sub-header { font-size: 12px; color: var(--pages-neutral-7, #525252); margin-bottom: 16px; }
    .empty { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--pages-neutral-7, #525252); font-size: 13px; }

    .section-title { font-size: 13px; font-weight: 600; margin: 20px 0 6px; color: var(--pages-neutral-9, #404040); text-transform: uppercase; letter-spacing: 0.5px; }
    .narrative { font-size: 13px; line-height: 1.6; color: var(--pages-neutral-8, #404040); margin: 6px 0 12px; }

    .badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 11px; font-weight: 600; }
    .badge.active { background: var(--pages-success-3, #dcfce7); color: var(--pages-success-9, #166534); }
    .badge.emerging { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-9, #92400e); }
    .badge.bootstrap { background: var(--pages-neutral-3, #e5e5e5); color: var(--pages-neutral-8, #404040); }

    .cap-list { margin: 6px 0; }
    .cap-item { display: flex; align-items: center; gap: 10px; padding: 6px 0; border-bottom: 1px solid var(--pages-neutral-2, #f5f5f5); }
    .cap-item:last-child { border-bottom: none; }
    .cap-name { flex: 1; font-size: 13px; }
    .cap-bar { width: 120px; height: 6px; background: var(--pages-neutral-3, #e5e5e5); border-radius: 3px; overflow: hidden; }
    .cap-fill { height: 100%; border-radius: 3px; }
    .cap-fill.high { background: var(--pages-success-9, #16a34a); }
    .cap-fill.mid { background: var(--pages-primary-9, #1d4ed8); }
    .cap-fill.low { background: var(--pages-warning-9, #d97706); }
    .cap-score { font-size: 13px; font-weight: 600; min-width: 36px; text-align: right; }

    .dim-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px; margin-top: 6px; }
    .dim-item { font-size: 12px; padding: 6px 8px; background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px; text-align: center; }
    .dim-label { color: var(--pages-neutral-7, #525252); display: block; font-size: 10px; text-transform: uppercase; margin-bottom: 2px; }
    .dim-val { font-weight: 600; font-size: 16px; }

    .status-card {
      margin: 10px 0; padding: 10px 14px; border-radius: 6px; font-size: 13px; line-height: 1.5;
      border-left: 3px solid;
    }
    .status-card.healthy { background: var(--pages-success-2, #f0fdf4); border-color: var(--pages-success-9, #16a34a); }
    .status-card.busy { background: var(--pages-warning-2, #fffbeb); border-color: var(--pages-warning-9, #d97706); }
    .status-card.concern { background: var(--pages-danger-2, #fef2f2); border-color: var(--pages-danger-9, #dc2626); }

    .review-card {
      margin: 8px 0; padding: 10px 14px; border-radius: 6px;
      border: 1px solid var(--pages-neutral-3, #e5e5e5);
      font-size: 12px; line-height: 1.5;
    }
    .review-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .review-pr { font-weight: 600; font-size: 13px; }
    .review-meta { color: var(--pages-neutral-6, #737373); font-size: 11px; }
    .review-capability { color: var(--pages-neutral-7, #525252); }
    .review-findings { margin-top: 6px; color: var(--pages-neutral-8, #404040); }
    .review-feedback { margin-top: 4px; font-size: 11px; }
    .feedback-badge { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 10px; font-weight: 600; }
    .feedback-badge.accepted { background: var(--pages-success-3, #dcfce7); color: var(--pages-success-9, #166534); }
    .feedback-badge.rejected { background: var(--pages-danger-3, #fee2e2); color: var(--pages-danger-9, #dc2626); }
    .feedback-badge.partial { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-9, #92400e); }
    .decline-reason { margin-top: 4px; font-style: italic; color: var(--pages-neutral-7, #525252); }
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
    this._data = null;
    try {
      const res = await fetch(`${this.endpoint}/reviewers/${this.actorId}`);
      if (res.ok) this._data = await res.json();
    } catch { /* unavailable */ }
    this._loading = false;
  }

  private _fillClass(score: number): string {
    if (score >= 0.80) return 'high';
    if (score >= 0.60) return 'mid';
    return 'low';
  }

  private _phaseBadgeClass(): string {
    const p = this.maturityPhase.toLowerCase();
    if (p === 'active') return 'active';
    if (p === 'emerging') return 'emerging';
    return 'bootstrap';
  }

  private _buildNarrative(d: ReviewerHealth): string {
    const caps = Object.keys(d.trustByCapability);
    const capList = caps.map(c => c.replace(/-/g, ' ')).join(', ');
    const topCap = caps.reduce((a, b) => (d.trustByCapability[a] ?? 0) >= (d.trustByCapability[b] ?? 0) ? a : b, caps[0]);
    const topScore = Math.round((d.trustByCapability[topCap] ?? 0) * 100);

    const parts: string[] = [];
    parts.push(`${d.reviewerId} is qualified for ${caps.length} ${caps.length === 1 ? 'capability' : 'capabilities'}: ${capList}.`);

    if (this.maturityPhase === 'Bootstrap') {
      parts.push(`In bootstrap phase with only ${d.totalDecisions} decisions — trust scores are provisional and will stabilise as more reviews complete.`);
    } else if (this.maturityPhase === 'Emerging') {
      parts.push(`Emerging reviewer with ${d.totalDecisions} decisions. Strongest capability is ${topCap.replace(/-/g, ' ')} at ${topScore}%.`);
    } else {
      parts.push(`Active reviewer with ${d.totalDecisions} decisions across ${caps.length === 1 ? 'its capability' : 'all capabilities'}. Strongest at ${topCap.replace(/-/g, ' ')} (${topScore}%).`);
    }

    return parts.join(' ');
  }

  private _buildStatus(d: ReviewerHealth): { text: string; level: 'healthy' | 'busy' | 'concern' } {
    const hasLowTrust = Object.values(d.trustByCapability).some(v => v < 0.50);
    const recentDeclines = d.recentOutcomes.filter(o => o.outcome === 'DECLINED').length;

    if (d.openCommitments >= 3) {
      return { text: `${d.openCommitments} open commitments — this reviewer is heavily loaded. New assignments should prefer other agents.`, level: 'busy' };
    }
    if (hasLowTrust) {
      const lowCaps = Object.entries(d.trustByCapability).filter(([, v]) => v < 0.50).map(([k]) => k.replace(/-/g, ' '));
      return { text: `Trust score below 50% for ${lowCaps.join(', ')}. Routing should prefer higher-trust agents for these capabilities.`, level: 'concern' };
    }
    if (recentDeclines > 0) {
      return { text: `${recentDeclines} recent DECLINED outcome${recentDeclines > 1 ? 's' : ''} — this reviewer is turning down work. May indicate overload or capability mismatch.`, level: 'busy' };
    }
    if (d.openCommitments === 0) {
      return { text: `No open commitments — available for new assignments.`, level: 'healthy' };
    }
    return { text: `${d.openCommitments} open commitment${d.openCommitments > 1 ? 's' : ''}, trust scores healthy. Operating normally.`, level: 'healthy' };
  }

  private _feedbackClass(fb: string): string {
    const lower = fb.toLowerCase();
    if (lower.includes('accept')) return 'accepted';
    if (lower.includes('reject')) return 'rejected';
    return 'partial';
  }

  private _feedbackLabel(fb: string): string {
    if (fb === 'ACCEPTED') return 'Findings accepted';
    if (fb === 'REJECTED') return 'Findings rejected (false positives)';
    if (fb === 'PARTIALLY_ACCEPTED') return 'Some findings accepted';
    return fb;
  }

  private _renderReviewCard(o: ReviewOutcome) {
    const pr = o.pr;
    return html`
      <div class="review-card">
        <div class="review-card-header">
          <span class="review-pr">${pr ? `PR #${pr.prNumber} — ${pr.repo}` : o.caseId.substring(0, 8)}</span>
          <span class="review-meta">${new Date(o.timestamp).toLocaleDateString()}</span>
        </div>
        ${pr ? html`<div class="review-capability">${o.capability.replace(/-/g, ' ')} · by ${pr.contributor}</div>` : nothing}
        ${o.outcome === 'DECLINED' && o.declineReason ? html`
          <div class="decline-reason">Declined: ${o.declineReason}</div>
        ` : nothing}
        ${o.findingCount != null && o.findingCount > 0 ? html`
          <div class="review-findings">${o.findingCount} finding${o.findingCount > 1 ? 's' : ''}: ${o.findingSummary}</div>
        ` : o.outcome === 'DONE' ? html`
          <div class="review-findings" style="color:var(--pages-neutral-6,#737373)">No findings — clean review</div>
        ` : nothing}
        ${o.feedbackOutcome ? html`
          <div class="review-feedback"><span class="feedback-badge ${this._feedbackClass(o.feedbackOutcome)}">${this._feedbackLabel(o.feedbackOutcome)}</span></div>
        ` : nothing}
      </div>
    `;
  }

  override render() {
    if (!this.actorId) return html`<div class="empty">Select a reviewer to view details</div>`;
    if (this._loading && !this._data) return html`<div class="empty">Loading...</div>`;
    if (!this._data) return html`<div class="empty">No data for ${this.actorId}</div>`;

    const d = this._data;
    const status = this._buildStatus(d);

    return html`
      <div class="header">${d.reviewerId} <span class="badge ${this._phaseBadgeClass()}">${this.maturityPhase}</span></div>
      <div class="sub-header">${d.totalDecisions} decisions · ${d.openCommitments} open commitments</div>

      <div class="narrative">${this._buildNarrative(d)}</div>

      <div class="status-card ${status.level}">${status.text}</div>

      <div class="section-title">Capability Trust</div>
      <div class="cap-list">
        ${Object.entries(d.trustByCapability).map(([cap, score]) => html`
          <div class="cap-item">
            <span class="cap-name">${cap.replace(/-/g, ' ')}</span>
            <div class="cap-bar"><div class="cap-fill ${this._fillClass(score)}" style="width:${Math.round(score * 100)}%"></div></div>
            <span class="cap-score">${Math.round(score * 100)}%</span>
          </div>
        `)}
      </div>

      ${Object.keys(d.trustByDimension).length > 0 ? html`
        <div class="section-title">Quality Dimensions</div>
        <div class="dim-grid">
          ${Object.entries(d.trustByDimension).map(([dim, score]) => html`
            <div class="dim-item">
              <span class="dim-label">${dim.replace(/-/g, ' ')}</span>
              <span class="dim-val">${Math.round(score * 100)}%</span>
            </div>
          `)}
        </div>
      ` : nothing}

      <div class="section-title">Review History</div>
      ${d.recentOutcomes.length > 0 ? d.recentOutcomes.map(o => this._renderReviewCard(o)) : html`
        <div class="narrative">No recent review outcomes recorded.</div>
      `}
    `;
  }
}
