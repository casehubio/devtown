import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { reviewTimelineStrategy } from './review-timeline-strategy.js';
import { renderTrustBar } from './shared/trust-bar.js';
import { renderDimensionGrid } from './shared/dimension-grid.js';
import { renderReviewCard } from './shared/review-card.js';
import { renderBadge } from './shared/badge.js';
import type { DimensionEntry } from './shared/types.js';
import type { ReviewOutcome } from './shared/review-card.js';
import { hostStyles, sectionTitleStyles, narrativeStyles, emptyStyles, trustBarStyles, dimensionGridStyles, reviewCardStyles, badgeStyles } from './shared/shared-styles.js';
import './routing-summary.js';
import './findings-panel.js';
import '@casehubio/blocks-ui-blocks-timeline';

interface ContributorProfile {
  actorId: string;
  globalScore: number | null;
  intakeClassification: { lane: string; trustScore: number; observationCount: number; classificationReason: string; fastTrackThreshold: number; standardThreshold: number };
  dimensionScores: Record<string, number>;
  recentOutcomes: Array<{ outcome: string; timestamp: string }>;
  githubIntelligence: { mergedCount: number; closedCount: number; mergeRatio: number; tier: string; maturity: string; summary: string } | null;
}

interface ReviewDetailData {
  caseId: string;
  pr: { repo: string; prNumber: number; contributor: string; linesChanged: number; headSha: string };
  timeline: Array<{ timestamp: string; category: string; eventType: string; actor: string; summary: string; metadata: unknown }>;
  capabilities: Array<{ name: string; status: string; outcome: string | null; completedAt: string }>;
  routing: { decisions: Array<{ capability: string; reason: string; confidence: number; bindingName: string }>; featureVector: string | null };
  findings: Record<string, Array<{ severity: string; category: string; filePath: string; message: string; confidence: number; startLine: number | null; endLine: number | null }>>;
}

@customElement('devtown-review-detail')
export class ReviewDetail extends LitElement {
  @property({ type: String, attribute: 'case-id' }) caseId = '';
  @property({ type: String }) endpoint = '';

  @state() private _data: ReviewDetailData | null = null;
  @state() private _loading = false;
  @state() private _error = '';
  @state() private _actionResult = '';
  @state() private _contributor: ContributorProfile | null = null;
  @state() private _prevContributor = '';

  static override styles = [
    hostStyles, sectionTitleStyles, narrativeStyles, emptyStyles,
    trustBarStyles, dimensionGridStyles, reviewCardStyles, badgeStyles,
    css`
      .meta { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; margin-bottom: 16px; font-size: 13px; }
      .meta dt { font-weight: 600; color: var(--pages-neutral-8, #404040); }
      .meta dd { margin: 0; }
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
      .action-result { font-size: 12px; padding: 6px 10px; margin: 4px 0 8px; background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px; }
      .error { color: var(--pages-danger-9, #dc2626); padding: 16px; }
      .check-list { list-style: none; padding: 0; margin: 6px 0 0 0; }
      .check-item { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 12px; }
      .check-icon { width: 16px; text-align: center; font-size: 14px; }
      .check-name { flex: 1; }
      .check-status { font-size: 11px; font-weight: 600; }
    `,
  ];

  override willUpdate(changed: Map<PropertyKey, unknown>): void {
    if (changed.has('caseId') && this.caseId) {
      this._fetchDetail();
    }
  }

  private async _fetchDetail(): Promise<void> {
    if (!this.caseId) return;
    this._loading = true;
    this._error = '';
    try {
      const res = await fetch(`${this.endpoint}/${this.caseId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this._data = await res.json();
      if (this._data && this._data.pr.contributor !== this._prevContributor) {
        this._prevContributor = this._data.pr.contributor;
        this._fetchContributor(this._data.pr.contributor);
      }
    } catch (err) {
      this._error = `Failed to load: ${err}`;
    } finally {
      this._loading = false;
    }
  }

  private async _fetchContributor(actorId: string): Promise<void> {
    this._contributor = null;
    try {
      const res = await fetch(`${this.endpoint}/contributors/${actorId}`);
      if (res.ok) this._contributor = await res.json();
    } catch { /* contributor data optional */ }
  }

  private _checkIcon(status: string): string {
    if (status === 'COMPLETED') return '✅';
    if (status === 'FAILED' || status === 'DECLINED') return '❌';
    if (status === 'SCHEDULED' || status === 'IN_PROGRESS') return '⏳';
    return '⭕';
  }

  private _buildTrustNarrative(contributor: string, c: ContributorProfile): string {
    const ic = c.intakeClassification;
    const gh = c.githubIntelligence;
    const dims = c.dimensionScores;
    const totalPrs = gh ? gh.mergedCount + gh.closedCount : ic.observationCount;
    const parts: string[] = [];

    if (gh && totalPrs > 0) {
      parts.push(`${contributor} has submitted ${totalPrs} PRs — ${gh.mergedCount} merged, ${gh.closedCount} closed (${Math.round(gh.mergeRatio * 100)}% merge rate).`);
    } else {
      parts.push(`${contributor} has ${ic.observationCount} observed PRs.`);
    }

    const lane = ic.lane.toLowerCase();
    const trustPct = Math.round(ic.trustScore * 100);
    const ftPct = Math.round(ic.fastTrackThreshold * 100);
    const stdPct = Math.round(ic.standardThreshold * 100);
    if (lane.includes('fast')) {
      parts.push(`Trust score ${trustPct}% exceeds the fast-track threshold (${ftPct}%), so this PR skips enhanced scrutiny.`);
    } else if (lane.includes('enhanced')) {
      parts.push(`Trust score ${trustPct}% is below the standard threshold (${stdPct}%), so this PR gets additional review scrutiny.`);
    } else {
      parts.push(`Trust score ${trustPct}% is between standard (${stdPct}%) and fast-track (${ftPct}%) thresholds — standard review applies.`);
    }

    const mergeRate = dims['merge-rate'];
    const faq = dims['first-attempt-quality'];
    if (mergeRate != null && faq != null) {
      const mr = Math.round(mergeRate * 100);
      const fa = Math.round(faq * 100);
      if (mr >= 90 && fa >= 80) {
        parts.push(`Strong track record: ${mr}% of PRs merge successfully and ${fa}% pass on first attempt.`);
      } else if (mr >= 70) {
        parts.push(`Merge rate is ${mr}%, but first-attempt quality is ${fa}% — some PRs need revision cycles.`);
      } else {
        parts.push(`Merge rate (${mr}%) and first-attempt quality (${fa}%) indicate this contributor's PRs frequently need rework.`);
      }
    }

    if (ic.observationCount < 10) {
      parts.push(`Only ${ic.observationCount} observations — trust score may shift significantly as more data accumulates.`);
    }

    return parts.join(' ');
  }

  private _buildRecentPattern(outcomes: Array<{ outcome: string }>): string | null {
    if (outcomes.length === 0) return null;
    const recent = outcomes.slice(0, 5);
    const merged = recent.filter(o => o.outcome === 'MERGED').length;
    if (merged === recent.length) return `Last ${recent.length} PRs all merged successfully.`;
    const closed = recent.length - merged;
    return `Last ${recent.length} PRs: ${merged} merged, ${closed} closed/rejected.`;
  }

  private async _doAction(action: string): Promise<void> {
    if (!this._data) return;
    try {
      const res = await fetch(`/api/actions/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          repo: this._data.pr.repo,
          prNumber: this._data.pr.prNumber,
          contributor: this._data.pr.contributor,
          headSha: this._data.pr.headSha,
        }),
      });
      const json = await res.json();
      this._actionResult = `${json.action}: ${json.result}`;
      this._fetchDetail();
    } catch (err) {
      this._actionResult = `Error: ${err}`;
    }
  }

  override render() {
    if (!this.caseId) return html`<div class="empty">Select a review to see details</div>`;
    if (this._loading && !this._data) return html`<div class="empty">Loading...</div>`;
    if (this._error) return html`<div class="error">${this._error}</div>`;
    if (!this._data) return html`<div class="empty">No data</div>`;

    const d = this._data;
    const c = this._contributor;
    const completedCaps = d.capabilities.filter(cap => cap.status === 'COMPLETED');
    const pendingCaps = d.capabilities.filter(cap => cap.status !== 'COMPLETED');

    return html`
      <div class="header">PR #${d.pr.prNumber} — ${d.pr.repo}</div>
      <dl class="meta">
        <dt>Author</dt><dd>${d.pr.contributor}</dd>
        <dt>Lines Changed</dt><dd>${d.pr.linesChanged}</dd>
        <dt>Case ID</dt><dd style="font-size:11px">${d.caseId}</dd>
      </dl>

      ${c ? html`
        <div class="section-title">Contributor Trust</div>
        <div style="margin-bottom:6px">
          ${renderBadge(c.intakeClassification.lane, c.intakeClassification.lane === 'FAST_TRACK' ? 'fast-track' : c.intakeClassification.lane === 'ENHANCED_REVIEW' ? 'enhanced' : 'standard')}
        </div>
        <div class="narrative">${this._buildTrustNarrative(d.pr.contributor, c)}</div>
        ${renderTrustBar({
          score: c.intakeClassification.trustScore,
          thresholds: [
            { value: c.intakeClassification.standardThreshold, label: 'standard' },
            { value: c.intakeClassification.fastTrackThreshold, label: 'fast-track' },
          ],
          observationCount: c.intakeClassification.observationCount,
        })}
        ${Object.keys(c.dimensionScores).length > 0 ? renderDimensionGrid(
          Object.entries(c.dimensionScores).map(([key, value]): DimensionEntry => ({ key, value: value as number }))
        ) : nothing}
        ${c.recentOutcomes.length > 0 ? html`
          <div class="section-title">Recent Activity</div>
          <div class="narrative">${this._buildRecentPattern(c.recentOutcomes)}</div>
        ` : nothing}
      ` : nothing}

      <devtown-routing-summary .routing=${d.routing}></devtown-routing-summary>

      ${pendingCaps.length > 0 ? html`
        <div class="section-title">Checks In Progress</div>
        <ul class="check-list">
          ${pendingCaps.map(cap => html`
            <li class="check-item">
              <span class="check-icon">${this._checkIcon(cap.status)}</span>
              <span class="check-name">${cap.name}</span>
              <span class="check-status">${cap.status}</span>
            </li>
          `)}
        </ul>
      ` : nothing}

      ${completedCaps.length > 0 ? html`
        <div class="section-title">Completed Reviews</div>
        ${completedCaps.map(cap => {
          const capFindings = d.findings[cap.name] ?? [];
          return renderReviewCard({
            caseId: d.caseId,
            capability: cap.name,
            outcome: cap.outcome === 'APPROVED' ? 'DONE' : 'DECLINED',
            timestamp: cap.completedAt,
            findingCount: capFindings.length,
            findingSummary: capFindings.map(f => f.message).join('; ') || null,
            feedbackOutcome: null,
          } satisfies ReviewOutcome);
        })}
      ` : nothing}

      <devtown-findings-panel .findings=${d.findings}></devtown-findings-panel>

      <div class="actions">
        <button @click=${() => this._doAction('approve')}>Approve</button>
        <button @click=${() => this._doAction('request-changes')}>Request Changes</button>
        <button class="primary" @click=${() => this._doAction('enqueue')}>Add to Merge Queue</button>
      </div>
      ${this._actionResult ? html`<div class="action-result">${this._actionResult}</div>` : nothing}

      <div class="section-title">Event Timeline</div>
      <blocks-timeline
        .strategy=${reviewTimelineStrategy}
        .data=${d.timeline}
        layout="vertical"
      ></blocks-timeline>
    `;
  }
}
