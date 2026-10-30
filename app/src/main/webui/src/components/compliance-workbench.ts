import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { renderTrustBar } from './shared/trust-bar.js';
import { trustBarStyles } from './shared/shared-styles.js';
import '@casehubio/mosaic-compliance-summary';
import '@casehubio/mosaic-audit-trail-viewer';
import '@casehubio/mosaic-gdpr-erasure-action';

interface CaseEntry {
  caseId: string;
  repo: string;
  prNumber: number;
  contributor: string;
  status: string;
}

interface LedgerEventRecord {
  entryId: string;
  eventType: string;
  actorId: string;
  actorRole: string;
  occurredAt: string;
  digest: string;
}

interface RoutingDecisionRecord {
  capabilityTag: string;
  strategyId: string;
  selectedWorkerId: string;
  finalScore: number;
  alternatives: Array<{ workerId: string; score: number }>;
}

interface ComplianceEvidence {
  caseId: string;
  generatedAt: string;
  auditChain: {
    requirementId: string; citation: string; mechanism: string;
    status: string; treeRoot: string; chainVerified: boolean;
    events: LedgerEventRecord[];
  };
  reviewSla: {
    requirementId: string; citation: string; mechanism: string;
    status: string; taskId: string; claimDeadline: string;
    completedAt: string; slaMet: boolean; candidateGroups: string[];
  };
  trustRouting: {
    requirementId: string; citation: string; mechanism: string;
    status: string; decisions: RoutingDecisionRecord[];
  };
  gdpr: {
    requirementId: string; citation: string; mechanism: string;
    status: string; erasureCapabilityWired: boolean;
    pseudonymisationActive: boolean; erasurePerformed: boolean;
    erasureReceiptIds: string[];
  };
}

interface RequirementDefinition {
  regulation: string;
  requirement: string;
  mechanism: string;
  status: string;
  evidenceUrl?: string;
}

@customElement('devtown-compliance-workbench')
export class ComplianceWorkbench extends LitElement {
  @property({ type: String }) endpoint = '';

  @state() private _cases: CaseEntry[] = [];
  @state() private _selectedCaseId = '';
  @state() private _evidence: ComplianceEvidence | null = null;
  @state() private _loading = false;
  @state() private _expandedSections = new Set<string>();

  static override styles = [
    trustBarStyles,
    css`
      :host { display: block; height: 100%; font-family: var(--pages-font-family, system-ui); }
      .list-panel { height: 100%; overflow: auto; padding: 0 12px; }
      .detail-panel { height: 100%; overflow: auto; padding: 0 16px; }
      .section-title { font-size: 13px; font-weight: 600; margin: 16px 0 8px; color: var(--pages-neutral-9, #404040); text-transform: uppercase; }
      .case-row { padding: 8px 10px; border-radius: 4px; margin-bottom: 4px; cursor: pointer; background: var(--pages-neutral-2, #f5f5f5); }
      .case-row:hover { background: var(--pages-neutral-3, #e5e5e5); }
      .case-row.selected { background: var(--pages-primary-2, #dbeafe); }
      .case-title { font-size: 12px; font-weight: 600; }
      .case-meta { font-size: 10px; color: var(--pages-neutral-7, #525252); }
      .empty { padding: 40px; text-align: center; color: var(--pages-neutral-6, #737373); }
      .card { border: 1px solid var(--pages-neutral-3, #e5e5e5); border-radius: 6px; margin: 8px 0; }
      .card-header { padding: 10px 12px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
      .card-header:hover { background: var(--pages-neutral-2, #f5f5f5); }
      .card-body { padding: 0 12px 12px; }
      .badge { display: inline-block; font-size: 10px; padding: 2px 8px; border-radius: 8px; font-weight: 600; }
      .badge.met { background: var(--pages-success-2, #dcfce7); color: var(--pages-success-8, #166534); }
      .badge.partial { background: var(--pages-warning-2, #fef3c7); color: var(--pages-warning-8, #92400e); }
      .badge.gap { background: var(--pages-warning-3, #fde68a); color: var(--pages-warning-9, #78350f); }
      .badge.breached { background: var(--pages-danger-2, #fee2e2); color: var(--pages-danger-8, #991b1b); }
      .kv-row { display: flex; gap: 8px; padding: 4px 0; font-size: 12px; }
      .kv-label { font-weight: 600; min-width: 120px; color: var(--pages-neutral-7, #525252); }
      .kv-value { color: var(--pages-neutral-9, #404040); }
      .decision-card { padding: 8px; border: 1px solid var(--pages-neutral-2, #f5f5f5); border-radius: 4px; margin: 4px 0; }
      .decision-cap { font-size: 12px; font-weight: 600; }
      .decision-meta { font-size: 11px; color: var(--pages-neutral-6, #737373); }
      .erasure-section { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--pages-neutral-3, #e5e5e5); }
    `,
  ];

  override connectedCallback(): void {
    super.connectedCallback();
    this._fetchCases();
  }

  configure(props: Record<string, unknown>): void {
    if (props.endpoint) this.endpoint = String(props.endpoint);
  }

  private async _fetchCases(): Promise<void> {
    try {
      const res = await fetch('/api/devtown/governance/queue-status');
      if (res.ok) {
        const data = await res.json();
        this._cases = data.reviews ?? [];
      }
    } catch { /* unavailable */ }
  }

  private async _selectCase(caseId: string): Promise<void> {
    this._selectedCaseId = caseId;
    this._loading = true;
    this._evidence = null;
    try {
      const res = await fetch(`${this.endpoint}/code-review/${caseId}`);
      if (res.ok) this._evidence = await res.json();
    } catch { /* unavailable */ }
    this._loading = false;
  }

  private _toggleSection(key: string): void {
    const next = new Set(this._expandedSections);
    if (next.has(key)) next.delete(key); else next.add(key);
    this._expandedSections = next;
  }

  private _toRequirements(ev: ComplianceEvidence): RequirementDefinition[] {
    return [
      { regulation: ev.auditChain.citation, requirement: 'Logging Requirements', mechanism: ev.auditChain.mechanism, status: ev.auditChain.status },
      { regulation: ev.reviewSla.citation, requirement: 'Human Oversight', mechanism: ev.reviewSla.mechanism, status: ev.reviewSla.status },
      { regulation: ev.trustRouting.citation, requirement: 'Trust Routing', mechanism: ev.trustRouting.mechanism, status: ev.trustRouting.status },
      { regulation: ev.gdpr.citation, requirement: 'Right to Erasure / Automated Decisions', mechanism: ev.gdpr.mechanism, status: ev.gdpr.status },
    ];
  }

  private _statusBadge(status: string) {
    const cls = status === 'MET' ? 'met' : status === 'PARTIAL' ? 'partial' : status === 'GAP' ? 'gap' : 'breached';
    return html`<span class="badge ${cls}">${status}</span>`;
  }

  private _renderAuditChain(ac: ComplianceEvidence['auditChain']) {
    return html`
      <div class="kv-row"><span class="kv-label">Tree Root</span><span class="kv-value" style="font-family:monospace">${ac.treeRoot}</span></div>
      <div class="kv-row"><span class="kv-label">Chain Verified</span><span class="kv-value">${ac.chainVerified ? 'Yes' : 'No'}</span></div>
      <div class="kv-row"><span class="kv-label">Ledger Entries</span><span class="kv-value">${ac.events.length} entries</span></div>
      <agency-audit-trail-viewer subject-id="${this._selectedCaseId}"></agency-audit-trail-viewer>
    `;
  }

  private _renderReviewSla(sla: ComplianceEvidence['reviewSla']) {
    return html`
      <div class="kv-row"><span class="kv-label">Task ID</span><span class="kv-value" style="font-family:monospace">${sla.taskId}</span></div>
      <div class="kv-row"><span class="kv-label">Claim Deadline</span><span class="kv-value">${new Date(sla.claimDeadline).toLocaleString()}</span></div>
      <div class="kv-row"><span class="kv-label">Completed At</span><span class="kv-value">${sla.completedAt ? new Date(sla.completedAt).toLocaleString() : '—'}</span></div>
      <div class="kv-row"><span class="kv-label">SLA Met</span><span class="kv-value">${sla.slaMet ? 'Yes' : 'No'}</span></div>
      <div class="kv-row"><span class="kv-label">Candidate Groups</span><span class="kv-value">${sla.candidateGroups.join(', ') || '—'}</span></div>
    `;
  }

  private _renderTrustRouting(tr: ComplianceEvidence['trustRouting']) {
    return html`
      ${tr.decisions.map(d => html`
        <div class="decision-card">
          <div class="decision-cap">${d.capabilityTag.replace(/-/g, ' ')}</div>
          <div class="decision-meta">Strategy: ${d.strategyId} · Selected: ${d.selectedWorkerId}</div>
          ${renderTrustBar({ score: d.finalScore })}
          ${d.alternatives.length > 0 ? html`
            <div class="decision-meta" style="margin-top:4px">Alternatives: ${d.alternatives.map(a => `${a.workerId} (${Math.round(a.score * 100)}%)`).join(', ')}</div>
          ` : nothing}
        </div>
      `)}
    `;
  }

  private _renderGdpr(g: ComplianceEvidence['gdpr']) {
    return html`
      <div class="kv-row"><span class="kv-label">Erasure Capable</span><span class="kv-value">${g.erasureCapabilityWired ? 'Yes' : 'No'}</span></div>
      <div class="kv-row"><span class="kv-label">Pseudonymisation</span><span class="kv-value">${g.pseudonymisationActive ? 'Active' : 'Inactive'}</span></div>
      <div class="kv-row"><span class="kv-label">Erasure Performed</span><span class="kv-value">${g.erasurePerformed ? 'Yes' : 'No'}</span></div>
      ${g.erasureReceiptIds.length > 0 ? html`
        <div class="kv-row"><span class="kv-label">Receipt IDs</span><span class="kv-value">${g.erasureReceiptIds.join(', ')}</span></div>
      ` : nothing}
      <div class="erasure-section">
        <div class="section-title">Request Erasure</div>
        <agency-gdpr-erasure-action endpoint="${this.endpoint}/erasure" subject-label="Actor"></agency-gdpr-erasure-action>
      </div>
    `;
  }

  override render() {
    const sections = [
      { key: 'audit', label: 'Audit Chain', status: () => this._evidence?.auditChain.status, render: () => this._renderAuditChain(this._evidence!.auditChain) },
      { key: 'sla', label: 'Review SLA', status: () => this._evidence?.reviewSla.status, render: () => this._renderReviewSla(this._evidence!.reviewSla) },
      { key: 'routing', label: 'Trust Routing', status: () => this._evidence?.trustRouting.status, render: () => this._renderTrustRouting(this._evidence!.trustRouting) },
      { key: 'gdpr', label: 'GDPR', status: () => this._evidence?.gdpr.status, render: () => this._renderGdpr(this._evidence!.gdpr) },
    ];

    return html`
      <pages-split-workbench selection-topic="compliance" title="Compliance">
        <div slot="list" class="list-panel">
          <div class="section-title">Cases (${this._cases.length})</div>
          ${this._cases.map(c => html`
            <div class="case-row ${this._selectedCaseId === c.caseId ? 'selected' : ''}"
                 @click=${() => this._selectCase(c.caseId)}>
              <div class="case-title">PR #${c.prNumber}</div>
              <div class="case-meta">${c.contributor} · ${c.repo.split('/').pop()}</div>
            </div>
          `)}
        </div>
        <div slot="detail" class="detail-panel">
          ${!this._selectedCaseId
            ? html`<div class="empty">Select a case to view compliance evidence</div>`
            : this._loading
              ? html`<div class="empty">Loading...</div>`
              : !this._evidence
                ? html`<div class="empty">No compliance data available</div>`
                : html`
                    <div class="section-title">Compliance Overview</div>
                    <agency-compliance-summary .requirements=${this._toRequirements(this._evidence)}></agency-compliance-summary>

                    ${sections.map(s => html`
                      <div class="card">
                        <div class="card-header" @click=${() => this._toggleSection(s.key)}>
                          <span>${this._expandedSections.has(s.key) ? '▾' : '▸'} ${s.label}</span>
                          ${this._statusBadge(s.status()!)}
                        </div>
                        ${this._expandedSections.has(s.key) ? html`<div class="card-body">${s.render()}</div>` : nothing}
                      </div>
                    `)}
                  `
          }
        </div>
      </pages-split-workbench>
    `;
  }
}
