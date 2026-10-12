import { html, nothing } from 'lit';
import type { TemplateResult } from 'lit';

export interface WorkItemData {
  workItemId: string;
  decisionType: string;
  candidateGroup: string;
  expiresAt: string;
  escalationStage: string;
  status: string;
}

function formatTimeRemaining(expiresAt: string): string {
  const diff = new Date(expiresAt).getTime() - Date.now();
  if (diff <= 0) return 'EXPIRED';
  const hours = Math.floor(diff / 3600000);
  const mins = Math.round((diff % 3600000) / 60000);
  if (hours > 0) return `${hours}h ${mins}m remaining`;
  return `${mins}m remaining`;
}

export function renderWorkItemAlert(item: WorkItemData | null): TemplateResult | typeof nothing {
  if (!item) return nothing;
  const isEscalated = item.escalationStage !== 'initial';
  return html`
    <div class="work-item-alert ${isEscalated ? 'escalated' : ''}">
      <div class="wia-header">
        <span class="wia-icon">${isEscalated ? '🔴' : '⚠️'}</span>
        <span class="wia-title">Human decision required</span>
      </div>
      <div class="wia-details">
        <div class="wia-row"><span class="wia-label">Type</span><span>${item.decisionType}</span></div>
        <div class="wia-row"><span class="wia-label">Assigned to</span><span>${item.candidateGroup}</span></div>
        <div class="wia-row"><span class="wia-label">Expires</span><span>${formatTimeRemaining(item.expiresAt)}</span></div>
        <div class="wia-row"><span class="wia-label">Escalation</span><span>${item.escalationStage}</span></div>
      </div>
    </div>
  `;
}
