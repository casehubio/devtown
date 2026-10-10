import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

interface QueuedPrData {
  number: number;
  repository: string;
  author: string;
  headSha: string;
  priorityLane: string;
  trustScore: number;
  waitMinutes: number;
  dependsOn: number[];
}

interface BatchData {
  batchId: string;
  caseId: string;
  prCount: number;
  riskLevel: string;
  ciStatus?: string;
  prNumbers?: number[];
  startedAt?: string;
  suspectedPr?: number;
}

interface ContributorProfile {
  actorId: string;
  globalScore: number | null;
  intakeClassification: { lane: string; trustScore: number; observationCount: number; classificationReason: string; fastTrackThreshold: number; standardThreshold: number };
  dimensionScores: Record<string, number>;
  recentOutcomes: Array<{ outcome: string; timestamp: string }>;
  githubIntelligence: { mergedCount: number; closedCount: number; mergeRatio: number; tier: string; maturity: string; summary: string } | null;
}

interface ReviewStatus {
  caseId: string;
  status: string;
  capabilities: Array<{ name: string; status: string; outcome: string | null }>;
}

@customElement('devtown-merge-queue-detail')
export class MergeQueueDetail extends LitElement {
  @property({ type: String }) endpoint = '';
  @property({ type: Object }) queuedPr: QueuedPrData | null = null;
  @property({ type: Object }) batch: BatchData | null = null;
  @property({ type: Array }) reviews: ReviewStatus[] = [];

  @state() private _contributor: ContributorProfile | null = null;
  @state() private _actionResult = '';
  @state() private _prevAuthor = '';

  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; padding: 16px; }
    .header { font-size: 18px; font-weight: 600; margin-bottom: 4px; }
    .sub-header { font-size: 12px; color: var(--pages-neutral-7, #525252); margin-bottom: 16px; }
    .meta { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; margin-bottom: 16px; font-size: 13px; }
    .meta dt { font-weight: 600; color: var(--pages-neutral-8, #404040); }
    .meta dd { margin: 0; }
    .badge {
      display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 11px; font-weight: 600;
    }
    .badge.fast-track { background: var(--pages-success-3, #dcfce7); color: var(--pages-success-9, #166534); }
    .badge.standard { background: var(--pages-primary-3, #dbeafe); color: var(--pages-primary-9, #1d4ed8); }
    .badge.enhanced { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-9, #92400e); }
    .badge.routine { background: var(--pages-neutral-3, #e5e5e5); color: var(--pages-neutral-9, #171717); }
    .badge.elevated { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-9, #92400e); }
    .section-title { font-size: 13px; font-weight: 600; margin: 16px 0 6px; color: var(--pages-neutral-9, #404040); text-transform: uppercase; letter-spacing: 0.5px; }
    .trust-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
    .trust-track { flex: 1; height: 8px; background: var(--pages-neutral-3, #e5e5e5); border-radius: 4px; overflow: hidden; position: relative; }
    .trust-fill { height: 100%; border-radius: 4px; transition: width 0.3s; }
    .trust-fill.high { background: var(--pages-success-9, #16a34a); }
    .trust-fill.mid { background: var(--pages-primary-9, #1d4ed8); }
    .trust-fill.low { background: var(--pages-warning-9, #d97706); }
    .trust-fill.very-low { background: var(--pages-danger-9, #dc2626); }
    .trust-threshold { position: absolute; top: -2px; bottom: -2px; width: 2px; background: var(--pages-neutral-7, #525252); }
    .trust-value { font-size: 13px; font-weight: 600; min-width: 40px; }
    .trust-context { font-size: 11px; color: var(--pages-neutral-7, #525252); margin-top: 2px; }
    .lane-reason { font-size: 12px; color: var(--pages-neutral-7, #525252); margin-top: 4px; font-style: italic; }
    .dim-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 6px; }
    .dim-item { font-size: 12px; display: flex; justify-content: space-between; padding: 4px 8px; background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px; }
    .dim-label { color: var(--pages-neutral-8, #404040); }
    .dim-val { font-weight: 600; }
    .check-list { list-style: none; padding: 0; margin: 6px 0 0 0; }
    .check-item { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 12px; }
    .check-icon { width: 16px; text-align: center; font-size: 14px; }
    .check-name { flex: 1; }
    .check-status { font-size: 11px; font-weight: 600; }
    .outcome-row { display: flex; gap: 8px; align-items: center; padding: 3px 0; font-size: 12px; }
    .outcome-icon { width: 16px; text-align: center; }
    .dep-list { list-style: none; padding: 0; margin: 0; }
    .dep-list li { padding: 3px 0; font-size: 13px; color: var(--pages-neutral-8, #404040); }
    .actions { display: flex; gap: 8px; margin: 16px 0; }
    .actions button {
      padding: 6px 14px; border-radius: 4px; font-size: 13px; font-weight: 500;
      cursor: pointer; border: 1px solid var(--pages-neutral-5, #a3a3a3);
      background: white; color: var(--pages-neutral-9, #171717);
    }
    .actions button:hover { background: var(--pages-neutral-2, #f5f5f5); }
    .actions button.primary {
      background: var(--pages-primary-9, #1d4ed8); color: white; border-color: var(--pages-primary-9, #1d4ed8);
    }
    .actions button.primary:hover { background: var(--pages-primary-10, #1e40af); }
    .actions button.danger {
      background: var(--pages-danger-9, #dc2626); color: white; border-color: var(--pages-danger-9, #dc2626);
    }
    .actions button.danger:hover { background: var(--pages-danger-10, #b91c1c); }
    .action-result { font-size: 12px; padding: 6px 10px; margin: 4px 0 8px; background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px; }
    .empty { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--pages-neutral-7, #525252); font-size: 13px; }
    .narrative { font-size: 13px; line-height: 1.5; color: var(--pages-neutral-8, #404040); margin: 6px 0 12px; }
    .threshold-legend { display: flex; gap: 12px; font-size: 11px; color: var(--pages-neutral-6, #737373); margin-top: 4px; margin-bottom: 8px; }
    .outcome-list { display: flex; gap: 4px; margin-top: 6px; }
    .outcome-chip {
      width: 24px; height: 24px; border-radius: 4px; display: flex; align-items: center; justify-content: center;
      font-size: 12px; font-weight: 700;
    }
    .outcome-chip.merged { background: var(--pages-success-3, #dcfce7); color: var(--pages-success-9, #166534); }
    .outcome-chip.closed { background: var(--pages-danger-3, #fee2e2); color: var(--pages-danger-9, #dc2626); }
  `;

  override willUpdate(changed: Map<PropertyKey, unknown>): void {
    if (changed.has('queuedPr')) {
      if (this.queuedPr && this.queuedPr.author !== this._prevAuthor) {
        this._prevAuthor = this.queuedPr.author;
        this._fetchContributor(this.queuedPr.author);
      } else if (!this.queuedPr) {
        this._prevAuthor = '';
        this._contributor = null;
      }
    }
  }

  private async _fetchContributor(actorId: string): Promise<void> {
    this._contributor = null;
    try {
      const res = await fetch(`${this.endpoint}/contributors/${actorId}`);
      if (res.ok) this._contributor = await res.json();
    } catch { /* no contributor data available */ }
  }

  private async _doAction(action: string): Promise<void> {
    if (!this.queuedPr) return;
    try {
      const res = await fetch(`/api/actions/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          repo: this.queuedPr.repository,
          prNumber: this.queuedPr.number,
          contributor: this.queuedPr.author,
          headSha: this.queuedPr.headSha,
        }),
      });
      const json = await res.json();
      this._actionResult = `${json.action}: ${json.result}`;
    } catch (err) {
      this._actionResult = `Error: ${err}`;
    }
  }

  private _laneBadgeClass(lane: string): string {
    const lower = lane.toLowerCase();
    if (lower.includes('fast')) return 'fast-track';
    if (lower.includes('enhanced')) return 'enhanced';
    return 'standard';
  }

  private _trustFillClass(score: number): string {
    if (score >= 0.80) return 'high';
    if (score >= 0.60) return 'mid';
    if (score >= 0.40) return 'low';
    return 'very-low';
  }

  private _checkIcon(status: string): string {
    if (status === 'COMPLETED') return '✅';
    if (status === 'FAILED' || status === 'DECLINED') return '❌';
    if (status === 'SCHEDULED' || status === 'IN_PROGRESS') return '⏳';
    return '⭕';
  }

  private _formatDimension(key: string): string {
    return key.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  override render() {
    if (this.queuedPr) return this._renderPr(this.queuedPr);
    if (this.batch) return this._renderBatch(this.batch);
    return html`<div class="empty">Select a queued PR or batch to see details</div>`;
  }

  private _buildTrustNarrative(pr: QueuedPrData, c: ContributorProfile): string {
    const ic = c.intakeClassification;
    const gh = c.githubIntelligence;
    const dims = c.dimensionScores;
    const totalPrs = gh ? gh.mergedCount + gh.closedCount : ic.observationCount;
    const trustPct = Math.round(pr.trustScore * 100);
    const ftPct = Math.round(ic.fastTrackThreshold * 100);
    const stdPct = Math.round(ic.standardThreshold * 100);

    const parts: string[] = [];

    if (gh && totalPrs > 0) {
      parts.push(`${pr.author} has submitted ${totalPrs} PRs — ${gh.mergedCount} merged, ${gh.closedCount} closed (${Math.round(gh.mergeRatio * 100)}% merge rate).`);
    } else {
      parts.push(`${pr.author} has ${ic.observationCount} observed PRs.`);
    }

    const lane = pr.priorityLane.toLowerCase();
    if (lane.includes('fast')) {
      parts.push(`Trust score ${trustPct}% exceeds the fast-track threshold (${ftPct}%), so this PR skips enhanced scrutiny.`);
    } else if (lane.includes('enhanced')) {
      parts.push(`Trust score ${trustPct}% is below the standard threshold (${stdPct}%), so this PR gets additional review scrutiny before merge.`);
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

  private _buildRecentPattern(outcomes: Array<{ outcome: string; timestamp: string }>): string | null {
    if (outcomes.length === 0) return null;
    const recent = outcomes.slice(0, 5);
    const merged = recent.filter(o => o.outcome === 'MERGED').length;
    if (merged === recent.length) return `Last ${recent.length} PRs all merged successfully.`;
    const closed = recent.length - merged;
    return `Last ${recent.length} PRs: ${merged} merged, ${closed} closed/rejected.`;
  }

  private _renderPr(pr: QueuedPrData) {
    const trustPct = Math.round(pr.trustScore * 100);
    const c = this._contributor;
    const review = this.reviews.find(r => r.caseId && r.status !== 'COMPLETED');

    return html`
      <div class="header">PR #${pr.number} — ${pr.repository}</div>
      <div class="sub-header">by ${pr.author} · waiting ${pr.waitMinutes} min · ${pr.headSha.substring(0, 7)}</div>

      <div class="section-title">Lane Assignment</div>
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
        <span class="badge ${this._laneBadgeClass(pr.priorityLane)}">${pr.priorityLane}</span>
      </div>

      ${c ? html`
        <div class="narrative">${this._buildTrustNarrative(pr, c)}</div>
      ` : nothing}

      <div class="section-title">Trust Score</div>
      <div class="trust-bar">
        <div class="trust-track">
          <div class="trust-fill ${this._trustFillClass(pr.trustScore)}" style="width:${trustPct}%"></div>
          ${c?.intakeClassification ? html`
            <div class="trust-threshold" style="left:${Math.round(c.intakeClassification.fastTrackThreshold * 100)}%" title="Fast-track threshold (${Math.round(c.intakeClassification.fastTrackThreshold * 100)}%)"></div>
            <div class="trust-threshold" style="left:${Math.round(c.intakeClassification.standardThreshold * 100)}%" title="Standard threshold (${Math.round(c.intakeClassification.standardThreshold * 100)}%)"></div>
          ` : nothing}
        </div>
        <div class="trust-value">${trustPct}%</div>
      </div>
      ${c?.intakeClassification ? html`
        <div class="threshold-legend">
          <span>▮ ${Math.round(c.intakeClassification.standardThreshold * 100)}% standard</span>
          <span>▮ ${Math.round(c.intakeClassification.fastTrackThreshold * 100)}% fast-track</span>
          <span>${c.intakeClassification.observationCount} observations</span>
        </div>
      ` : nothing}

      ${c?.dimensionScores && Object.keys(c.dimensionScores).length > 0 ? html`
        <div class="dim-grid">
          ${Object.entries(c.dimensionScores).map(([k, v]) => html`
            <div class="dim-item">
              <span class="dim-label">${this._formatDimension(k)}</span>
              <span class="dim-val">${typeof v === 'number' ? Math.round(v * 100) + '%' : '—'}</span>
            </div>
          `)}
        </div>
      ` : nothing}

      ${c?.recentOutcomes && c.recentOutcomes.length > 0 ? html`
        <div class="section-title">Recent Activity</div>
        <div class="narrative">${this._buildRecentPattern(c.recentOutcomes)}</div>
      ` : nothing}

      ${review && review.capabilities.length > 0 ? html`
        <div class="section-title">Review Checks</div>
        <ul class="check-list">
          ${review.capabilities.map(cap => html`
            <li class="check-item">
              <span class="check-icon">${this._checkIcon(cap.status)}</span>
              <span class="check-name">${cap.name}</span>
              <span class="check-status">${cap.status}</span>
            </li>
          `)}
        </ul>
      ` : nothing}

      ${pr.dependsOn && pr.dependsOn.length > 0 ? html`
        <div class="section-title">Dependencies</div>
        <ul class="dep-list">
          ${pr.dependsOn.map(d => html`<li>Blocked by PR #${d}</li>`)}
        </ul>
      ` : nothing}

      <div class="actions">
        <button class="danger" @click=${() => this._doAction('dequeue')}>Dequeue</button>
        <button class="primary" @click=${() => this._doAction('signal-ci-pass')}>Signal CI Pass</button>
      </div>
      ${this._actionResult ? html`<div class="action-result">${this._actionResult}</div>` : nothing}
    `;
  }

  private _batchElapsed(startedAt?: string): string {
    if (!startedAt) return '—';
    const mins = Math.round((Date.now() - new Date(startedAt).getTime()) / 60000);
    if (mins < 60) return `${mins} min`;
    return `${Math.floor(mins / 60)}h ${mins % 60}m`;
  }

  private _buildBatchNarrative(batch: BatchData): string {
    const prs = batch.prNumbers ?? [];
    const prList = prs.map(n => `#${n}`).join(', ');
    const ci = batch.ciStatus ?? 'UNKNOWN';

    if (ci === 'RUNNING') {
      return `Testing ${prs.length} PRs (${prList}) together against main. If CI passes, all merge as one atomic operation. If it fails, bisection will identify the faulty PR.`;
    }
    if (ci === 'BISECTING') {
      const suspect = batch.suspectedPr ? ` PR #${batch.suspectedPr} is the current suspect.` : '';
      return `CI failed for this batch of ${prs.length} PRs (${prList}). Bisection is running to isolate which PR caused the failure.${suspect} The faulty PR will be ejected and the rest retried.`;
    }
    if (ci === 'PASSED') {
      return `All ${prs.length} PRs (${prList}) passed CI together and are ready to merge.`;
    }
    return `Batch contains ${prs.length} PRs (${prList}). CI status: ${ci}.`;
  }

  private _renderBatch(batch: BatchData) {
    const riskClass = batch.riskLevel.toLowerCase().includes('elevated') ? 'elevated' : 'routine';
    const prs = batch.prNumbers ?? [];
    const ci = batch.ciStatus ?? '—';

    return html`
      <div class="header">Merge Batch — ${prs.length} PRs</div>
      <div class="sub-header">${ci === 'BISECTING' ? 'Bisecting — CI failure detected' : ci === 'RUNNING' ? 'CI running' : ci} · ${this._batchElapsed(batch.startedAt)} elapsed</div>

      <div class="narrative">${this._buildBatchNarrative(batch)}</div>

      <div class="section-title">PRs in Batch</div>
      <ul class="dep-list">
        ${prs.map(n => html`<li style="${batch.suspectedPr === n ? 'color:var(--pages-danger-9,#dc2626);font-weight:600' : ''}">PR #${n}${batch.suspectedPr === n ? ' — suspected cause' : ''}</li>`)}
      </ul>

      <dl class="meta" style="margin-top:12px">
        <dt>Risk</dt><dd><span class="badge ${riskClass}">${batch.riskLevel}</span></dd>
        <dt>CI Status</dt><dd>${ci}</dd>
      </dl>
    `;
  }
}
