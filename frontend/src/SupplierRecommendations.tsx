import React, { useState, useEffect } from 'react';
import {
  api,
  type SupplierRecommendationList,
  type SupplierRecommendationCandidateView,
  ApiError
} from './api';

// =============================================================================
// Industrial Icons (16px, SVG line art)
// =============================================================================

function IconShieldAlert({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  );
}

function IconCheckCircle({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  );
}

function IconAlertTriangle({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  );
}

function IconSparkles({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z" />
    </svg>
  );
}

function IconMedal({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <circle cx="12" cy="8" r="6" />
      <path d="M15.477 12.89 17 22l-5-3-5 3 1.523-9.11" />
    </svg>
  );
}

function IconClock({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
  );
}

function IconArrowLeft({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <line x1="19" y1="12" x2="5" y2="12" />
      <polyline points="12 19 5 12 12 5" />
    </svg>
  );
}

function IconRefresh({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0, ...style }}>
      <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
    </svg>
  );
}

// =============================================================================
// Props & Component
// =============================================================================

export interface SupplierRecommendationsProps {
  rfqId: string;
  onBack?: () => void;
  onSelectQuote?: (quoteRevisionId: string) => void;
  onViewScorecard?: (supplierId: string) => void;
}

export function SupplierRecommendations({
  rfqId,
  onBack,
  onSelectQuote,
  onViewScorecard
}: SupplierRecommendationsProps) {
  const [data, setData] = useState<SupplierRecommendationList | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchRecommendations = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.getSupplierRecommendations(rfqId);
      setData(res);
    } catch (e: any) {
      if (e instanceof ApiError) {
        setError(e.message);
      } else {
        setError(e?.message || 'Failed to load supplier recommendations');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRecommendations();
  }, [rfqId]);

  const formatCurrency = (val?: number | string | null) => {
    if (val === undefined || val === null) return '—';
    const num = typeof val === 'string' ? parseFloat(val) : val;
    if (isNaN(num)) return '—';
    return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const formatPercent = (val?: number | null) => {
    if (val === undefined || val === null) return '—';
    return `${val.toFixed(1)}%`;
  };

  const formatDate = (iso?: string | null) => {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch {
      return iso;
    }
  };

  const getTierBadgeStyle = (tier: string): React.CSSProperties => {
    switch (tier) {
      case 'RECOMMENDED':
        return {
          background: 'rgba(16, 185, 129, 0.15)',
          color: '#34d399',
          border: '1px solid rgba(16, 185, 129, 0.35)',
          padding: '2px 8px',
          borderRadius: '4px',
          fontSize: '11px',
          fontWeight: 600
        };
      case 'VIABLE_ALTERNATIVE':
        return {
          background: 'rgba(59, 130, 246, 0.15)',
          color: '#60a5fa',
          border: '1px solid rgba(59, 130, 246, 0.35)',
          padding: '2px 8px',
          borderRadius: '4px',
          fontSize: '11px',
          fontWeight: 600
        };
      case 'CAUTION_RISK':
        return {
          background: 'rgba(245, 158, 11, 0.15)',
          color: '#fbbf24',
          border: '1px solid rgba(245, 158, 11, 0.35)',
          padding: '2px 8px',
          borderRadius: '4px',
          fontSize: '11px',
          fontWeight: 600
        };
      case 'INSUFFICIENT_EVIDENCE':
      default:
        return {
          background: 'rgba(148, 163, 184, 0.12)',
          color: '#94a3b8',
          border: '1px solid rgba(148, 163, 184, 0.25)',
          padding: '2px 8px',
          borderRadius: '4px',
          fontSize: '11px',
          fontWeight: 600
        };
    }
  };

  const getTierLabel = (tier: string) => {
    switch (tier) {
      case 'RECOMMENDED': return 'Top Recommendation';
      case 'VIABLE_ALTERNATIVE': return 'Viable Alternative';
      case 'CAUTION_RISK': return 'Caution / High Risk';
      case 'INSUFFICIENT_EVIDENCE': return 'Insufficient Evidence';
      default: return tier;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }} data-testid="supplier-recommendations-view">
      {/* Top Header & Actions */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', paddingBottom: '12px', borderBottom: '1px solid #1e293b' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {onBack && (
            <button
              onClick={onBack}
              type="button"
              className="btn-sm btn-outline"
              title="Back to RFQ Details"
              aria-label="Back to RFQ"
              style={{ display: 'inline-flex', alignItems: 'center', padding: '6px 10px' }}
            >
              <IconArrowLeft />
            </button>
          )}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ display: 'inline-flex', padding: '4px', background: 'rgba(245, 158, 11, 0.15)', color: '#f59e0b', borderRadius: '4px', border: '1px solid rgba(245, 158, 11, 0.3)' }}>
                <IconSparkles />
              </span>
              <h2 style={{ margin: 0, fontSize: '18px', fontWeight: 700, color: '#f8fafc', letterSpacing: '-0.01em' }}>
                AI Supplier Recommendations & Advisory
              </h2>
            </div>
            <p style={{ margin: '4px 0 0', fontSize: '12px', color: '#94a3b8' }}>
              Deterministic ranking combining quoted financials with verified historical delivery and quality outcomes
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button
            onClick={fetchRecommendations}
            disabled={loading}
            type="button"
            className="btn-sm btn-outline"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <IconRefresh className={loading ? 'spin' : ''} />
            Refresh Analysis
          </button>
        </div>
      </div>

      {/* Advisory Disclaimer Banner (Human-in-the-Loop) */}
      <div className="banner banner-recommendation" style={{ borderLeft: '4px solid #f59e0b', background: 'rgba(245, 158, 11, 0.08)', padding: '16px', borderRadius: '6px', display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
        <span style={{ color: '#f59e0b', marginTop: '2px' }}>
          <IconShieldAlert />
        </span>
        <div style={{ fontSize: '13px', lineHeight: '1.5', color: '#fef3c7' }}>
          <strong style={{ display: 'block', color: '#fbbf24', marginBottom: '4px', fontSize: '13px' }}>
            Advisory Decision Support — Human Owner Approval Required
          </strong>
          Recommendations synthesize historical delivery metrics (OTD, yield, cost variance) and quoted terms.
          MUNIM does <strong>not</strong> perform autonomous awards, quote approvals, or purchase order generation.
          All commercial commitments remain strictly with the designated owner.
        </div>
      </div>

      {/* Loading state */}
      {loading && (
        <div style={{ padding: '60px 0', textAlign: 'center', color: '#94a3b8' }}>
          <div className="spin" style={{ display: 'inline-block', width: '28px', height: '28px', border: '3px solid #334155', borderTopColor: '#f59e0b', borderRadius: '50%', marginBottom: '12px' }} />
          <p style={{ fontSize: '13px', margin: 0 }}>Synthesizing supplier history and evaluating quotes...</p>
        </div>
      )}

      {/* Error state */}
      {error && !loading && (
        <div style={{ padding: '20px', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.3)', borderRadius: '8px', textAlign: 'center' }}>
          <p style={{ color: '#f87171', fontSize: '13px', marginBottom: '12px' }}>{error}</p>
          <button
            onClick={fetchRecommendations}
            type="button"
            className="btn-sm btn-outline"
          >
            Try Again
          </button>
        </div>
      )}

      {/* Data Loaded */}
      {!loading && !error && data && (
        <>
          {/* Executive Brief Card */}
          <div style={{ background: '#0b1120', border: '1px solid #1e293b', borderRadius: '8px', padding: '18px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px', marginBottom: '12px' }}>
              <div>
                <span className="eyebrow" style={{ display: 'block', marginBottom: '4px' }}>
                  Procurement Requirement
                </span>
                <div style={{ fontSize: '15px', fontWeight: 600, color: '#f8fafc' }}>
                  {data.rfqMaterialName} — {data.rfqQuantity} {data.rfqUnit}
                </div>
                <div style={{ fontSize: '12px', color: '#94a3b8', display: 'flex', alignItems: 'center', gap: '6px', marginTop: '4px' }}>
                  <IconClock /> Required Factory Delivery: <strong style={{ color: '#e2e8f0' }}>{formatDate(data.requiredDeliveryAt)}</strong>
                </div>
              </div>

              {/* AI Provider Tag */}
              <div>
                <span style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '4px 10px',
                  borderRadius: '4px',
                  fontSize: '11px',
                  fontWeight: 600,
                  background: data.aiGeneratedExplanation ? 'rgba(168, 85, 247, 0.15)' : 'rgba(100, 116, 139, 0.15)',
                  color: data.aiGeneratedExplanation ? '#d8b4fe' : '#cbd5e1',
                  border: data.aiGeneratedExplanation ? '1px solid rgba(168, 85, 247, 0.35)' : '1px solid rgba(100, 116, 139, 0.3)'
                }}>
                  <IconSparkles />
                  {data.aiGeneratedExplanation ? 'AI-Synthesized Narrative' : 'Deterministic Rule Engine'}
                  <span style={{ opacity: 0.75, fontFamily: 'monospace', fontSize: '10px' }}>({data.aiProviderUsed})</span>
                </span>
              </div>
            </div>

            {/* Narrative Box */}
            <div style={{ background: '#020617', border: '1px solid #1e293b', borderRadius: '6px', padding: '14px', fontSize: '13px', lineHeight: '1.6', color: '#cbd5e1' }}>
              {data.overallProcurementBrief}
            </div>
          </div>

          {/* Candidates List */}
          {data.recommendations.length === 0 ? (
            <div style={{ padding: '48px 16px', textAlign: 'center', background: '#0b1120', border: '1px solid #1e293b', borderRadius: '8px' }}>
              <p style={{ color: '#f1f5f9', fontWeight: 600, marginBottom: '6px', fontSize: '14px' }}>No Supplier Quotes Available</p>
              <p style={{ color: '#94a3b8', fontSize: '12px', margin: 0 }}>
                There are no evaluated supplier quotes recorded for this RFQ yet.
                Add and evaluate quotes to view ranked recommendations.
              </p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span className="eyebrow" style={{ margin: 0 }}>
                  Ranked Candidates ({data.recommendations.length})
                </span>
                <span style={{ fontSize: '11px', color: '#64748b' }}>
                  Ranked by delivery compliance, verified quality, landed cost, and evidence depth
                </span>
              </div>

              {data.recommendations.map((candidate: SupplierRecommendationCandidateView) => (
                <div
                  key={candidate.supplierId}
                  data-testid={`recommendation-card-${candidate.rank}`}
                  style={{
                    background: '#0b1120',
                    border: candidate.rank === 1
                      ? '1px solid rgba(16, 185, 129, 0.5)'
                      : (candidate.deliveryRiskWarning || candidate.costDriftWarning
                        ? '1px solid rgba(245, 158, 11, 0.4)'
                        : '1px solid #1e293b'),
                    borderRadius: '8px',
                    padding: '18px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '14px',
                    boxShadow: candidate.rank === 1 ? '0 0 15px rgba(16, 185, 129, 0.08)' : 'none'
                  }}
                >
                  {/* Candidate Header */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '10px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <span style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: '32px',
                        height: '32px',
                        borderRadius: '50%',
                        fontWeight: 700,
                        fontSize: '13px',
                        background: candidate.rank === 1 ? 'rgba(16, 185, 129, 0.2)' : 'rgba(30, 41, 59, 0.8)',
                        color: candidate.rank === 1 ? '#34d399' : '#cbd5e1',
                        border: candidate.rank === 1 ? '1px solid rgba(16, 185, 129, 0.4)' : '1px solid #334155'
                      }}>
                        #{candidate.rank}
                      </span>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                          <h4 style={{ margin: 0, fontSize: '15px', fontWeight: 700, color: '#f8fafc' }}>
                            {candidate.supplierName}
                          </h4>
                          <span style={getTierBadgeStyle(candidate.recommendationTier)}>
                            {getTierLabel(candidate.recommendationTier)}
                          </span>
                        </div>
                        {candidate.recommendationHeadline && (
                          <div style={{ fontSize: '12px', color: '#94a3b8', fontStyle: 'italic', marginTop: '3px' }}>
                            "{candidate.recommendationHeadline}"
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Suitability Score */}
                    {candidate.compositeSuitabilityScore !== undefined && candidate.compositeSuitabilityScore !== null && (
                      <div style={{ background: '#020617', border: '1px solid #1e293b', padding: '4px 10px', borderRadius: '4px', textAlign: 'right' }}>
                        <span style={{ fontSize: '9px', textTransform: 'uppercase', color: '#64748b', display: 'block', fontFamily: 'monospace' }}>Suitability Score</span>
                        <strong style={{ fontSize: '14px', color: '#f8fafc', fontWeight: 700 }}>
                          {candidate.compositeSuitabilityScore.toFixed(1)} <span style={{ fontSize: '10px', color: '#64748b', fontWeight: 400 }}>/ 100</span>
                        </strong>
                      </div>
                    )}
                  </div>

                  {/* Highlights / Badges Row */}
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                    {/* Evidence Scope Badge */}
                    {candidate.isMaterialSpecificEvidence ? (
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                        padding: '3px 8px',
                        background: 'rgba(16, 185, 129, 0.1)',
                        color: '#34d399',
                        border: '1px solid rgba(16, 185, 129, 0.25)',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 600
                      }}>
                        ● VERIFIED MATERIAL HISTORY
                      </span>
                    ) : (candidate.historicalScorecard && (candidate.historicalScorecard.eligiblePoCount ?? 0) > 0 ? (
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                        padding: '3px 8px',
                        background: 'rgba(245, 158, 11, 0.1)',
                        color: '#fbbf24',
                        border: '1px solid rgba(245, 158, 11, 0.25)',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 600
                      }}>
                        ▲ CROSS-MATERIAL CONTEXT ONLY
                      </span>
                    ) : (
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                        padding: '3px 8px',
                        background: 'rgba(148, 163, 184, 0.1)',
                        color: '#94a3b8',
                        border: '1px solid rgba(148, 163, 184, 0.25)',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 600
                      }}>
                        ○ UNRATED MATERIAL
                      </span>
                    ))}

                    {candidate.verifiedPerformerBadge && (
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        padding: '3px 8px',
                        background: 'rgba(16, 185, 129, 0.15)',
                        color: '#34d399',
                        border: '1px solid rgba(16, 185, 129, 0.35)',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 700,
                        letterSpacing: '0.02em'
                      }}>
                        <IconMedal /> VERIFIED PERFORMER
                      </span>
                    )}
                    {candidate.deliveryRiskWarning && (
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        padding: '3px 8px',
                        background: 'rgba(239, 68, 68, 0.15)',
                        color: '#f87171',
                        border: '1px solid rgba(239, 68, 68, 0.35)',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 700,
                        letterSpacing: '0.02em'
                      }}>
                        <IconAlertTriangle /> DELIVERY RISK WARNING
                      </span>
                    )}
                    {candidate.costDriftWarning && (
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        padding: '3px 8px',
                        background: 'rgba(245, 158, 11, 0.15)',
                        color: '#fbbf24',
                        border: '1px solid rgba(245, 158, 11, 0.35)',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 700,
                        letterSpacing: '0.02em'
                      }}>
                        <IconAlertTriangle /> COST DRIFT WARNING (&gt;+5%)
                      </span>
                    )}
                  </div>

                  {/* Comparison Grid: Quoted Commercials vs Historical Track Record */}
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
                    gap: '10px',
                    background: '#020617',
                    border: '1px solid #1e293b',
                    borderRadius: '6px',
                    padding: '12px',
                    fontSize: '11px'
                  }}>
                    <div>
                      <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Quoted Landed Cost</span>
                      <strong style={{ color: '#f8fafc', fontSize: '13px', fontFamily: 'monospace', display: 'block' }}>
                        {formatCurrency(candidate.quotedEconomicLandedCost)}
                      </strong>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Unit Price</span>
                      <span style={{ color: '#cbd5e1', fontFamily: 'monospace', display: 'block' }}>
                        {formatCurrency(candidate.quotedUnitPrice)}
                      </span>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Promised Delivery</span>
                      <span style={{
                        display: 'block',
                        fontWeight: 500,
                        color: candidate.meetsRequiredDeliveryDate === false ? '#f87171' : '#cbd5e1'
                      }}>
                        {formatDate(candidate.quotedPromisedDeliveryDate)}
                      </span>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Contract OTD</span>
                      <span style={{
                        display: 'block',
                        fontFamily: 'monospace',
                        color: candidate.historicalScorecard?.contractOtdRate !== null && (candidate.historicalScorecard?.contractOtdRate ?? 0) >= 90
                          ? '#34d399'
                          : '#cbd5e1'
                      }}>
                        {formatPercent(candidate.historicalScorecard?.contractOtdRate)}
                      </span>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Gate Acceptance</span>
                      <span style={{ color: '#cbd5e1', fontFamily: 'monospace', display: 'block' }}>
                        {formatPercent(candidate.historicalScorecard?.gateAcceptanceRate)}
                      </span>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Net Usable Yield</span>
                      <span style={{ color: '#cbd5e1', fontFamily: 'monospace', display: 'block' }}>
                        {formatPercent(candidate.historicalScorecard?.netUsableYieldRate)}
                      </span>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Evidence Tier</span>
                      <span style={{ color: '#cbd5e1', display: 'block' }}>
                        {candidate.historicalScorecard?.confidenceTier ?? 'UNRATED'}
                        <span style={{ fontSize: '10px', color: '#64748b', marginLeft: '4px' }}>
                          ({candidate.historicalScorecard?.eligiblePoCount ?? 0} POs)
                        </span>
                      </span>
                    </div>
                  </div>

                  {/* Evidence Chips: Strengths, Risks, Missing */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '11px' }}>
                    {candidate.verifiedStrengths.length > 0 && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '6px' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Strengths:</span>
                        {candidate.verifiedStrengths.map((str, idx) => (
                          <span key={idx} style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            padding: '2px 8px',
                            background: 'rgba(16, 185, 129, 0.1)',
                            color: '#6ee7b7',
                            border: '1px solid rgba(16, 185, 129, 0.25)',
                            borderRadius: '4px'
                          }}>
                            <IconCheckCircle /> {str}
                          </span>
                        ))}
                      </div>
                    )}

                    {candidate.identifiedRisks.length > 0 && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '6px' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Identified Risks:</span>
                        {candidate.identifiedRisks.map((risk, idx) => (
                          <span key={idx} style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            padding: '2px 8px',
                            background: 'rgba(239, 68, 68, 0.1)',
                            color: '#fca5a5',
                            border: '1px solid rgba(239, 68, 68, 0.25)',
                            borderRadius: '4px'
                          }}>
                            <IconAlertTriangle /> {risk}
                          </span>
                        ))}
                      </div>
                    )}

                    {candidate.missingEvidence.length > 0 && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '6px' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Missing Evidence:</span>
                        {candidate.missingEvidence.map((ev, idx) => (
                          <span key={idx} style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            padding: '2px 8px',
                            background: 'rgba(51, 65, 85, 0.4)',
                            color: '#94a3b8',
                            border: '1px solid rgba(71, 85, 105, 0.4)',
                            borderRadius: '4px'
                          }}>
                            {ev}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Advisory Explanation Text */}
                  {candidate.advisoryExplanation && (
                    <div style={{ background: '#020617', border: '1px solid #1e293b', borderRadius: '4px', padding: '10px 12px', fontSize: '12px', lineHeight: '1.5', color: '#cbd5e1' }}>
                      {candidate.advisoryExplanation}
                    </div>
                  )}

                  {/* Bottom Actions */}
                  <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '10px', paddingTop: '8px', borderTop: '1px solid #1e293b' }}>
                    {onViewScorecard && (
                      <button
                        onClick={() => onViewScorecard(candidate.supplierId)}
                        type="button"
                        className="btn-sm btn-outline"
                        style={{ fontSize: '11px', padding: '4px 10px' }}
                      >
                        View Full Scorecard
                      </button>
                    )}
                    {onSelectQuote && candidate.quoteRevisionId && (
                      <button
                        onClick={() => onSelectQuote(candidate.quoteRevisionId!)}
                        type="button"
                        className="btn-ai-gradient"
                        style={{ fontSize: '11px', padding: '4px 12px' }}
                      >
                        Inspect Quote
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
