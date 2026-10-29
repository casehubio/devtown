import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { renderTrustBar } from './shared/trust-bar.js';
import { renderDimensionGrid } from './shared/dimension-grid.js';
import { renderBadge } from './shared/badge.js';
import { renderWorkItemAlert } from './shared/work-item-alert.js';
import { hostStyles, sectionTitleStyles, narrativeStyles, trustBarStyles, dimensionGridStyles, badgeStyles, workItemAlertStyles, reviewCardStyles } from './shared/shared-styles.js';
import './routing-summary.js';
import './findings-panel.js';

type IntelNodeType = 'case-header' | 'dispatch' | 'findings' | 'human-gate' | 'lifecycle' | '';

interface FindingEntry {
  severity: string;
  category: string;
  filePath: string;
  message: string;
  confidence: number;
  startLine: number | null;
  endLine: number | null;
}

interface CacheEntry {
  promise: Promise<unknown>;
  timestamp: number;
}

const CACHE_TTL_MS = 30_000;

@customElement('intelligence-pane')
export class IntelligencePaneElement extends LitElement {
  @property({ type: String, attribute: 'node-type' }) intelNodeType: IntelNodeType = '';
  @property({ type: Object, attribute: false }) nodeData: Record<string, unknown> = {};
  @property({ type: String }) endpoint = '';
  @property({ type: String, attribute: 'case-id' }) caseId = '';

  @state() private _reviewDetail: Record<string, unknown> | null = null;
  @state() private _contributor: Record<string, unknown> | null = null;
  @state() private _precedents: unknown[] = [];
  @state() private _reviewer: Record<string, unknown> | null = null;
  @state() private _routingDetail: Record<string, unknown> | null = null;
  @state() private _loading = false;

  private _cache = new Map<string, CacheEntry>();

  static override styles = [
    hostStyles, sectionTitleStyles, narrativeStyles, trustBarStyles,
    dimensionGridStyles, badgeStyles, workItemAlertStyles, reviewCardStyles,
    css`
      :host { display: block; height: 100%; overflow: auto; padding: 16px; font-family: var(--pages-font-family, system-ui); }
      .section { margin-bottom: 16px; }
      .section-title { font-size: 11px; text-transform: uppercase; color: var(--pages-neutral-6, #737373); margin-bottom: 6px; font-weight: 600; }
      .finding-card { padding: 8px; border-radius: 4px; margin: 6px 0; border: 1px solid var(--pages-neutral-4, #d4d4d4); }
      .finding-high { border-color: var(--pages-danger-6, #dc2626); background: var(--pages-danger-1, #fef2f2); }
      .finding-medium { border-color: var(--pages-warning-6, #d97706); background: var(--pages-warning-1, #fffbeb); }
      .finding-severity { font-size: 11px; font-weight: 600; }
      .finding-file { font-size: 10px; color: var(--pages-neutral-6, #737373); }
      .precedent-card { padding: 8px; border-radius: 4px; margin: 6px 0; border-left: 3px solid var(--pages-primary-6, #2563eb); background: var(--pages-primary-1, #eff6ff); }
      .pane-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
      .meta-row { font-size: 11px; color: var(--pages-neutral-7, #525252); margin-bottom: 4px; }
      .empty { padding: 40px; text-align: center; color: var(--pages-neutral-6, #737373); }
      .data-gap { font-size: 11px; color: var(--pages-neutral-5, #a3a3a3); font-style: italic; }
      .rationale-card { padding: 10px; border-radius: 4px; background: var(--pages-neutral-2, #f5f5f5); margin: 6px 0; }
      .candidate { padding: 4px 0; font-size: 11px; border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5); }
      .candidate:last-child { border-bottom: none; }
    `,
  ];

  private async _cachedFetch(url: string): Promise<unknown> {
    const cached = this._cache.get(url);
    if (cached && Date.now() - cached.timestamp < CACHE_TTL_MS) {
      return cached.promise;
    }
    const promise = fetch(url).then(r => r.ok ? r.json() : null);
    this._cache.set(url, { promise, timestamp: Date.now() });
    return promise;
  }

  override willUpdate(changed: Map<string, unknown>): void {
    if (changed.has('intelNodeType') || changed.has('caseId') || changed.has('nodeData')) {
      this._loadData();
    }
  }

  private async _loadData(): Promise<void> {
    if (!this.caseId && this.intelNodeType !== 'lifecycle') return;
    this._loading = true;
    this._reviewDetail = null;
    this._contributor = null;
    this._precedents = [];
    this._reviewer = null;
    this._routingDetail = null;

    try {
      switch (this.intelNodeType) {
        case 'case-header': {
          const [detail, precedents] = await Promise.all([
            this._cachedFetch(`${this.endpoint}/${this.caseId}`),
            this._cachedFetch(`${this.endpoint}/${this.caseId}/similar-cases`),
          ]);
          this._reviewDetail = detail as Record<string, unknown>;
          this._precedents = (precedents as unknown[]) ?? [];
          const pr = (this._reviewDetail?.pr as Record<string, unknown>) ?? {};
          if (pr.contributor) {
            this._contributor = await this._cachedFetch(`${this.endpoint}/contributors/${pr.contributor}`) as Record<string, unknown>;
          }
          break;
        }
        case 'dispatch': {
          const actorId = this.nodeData.actor as string;
          const entryId = this.nodeData.entryId as string | undefined;
          const fetches: Promise<unknown>[] = [
            this._cachedFetch(`${this.endpoint}/reviewers/${actorId}`),
          ];
          if (entryId) {
            fetches.push(this._cachedFetch(`/api/devtown/trust/${actorId}/routing-history/${entryId}`));
          }
          const [reviewer, routing] = await Promise.all(fetches);
          this._reviewer = reviewer as Record<string, unknown>;
          this._routingDetail = (routing as Record<string, unknown>) ?? null;
          break;
        }
        case 'findings': {
          const [detail, precedents] = await Promise.all([
            this._cachedFetch(`${this.endpoint}/${this.caseId}`),
            this._cachedFetch(`${this.endpoint}/${this.caseId}/similar-cases`),
          ]);
          this._reviewDetail = detail as Record<string, unknown>;
          this._precedents = (precedents as unknown[]) ?? [];
          break;
        }
        case 'human-gate': {
          this._reviewDetail = await this._cachedFetch(`${this.endpoint}/${this.caseId}`) as Record<string, unknown>;
          break;
        }
        case 'lifecycle':
          break;
      }
    } catch (e) {
      console.warn('Intelligence pane fetch error', e);
    }
    this._loading = false;
  }

  override render() {
    if (!this.intelNodeType) return html`<div class="empty">Select a case or event node to view intelligence</div>`;
    if (this._loading) return html`<div class="empty">Loading...</div>`;

    switch (this.intelNodeType) {
      case 'case-header': return this._renderCaseHeader();
      case 'dispatch': return this._renderDispatch();
      case 'findings': return this._renderFindings();
      case 'human-gate': return this._renderHumanGate();
      case 'lifecycle': return this._renderLifecycle();
      default: return html`<div class="empty">Unknown node type</div>`;
    }
  }

  private _renderCaseHeader() {
    const pr = (this._reviewDetail?.pr as Record<string, unknown>) ?? {};
    const routing = (this._reviewDetail?.routing as Record<string, unknown>) ?? {};
    const contrib = this._contributor as Record<string, unknown> | null;

    return html`
      <div class="pane-title">PR #${pr.prNumber} — ${pr.repo}</div>
      <div class="meta-row">${pr.contributor} · ${pr.linesChanged} lines</div>

      ${contrib ? html`
        <div class="section">
          <div class="section-title">Contributor Profile</div>
          ${renderBadge((contrib.intakeClassification as Record<string, unknown>)?.lane as string ?? 'standard', 'standard')}
          ${renderTrustBar({ score: (contrib.globalScore as number) ?? 0, thresholds: [], observationCount: 0 })}
          ${contrib.dimensionScores ? renderDimensionGrid(Object.entries(contrib.dimensionScores as Record<string, number>).map(([key, value]) => ({ key, value }))) : nothing}
          ${(contrib.githubIntelligence as Record<string, unknown>) ? html`
            <div class="meta-row">${(contrib.githubIntelligence as Record<string, unknown>).mergedCount} merges, ${Math.round(((contrib.githubIntelligence as Record<string, unknown>).mergeRatio as number) * 100)}% merge rate</div>
          ` : nothing}
        </div>
      ` : nothing}

      ${this._precedents.length > 0 ? html`
        <div class="section">
          <div class="section-title">Similar Past Cases (${this._precedents.length})</div>
          ${this._precedents.slice(0, 5).map((p: unknown) => {
            const prec = p as Record<string, unknown>;
            return html`
              <div class="precedent-card">
                <div style="font-size:12px;font-weight:600">Case ${prec.caseId}</div>
                <div class="meta-row">Similarity: ${Math.round(((prec.similarity as Record<string, unknown>)?.score as number ?? 0) * 100)}%</div>
              </div>
            `;
          })}
        </div>
      ` : nothing}

      ${(routing as Record<string, unknown>).decisions ? html`
        <div class="section">
          <div class="section-title">Routing</div>
          <devtown-routing-summary .routing=${routing}></devtown-routing-summary>
        </div>
      ` : nothing}
    `;
  }

  private _renderDispatch() {
    const capName = this.nodeData.capabilityName as string ?? 'unknown';
    const reason = this.nodeData.reason as string ?? '';
    const confidence = this.nodeData.confidence as number ?? 0;
    const actor = this.nodeData.actor as string ?? '';
    const entryId = this.nodeData.entryId as string | undefined;

    return html`
      <div class="pane-title">${capName.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase())} → ${actor}</div>

      <div class="section">
        <div class="section-title">Selection Rationale</div>
        <div class="rationale-card">
          <div class="meta-row">Reason: ${reason}</div>
          <div class="meta-row">Confidence: ${Math.round(confidence * 100)}%</div>
        </div>
      </div>

      ${this._reviewer ? html`
        <div class="section">
          <div class="section-title">Agent Profile</div>
          <div class="meta-row">${(this._reviewer as Record<string, unknown>).totalDecisions ?? 0} total decisions · ${(this._reviewer as Record<string, unknown>).openCommitments ?? 0} open commitments</div>
          ${(this._reviewer as Record<string, unknown>).trustByCapability ? renderDimensionGrid(Object.entries((this._reviewer as Record<string, unknown>).trustByCapability as Record<string, number>).map(([key, value]) => ({ key, value }))) : nothing}
        </div>
      ` : nothing}

      ${this._routingDetail ? html`
        <div class="section">
          <div class="section-title">Candidate Alternatives</div>
          ${(((this._routingDetail as Record<string, unknown>).rationale as Record<string, unknown>)?.candidates as unknown[] ?? []).map((c: unknown) => {
            const cand = c as Record<string, unknown>;
            return html`
              <div class="candidate">
                <strong>${cand.workerId}</strong> — trust: ${(cand.trustScore as number)?.toFixed(2) ?? '—'}, final: ${(cand.finalScore as number)?.toFixed(2) ?? '—'}
                ${cand.exclusionReason ? html` <em>(${cand.exclusionReason})</em>` : nothing}
              </div>
            `;
          })}
        </div>
      ` : html`
        ${!entryId ? html`<div class="data-gap">Full rationale available after review completes</div>` : nothing}
      `}
    `;
  }

  private _renderFindings() {
    const findings = (this._reviewDetail?.findings as Record<string, FindingEntry[]>) ?? {};
    const allFindings = Object.entries(findings).flatMap(([cap, items]) =>
      items.map(f => ({ ...f, capability: cap }))
    );

    return html`
      <div class="pane-title">Findings (${allFindings.length})</div>

      ${allFindings.map(f => html`
        <div class="finding-card ${f.severity === 'HIGH' ? 'finding-high' : f.severity === 'MEDIUM' ? 'finding-medium' : ''}">
          <div class="finding-severity" style="color:${f.severity === 'HIGH' ? 'var(--pages-danger-7, #b91c1c)' : 'var(--pages-warning-7, #a16207)'}">${f.severity}: ${f.message}</div>
          <div class="finding-file">${f.filePath}${f.startLine ? `:${f.startLine}` : ''} · confidence ${Math.round((f.confidence as number) * 100)}%</div>
        </div>
      `)}

      ${this._precedents.length > 0 ? html`
        <div class="section" style="margin-top:16px">
          <div class="section-title">Precedent Comparison (CBR)</div>
          ${this._precedents.slice(0, 3).map((p: unknown) => {
            const prec = p as Record<string, unknown>;
            return html`
              <div class="precedent-card">
                <div style="font-size:12px">Similar case ${prec.caseId}</div>
                <div class="meta-row">Similarity: ${Math.round(((prec.similarity as Record<string, unknown>)?.score as number ?? 0) * 100)}%</div>
              </div>
            `;
          })}
        </div>
      ` : nothing}
    `;
  }

  private _renderHumanGate() {
    return html`
      <div class="pane-title">Human Decision Required</div>
      ${renderWorkItemAlert({
        workItemId: this.nodeData.workItemId as string ?? '',
        decisionType: this.nodeData.decisionType as string ?? '',
        candidateGroup: this.nodeData.candidateGroup as string ?? '',
        expiresAt: this.nodeData.expiresAt as string ?? '',
        escalationStage: this.nodeData.escalationStage as string ?? '',
        status: this.nodeData.status as string ?? 'PENDING',
      })}
    `;
  }

  private _renderLifecycle() {
    return html`
      <div class="pane-title">${this.nodeData.eventType ?? 'Event'}</div>
      <div class="meta-row">Actor: ${this.nodeData.actor ?? 'system'}</div>
      <div class="meta-row">Timestamp: ${this.nodeData.timestamp ? new Date(this.nodeData.timestamp as string).toLocaleString() : '—'}</div>
    `;
  }
}
