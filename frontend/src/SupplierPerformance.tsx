import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  api,
  type SupplierScorecard,
  type SupplierPerformanceFilters,
  type ConfidenceTier,
  ApiError
} from './api';

// =============================================================================
// Industrial Line Icons (16px, 1.75px stroke, tabular-ready)
// =============================================================================

function IconArrowLeft({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', ...style }}>
      <line x1="19" y1="12" x2="5" y2="12" />
      <polyline points="12 19 5 12 12 5" />
    </svg>
  );
}

function IconRefresh({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', ...style }}>
      <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
    </svg>
  );
}

function IconInfo({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', ...style }}>
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="16" x2="12" y2="12" />
      <line x1="12" y1="8" x2="12.01" y2="8" />
    </svg>
  );
}

function IconFilter({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', ...style }}>
      <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
    </svg>
  );
}

function IconCheckCircle({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', ...style }}>
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  );
}

function IconAlertTriangle({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', ...style }}>
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  );
}

function IconShield({ className = '', style = {} }: { className?: string; style?: React.CSSProperties }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ display: 'inline-block', verticalAlign: 'middle', ...style }}>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  );
}

// =============================================================================
// Formatting & Null-Handling Helpers (Strict: Null is Never 0)
// =============================================================================

function formatPercentage(val: number | null | undefined): string {
  if (val === null || val === undefined) return '—';
  return `${Number(val).toFixed(2)}%`;
}

function formatDays(val: number | null | undefined): string {
  if (val === null || val === undefined) return '—';
  const num = Number(val);
  return `${num.toFixed(1)} ${num === 1.0 ? 'day' : 'days'}`;
}

function formatInrCurrency(val: number | null | undefined): string {
  if (val === null || val === undefined) return '—';
  const num = Number(val);
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2
  }).format(num);
}

function formatQuantity(val: number | null | undefined, unit = 'kg'): string {
  if (val === null || val === undefined) return '—';
  return `${Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })} ${unit}`;
}

// =============================================================================
// Confidence Tier Badge
// =============================================================================

export function SupplierConfidenceBadge({ tier, showLabel = true }: { tier: ConfidenceTier; showLabel?: boolean }) {
  switch (tier) {
    case 'HIGH':
      return (
        <span
          className="badge badge-approved"
          title="High Confidence: 8 or more eligible purchase orders with verified outcome history"
          style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', fontWeight: 600 }}
        >
          <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#10b981' }} />
          HIGH CONFIDENCE
        </span>
      );
    case 'MEDIUM':
      return (
        <span
          className="badge"
          title="Medium Confidence: 3 to 7 eligible purchase orders with verified outcome history"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '5px',
            fontWeight: 600,
            background: 'rgba(99, 102, 241, 0.15)',
            color: '#a5b4fc',
            border: '1px solid rgba(99, 102, 241, 0.35)'
          }}
        >
          <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#6366f1' }} />
          MEDIUM CONFIDENCE
        </span>
      );
    case 'LOW':
      return (
        <span
          className="badge badge-pending"
          title="Low Confidence: Fewer than 3 eligible purchase orders. Metrics represent preliminary sample."
          style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', fontWeight: 600 }}
        >
          <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#f59e0b' }} />
          LOW CONFIDENCE
        </span>
      );
    case 'UNRATED':
    default:
      return (
        <span
          className="badge badge-stale"
          title="Unrated: No verified delivery, quality, or invoice outcomes recorded yet."
          style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', fontWeight: 600 }}
        >
          <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#94a3b8' }} />
          {showLabel ? 'UNRATED' : '—'}
        </span>
      );
  }
}

// =============================================================================
// Reusable Metric Card (Preserves Null Distinction)
// =============================================================================

interface MetricCardProps {
  label: string;
  value: string;
  subValue?: string;
  isNull?: boolean;
  nullExplanation?: string;
  tone?: 'positive' | 'warning' | 'danger' | 'neutral';
  tooltip?: string;
  icon?: React.ReactNode;
}

function MetricCard({
  label,
  value,
  subValue,
  isNull = false,
  nullExplanation = 'Insufficient data',
  tone = 'neutral',
  tooltip,
  icon
}: MetricCardProps) {
  let valColor = '#f8fafc';
  if (!isNull) {
    if (tone === 'positive') valColor = '#10b981';
    else if (tone === 'warning') valColor = '#f59e0b';
    else if (tone === 'danger') valColor = '#f43f5e';
    else if (tone === 'neutral') valColor = '#a5b4fc';
  } else {
    valColor = '#64748b';
  }

  return (
    <div
      className="financial-metric-card"
      style={{
        background: 'rgba(12, 15, 23, 0.85)',
        border: '1px solid var(--border-subtle)',
        borderRadius: '10px',
        padding: '16px 18px',
        position: 'relative'
      }}
      title={tooltip}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-secondary)' }}>
          {label}
        </span>
        {icon && <span style={{ opacity: 0.7 }}>{icon}</span>}
      </div>

      <div style={{ fontSize: '24px', fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: valColor, letterSpacing: '-0.01em' }}>
        {isNull ? '—' : value}
      </div>

      <div style={{ fontSize: '11px', color: isNull ? 'var(--text-dim)' : 'var(--text-muted)', marginTop: '4px', fontVariantNumeric: 'tabular-nums' }}>
        {isNull ? nullExplanation : subValue}
      </div>
    </div>
  );
}

// =============================================================================
// Main Component Props
// =============================================================================

export interface SupplierPerformanceProps {
  role?: 'dev-owner' | 'dev-procurement' | 'dev-evaluator';
  initialSupplierId?: string | null;
  onNavigateSupplier?: (supplierId: string | null) => void;
}

export function SupplierPerformance({
  role = 'dev-owner',
  initialSupplierId,
  onNavigateSupplier
}: SupplierPerformanceProps) {
  // Navigation state: null = leaderboard, UUID = detailed scorecard
  const [selectedSupplierId, setSelectedSupplierId] = useState<string | null>(() => {
    if (initialSupplierId) return initialSupplierId;
    try {
      return new URLSearchParams(window.location.search).get('supplierId') || null;
    } catch {
      return null;
    }
  });

  // Data states
  const [scorecards, setScorecards] = useState<SupplierScorecard[]>([]);
  const [selectedScorecard, setSelectedScorecard] = useState<SupplierScorecard | null>(null);
  const [materials, setMaterials] = useState<Array<{ id: string; name: string; specification: string }>>([]);

  // Filter states
  const [materialFilter, setMaterialFilter] = useState<string>('');
  const [fromDateFilter, setFromDateFilter] = useState<string>('');
  const [toDateFilter, setToDateFilter] = useState<string>('');
  const [filterError, setFilterError] = useState<string | null>(null);
  const [appliedFilters, setAppliedFilters] = useState<SupplierPerformanceFilters>({});
  const [refreshNonce, setRefreshNonce] = useState<number>(0);

  // UI state
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefreshedAt, setLastRefreshedAt] = useState<string | null>(null);

  // Sorting state for leaderboard
  const [sortField, setSortField] = useState<'default' | 'name' | 'otd' | 'yield' | 'variance' | 'evidence'>('default');
  const [sortAsc, setSortAsc] = useState<boolean>(false);

  // Sync with prop when explicitly provided
  useEffect(() => {
    if (initialSupplierId) {
      setSelectedSupplierId(initialSupplierId);
    }
  }, [initialSupplierId]);

  // Load available materials for filter dropdown
  useEffect(() => {
    let mounted = true;
    api.materials()
      .then(res => {
        if (mounted && res && res.items) {
          setMaterials(res.items.map(m => ({ id: m.id, name: m.name, specification: m.specification })));
        }
      })
      .catch(() => {
        // Material API failure shouldn't crash the scorecard page
      });
    return () => { mounted = false; };
  }, []);

  // Format filter dates to ISO strings with timezone boundaries for backend
  const buildApiFilters = useCallback((): SupplierPerformanceFilters => {
    const filters: SupplierPerformanceFilters = {};
    if (materialFilter.trim()) {
      filters.materialId = materialFilter.trim();
    }
    if (fromDateFilter.trim()) {
      filters.fromDate = `${fromDateFilter.trim()}T00:00:00Z`;
    }
    if (toDateFilter.trim()) {
      filters.toDate = `${toDateFilter.trim()}T23:59:59Z`;
    }
    return filters;
  }, [materialFilter, fromDateFilter, toDateFilter]);

  // Validate date range
  const validateFilters = useCallback((): boolean => {
    if (fromDateFilter && toDateFilter) {
      if (fromDateFilter > toDateFilter) {
        setFilterError('From Date cannot be after To Date.');
        return false;
      }
    }
    setFilterError(null);
    return true;
  }, [fromDateFilter, toDateFilter]);

  // Trigger data load based on current route/view and applied filters
  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);

    if (selectedSupplierId) {
      api.getSupplierScorecard(selectedSupplierId, appliedFilters)
        .then(res => {
          if (mounted) {
            setSelectedScorecard(res);
            setLastRefreshedAt(new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true }));
          }
        })
        .catch(err => {
          if (mounted) {
            const msg = err instanceof ApiError ? err.message : err instanceof Error ? err.message : String(err);
            if (err instanceof ApiError && err.status === 404) {
              setError('Supplier scorecard not found (404). This supplier may not exist or has been deleted.');
            } else {
              setError(`Failed to load supplier scorecard: ${msg}`);
            }
            setSelectedScorecard(null);
          }
        })
        .finally(() => {
          if (mounted) setLoading(false);
        });
    } else {
      api.listSupplierScorecards(appliedFilters)
        .then(res => {
          if (mounted) {
            setScorecards(res.items || []);
            setLastRefreshedAt(new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true }));
          }
        })
        .catch(err => {
          if (mounted) {
            const msg = err instanceof ApiError ? err.message : err instanceof Error ? err.message : String(err);
            setError(`Failed to load supplier performance scorecards: ${msg}`);
          }
        })
        .finally(() => {
          if (mounted) setLoading(false);
        });
    }

    return () => { mounted = false; };
  }, [selectedSupplierId, appliedFilters, refreshNonce]);

  // Navigation handlers
  const handleSelectSupplier = (id: string) => {
    setSelectedSupplierId(id);
    if (onNavigateSupplier) onNavigateSupplier(id);
  };

  const handleBackToLeaderboard = () => {
    setSelectedSupplierId(null);
    setSelectedScorecard(null);
    setError(null);
    if (onNavigateSupplier) onNavigateSupplier(null);
  };

  const handleApplyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateFilters()) return;
    setAppliedFilters(buildApiFilters());
  };

  const handleClearFilters = () => {
    setMaterialFilter('');
    setFromDateFilter('');
    setToDateFilter('');
    setFilterError(null);
    setAppliedFilters({});
  };

  const triggerRefresh = () => {
    setRefreshNonce(n => n + 1);
  };

  // Derive overview counts (strictly from real API items)
  const overviewCounts = useMemo(() => {
    const total = scorecards.length;
    const high = scorecards.filter(s => s.confidenceTier === 'HIGH').length;
    const medium = scorecards.filter(s => s.confidenceTier === 'MEDIUM').length;
    const low = scorecards.filter(s => s.confidenceTier === 'LOW').length;
    const unrated = scorecards.filter(s => s.confidenceTier === 'UNRATED').length;
    return { total, high, medium, low, unrated };
  }, [scorecards]);

  // Sorted list for leaderboard
  const sortedScorecards = useMemo(() => {
    if (sortField === 'default') {
      // Respect backend's canonical ranking: Confidence tier desc -> OTD desc -> name asc
      return scorecards;
    }
    const copy = [...scorecards];
    copy.sort((a, b) => {
      let cmp = 0;
      if (sortField === 'name') {
        cmp = a.supplierName.localeCompare(b.supplierName);
      } else if (sortField === 'evidence') {
        cmp = a.eligiblePoCount - b.eligiblePoCount;
      } else if (sortField === 'otd') {
        const valA = a.contractOtdRate ?? -1;
        const valB = b.contractOtdRate ?? -1;
        cmp = Number(valA) - Number(valB);
      } else if (sortField === 'yield') {
        const valA = a.netUsableYieldRate ?? -1;
        const valB = b.netUsableYieldRate ?? -1;
        cmp = Number(valA) - Number(valB);
      } else if (sortField === 'variance') {
        const valA = a.costVariancePercentage ?? 999999;
        const valB = b.costVariancePercentage ?? 999999;
        cmp = Number(valA) - Number(valB);
      }
      return sortAsc ? cmp : -cmp;
    });
    return copy;
  }, [scorecards, sortField, sortAsc]);

  const toggleSort = (field: 'name' | 'otd' | 'yield' | 'variance' | 'evidence') => {
    if (sortField === field) {
      if (sortAsc) {
        // Reset to default
        setSortField('default');
        setSortAsc(false);
      } else {
        setSortAsc(true);
      }
    } else {
      setSortField(field);
      setSortAsc(false);
    }
  };

  // ===========================================================================
  // RENDER: INDIVIDUAL SUPPLIER SCORECARD VIEW
  // ===========================================================================
  if (selectedSupplierId) {
    return (
      <section className="card" aria-label="Individual Supplier Scorecard" style={{ marginTop: '16px' }}>
        {/* Navigation & Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
          <button
            type="button"
            className="btn-sm btn-outline"
            onClick={handleBackToLeaderboard}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
          >
            <IconArrowLeft /> Back to Supplier Leaderboard
          </button>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            {lastRefreshedAt && (
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Refreshed at {lastRefreshedAt}
              </span>
            )}
            <button
              type="button"
              className="btn-sm btn-outline"
              onClick={triggerRefresh}
              disabled={loading}
              title="Refresh scorecard data from backend"
            >
              <IconRefresh className={loading ? 'spin' : ''} style={{ marginRight: '6px' }} /> Refresh
            </button>
          </div>
        </div>

        {/* Filter Bar */}
        <form onSubmit={handleApplyFilters} className="what-if-panel" style={{ marginTop: 0, marginBottom: '24px', padding: '16px 20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
            <span style={{ fontSize: '12px', fontWeight: 700, letterSpacing: '0.05em', color: '#a5b4fc', textTransform: 'uppercase', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <IconFilter /> Filter Scorecard Outcome History
            </span>
            {(materialFilter || fromDateFilter || toDateFilter) && (
              <span className="badge badge-confirmed" style={{ fontSize: '11px' }}>
                Filters Active
              </span>
            )}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', alignItems: 'flex-end' }}>
            <div>
              <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                Material Filter
              </label>
              <select
                className="field-input"
                value={materialFilter}
                onChange={e => setMaterialFilter(e.target.value)}
                style={{ width: '100%', fontSize: '12px', padding: '8px 10px' }}
              >
                <option value="">All Materials</option>
                {materials.map((m, idx) => (
                  <option key={m.id ? `${m.id}-${idx}` : `mat-${idx}`} value={m.id}>
                    {m.name} ({m.specification})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                From Date
              </label>
              <input
                type="date"
                className="field-input"
                value={fromDateFilter}
                onChange={e => setFromDateFilter(e.target.value)}
                style={{ width: '100%', fontSize: '12px', padding: '8px 10px' }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                To Date
              </label>
              <input
                type="date"
                className="field-input"
                value={toDateFilter}
                onChange={e => setToDateFilter(e.target.value)}
                style={{ width: '100%', fontSize: '12px', padding: '8px 10px' }}
              />
            </div>

            <div style={{ display: 'flex', gap: '8px' }}>
              <button type="submit" className="btn-sm btn-primary" disabled={loading} style={{ flex: 1 }}>
                Apply
              </button>
              {(materialFilter || fromDateFilter || toDateFilter) && (
                <button type="button" className="btn-sm btn-outline" onClick={handleClearFilters} disabled={loading}>
                  Clear
                </button>
              )}
            </div>
          </div>

          {filterError && (
            <div style={{ marginTop: '10px', fontSize: '12px', color: '#f43f5e', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <IconAlertTriangle /> {filterError}
            </div>
          )}
        </form>

        {/* Loading Spinner */}
        {loading && (
          <div style={{ padding: '60px 0', textAlign: 'center', color: 'var(--text-muted)' }}>
            <div className="spin" style={{ display: 'inline-block', width: '24px', height: '24px', border: '2px solid rgba(255,255,255,0.2)', borderTopColor: '#6366f1', borderRadius: '50%', marginBottom: '12px' }} />
            <div>Calculating empirical supplier performance from verified outcomes...</div>
          </div>
        )}

        {/* Error State */}
        {error && !loading && (
          <div style={{ background: 'rgba(244, 63, 94, 0.1)', border: '1px solid rgba(244, 63, 94, 0.3)', borderRadius: '12px', padding: '24px', textAlign: 'center', margin: '20px 0' }}>
            <IconAlertTriangle style={{ color: '#f43f5e', width: '24px', height: '24px', marginBottom: '8px' }} />
            <h4 style={{ color: '#f43f5e', margin: '0 0 8px' }}>Unable to Display Scorecard</h4>
            <p style={{ color: 'var(--text-secondary)', fontSize: '13px', margin: '0 0 16px' }}>{error}</p>
            <button type="button" className="btn-sm btn-primary" onClick={triggerRefresh}>
              Retry
            </button>
          </div>
        )}

        {/* Scorecard Content */}
        {!loading && !error && selectedScorecard && (
          <div>
            {/* Header Identity Card */}
            <div style={{ background: 'rgba(18, 22, 34, 0.9)', border: '1px solid var(--border-subtle)', borderRadius: '14px', padding: '24px', marginBottom: '24px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                    <span className="phase-pill">VERIFIED OUTCOME TRACK RECORD</span>
                    <SupplierConfidenceBadge tier={selectedScorecard.confidenceTier} />
                  </div>
                  <h1 style={{ margin: '4px 0 6px', fontSize: '26px', fontWeight: 800, color: '#f8fafc' }}>
                    {selectedScorecard.supplierName}
                  </h1>
                  <div style={{ fontSize: '12px', color: 'var(--text-dim)', fontVariantNumeric: 'tabular-nums' }}>
                    Supplier ID: {selectedScorecard.supplierId}
                  </div>
                </div>

                <div style={{ textAlign: 'right', minWidth: '220px' }}>
                  <div style={{ fontSize: '11px', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    Confidence Assessment
                  </div>
                  <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px', maxWidth: '340px', lineHeight: 1.5 }}>
                    {selectedScorecard.confidenceExplanation}
                  </div>
                </div>
              </div>

              {/* Sample Size Warning if Unrated or Low */}
              {(selectedScorecard.confidenceTier === 'UNRATED' || selectedScorecard.confidenceTier === 'LOW') && (
                <div style={{ marginTop: '16px', padding: '10px 14px', background: 'rgba(245, 158, 11, 0.08)', border: '1px solid rgba(245, 158, 11, 0.25)', borderRadius: '8px', fontSize: '12px', color: '#fbbf24', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <IconAlertTriangle />
                  <span>
                    <strong>Statistical Evidence Notice:</strong> This supplier has {selectedScorecard.eligiblePoCount} eligible order{selectedScorecard.eligiblePoCount === 1 ? '' : 's'}. High or low percentages based on small sample sizes should be treated with caution.
                  </span>
                </div>
              )}
            </div>

            {/* Evidence Volume Strip */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '14px', marginBottom: '28px' }}>
              <MetricCard
                label="Eligible Purchase Orders"
                value={`${selectedScorecard.eligiblePoCount}`}
                subValue={`${selectedScorecard.onTimePoCount} On-Time · ${selectedScorecard.delayedPoCount} Delayed · ${selectedScorecard.shortClosedPoCount} Short-Closed`}
                tooltip="POs completed, short-closed, or currently overdue. Excludes unfulfilled future POs."
              />
              <MetricCard
                label="Verified Consignments"
                value={`${selectedScorecard.consignmentCount}`}
                subValue="Receipt events with verified gate inspection"
                tooltip="Count of distinct verified delivery intake receipt events."
              />
              <MetricCard
                label="Total Inspected Quantity"
                value={formatQuantity(selectedScorecard.inspectedQuantityTotal)}
                isNull={selectedScorecard.inspectedQuantityTotal === null}
                nullExplanation="No inspected receipts recorded"
                subValue="Verified cumulative receiving volume"
                tooltip="Cumulative quantity inspected across all verified delivery receipts."
              />
              <MetricCard
                label="Verified Actual Landed Spend"
                value={formatInrCurrency(selectedScorecard.totalActualLandedCost)}
                isNull={selectedScorecard.totalActualLandedCost === null}
                nullExplanation="No reconciled invoices"
                subValue="Landed material + freight cost"
                tooltip="Cumulative actual economic landed cost across reconciled invoices and signed adjustments."
              />
            </div>

            {/* SECTION 1: DELIVERY PERFORMANCE */}
            <div style={{ marginBottom: '32px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '14px' }}>
                <span className="eyebrow" style={{ margin: 0 }}>LOGISTICS & FULFILLMENT</span>
                <h3 style={{ margin: 0, fontSize: '18px' }}>Delivery Timeliness & Consignment Logistics</h3>
              </div>

              <div style={{ background: 'rgba(14, 18, 28, 0.8)', border: '1px solid var(--border-subtle)', borderRadius: '12px', padding: '20px', marginBottom: '16px' }}>
                <p style={{ margin: '0 0 16px', fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                  <strong>Contract OTD vs. Consignment OTD:</strong> Contract OTD measures whether the purchase order was fulfilled by the agreed promised date. Consignment metrics track partial deliveries, shipment frequency, and receipt timeliness.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
                  <MetricCard
                    label="Contract On-Time Delivery (OTD)"
                    value={formatPercentage(selectedScorecard.contractOtdRate)}
                    isNull={selectedScorecard.contractOtdRate === null}
                    nullExplanation="No eligible purchase orders"
                    tone={
                      selectedScorecard.contractOtdRate === null ? 'neutral' :
                      selectedScorecard.contractOtdRate >= 95 ? 'positive' :
                      selectedScorecard.contractOtdRate >= 80 ? 'warning' : 'danger'
                    }
                    subValue={`${selectedScorecard.onTimePoCount} of ${selectedScorecard.eligiblePoCount} POs fulfilled on time`}
                    tooltip="Percentage of eligible purchase orders completed on or before the promised delivery date."
                  />
                  <MetricCard
                    label="Average PO Delay"
                    value={formatDays(selectedScorecard.averagePoDelayDays)}
                    isNull={selectedScorecard.averagePoDelayDays === null}
                    nullExplanation="No delayed or overdue orders"
                    tone={
                      selectedScorecard.averagePoDelayDays === null ? 'neutral' :
                      selectedScorecard.averagePoDelayDays <= 2 ? 'warning' : 'danger'
                    }
                    subValue={`Calculated across ${selectedScorecard.delayedPoCount} delayed/overdue POs`}
                    tooltip="Average delay in days across completed late POs and open overdue orders."
                  />
                  <MetricCard
                    label="Consignment On-Time Rate"
                    value={formatPercentage(selectedScorecard.consignmentOnTimeRate)}
                    isNull={selectedScorecard.consignmentOnTimeRate === null}
                    nullExplanation="No verified consignments"
                    tone={
                      selectedScorecard.consignmentOnTimeRate === null ? 'neutral' :
                      selectedScorecard.consignmentOnTimeRate >= 90 ? 'positive' : 'warning'
                    }
                    subValue="Timeliness of individual delivery challans"
                    tooltip="Percentage of individual verified delivery intake events delivered on or before promised PO delivery date."
                  />
                  <MetricCard
                    label="Partial Delivery Order Rate"
                    value={formatPercentage(selectedScorecard.partialDeliveryOrderRate)}
                    isNull={selectedScorecard.partialDeliveryOrderRate === null}
                    nullExplanation="No completed PO history"
                    subValue={
                      selectedScorecard.averageConsignmentsPerCompletedPo !== null
                        ? `Avg ${Number(selectedScorecard.averageConsignmentsPerCompletedPo).toFixed(1)} shipments / PO`
                        : 'Single-shipment orders'
                    }
                    tooltip="Percentage of completed POs fulfilled in multiple shipments."
                  />
                </div>
              </div>
            </div>

            {/* SECTION 2: QUALITY PERFORMANCE */}
            <div style={{ marginBottom: '32px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '14px' }}>
                <span className="eyebrow" style={{ margin: 0 }}>QUALITY ASSURANCE</span>
                <h3 style={{ margin: 0, fontSize: '18px' }}>Material Quality, Net Yield & Latent Defects</h3>
              </div>

              <div style={{ background: 'rgba(14, 18, 28, 0.8)', border: '1px solid var(--border-subtle)', borderRadius: '12px', padding: '20px', marginBottom: '16px' }}>
                <p style={{ margin: '0 0 16px', fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                  <strong>Gate Acceptance vs. Net Usable Yield:</strong> Gate acceptance measures physical conformance at initial receiving inspection. Net Usable Yield accounts for subsequent physical returns, latent manufacturing defects, and signed quantity corrections. Financial-only credit/debit notes do not alter quality metrics.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
                  <MetricCard
                    label="Gate Acceptance Rate"
                    value={formatPercentage(selectedScorecard.gateAcceptanceRate)}
                    isNull={selectedScorecard.gateAcceptanceRate === null}
                    nullExplanation="No inspected receipts recorded"
                    tone={
                      selectedScorecard.gateAcceptanceRate === null ? 'neutral' :
                      selectedScorecard.gateAcceptanceRate >= 98 ? 'positive' :
                      selectedScorecard.gateAcceptanceRate >= 90 ? 'warning' : 'danger'
                    }
                    subValue="Accepted / Inspected at gate intake"
                    tooltip="Original accepted quantity divided by original inspected quantity across verified receipts."
                  />
                  <MetricCard
                    label="Net Usable Yield Rate"
                    value={formatPercentage(selectedScorecard.netUsableYieldRate)}
                    isNull={selectedScorecard.netUsableYieldRate === null}
                    nullExplanation="No inspected receipts recorded"
                    tone={
                      selectedScorecard.netUsableYieldRate === null ? 'neutral' :
                      selectedScorecard.netUsableYieldRate >= 98 ? 'positive' :
                      selectedScorecard.netUsableYieldRate >= 90 ? 'warning' : 'danger'
                    }
                    subValue="Effective usable yield post-returns"
                    tooltip="Net accepted quantity divided by net inspected quantity after verified physical returns and adjustments."
                  />
                  <MetricCard
                    label="Latent Defect Rate"
                    value={formatPercentage(selectedScorecard.latentDefectRate)}
                    isNull={selectedScorecard.latentDefectRate === null}
                    nullExplanation="No accepted material recorded"
                    tone={
                      selectedScorecard.latentDefectRate === null ? 'neutral' :
                      selectedScorecard.latentDefectRate === 0 ? 'positive' :
                      selectedScorecard.latentDefectRate <= 2 ? 'warning' : 'danger'
                    }
                    subValue="Post-acceptance physical return rate"
                    tooltip="Rate of previously accepted material subsequently identified as defective and returned via negative accepted adjustments."
                  />
                </div>
              </div>
            </div>

            {/* SECTION 3: LANDED COST & FINANCIAL RECONCILIATION */}
            <div style={{ marginBottom: '32px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '14px' }}>
                <span className="eyebrow" style={{ margin: 0 }}>COMMERCIAL RECONCILIATION</span>
                <h3 style={{ margin: 0, fontSize: '18px' }}>Economic Landed Cost & Baseline Variance</h3>
              </div>

              <div style={{ background: 'rgba(14, 18, 28, 0.8)', border: '1px solid var(--border-subtle)', borderRadius: '12px', padding: '20px', marginBottom: '16px' }}>
                <p style={{ margin: '0 0 16px', fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                  <strong>Landed Cost Variance:</strong> Compares actual landed costs (material base, uncredited freight, and verified debit/credit notes) against the authorized PO baseline. Positive variance indicates cost overruns; negative variance indicates commercial savings.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
                  <MetricCard
                    label="Landed Cost Variance (%)"
                    value={
                      selectedScorecard.costVariancePercentage !== null
                        ? `${selectedScorecard.costVariancePercentage > 0 ? '+' : ''}${formatPercentage(selectedScorecard.costVariancePercentage)}`
                        : '—'
                    }
                    isNull={selectedScorecard.costVariancePercentage === null}
                    nullExplanation="No reconciled invoices recorded"
                    tone={
                      selectedScorecard.costVariancePercentage === null ? 'neutral' :
                      selectedScorecard.costVariancePercentage < 0 ? 'positive' :
                      selectedScorecard.costVariancePercentage === 0 ? 'neutral' : 'danger'
                    }
                    subValue={
                      selectedScorecard.costVariancePercentage === null ? 'Awaiting invoice reconciliation' :
                      selectedScorecard.costVariancePercentage > 0 ? 'Cost exceeded authorized baseline' :
                      selectedScorecard.costVariancePercentage < 0 ? 'Cost lower than authorized baseline (savings)' :
                      'Landed cost matched authorized baseline'
                    }
                    tooltip="Variance percentage between verified actual landed cost and agreed baseline cost."
                  />
                  <MetricCard
                    label="Total Verified Landed Spend"
                    value={formatInrCurrency(selectedScorecard.totalActualLandedCost)}
                    isNull={selectedScorecard.totalActualLandedCost === null}
                    nullExplanation="No verified invoice reconciliation"
                    subValue="Net economic landed spend to date"
                    tooltip="Cumulative actual economic landed cost across reconciled invoices."
                  />
                </div>
              </div>
            </div>

            {/* SECTION 4: CONFIDENCE TIERS REFERENCE */}
            <div style={{ background: 'rgba(10, 12, 18, 0.7)', border: '1px solid var(--border-subtle)', borderRadius: '12px', padding: '20px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
                <IconShield style={{ color: '#a5b4fc' }} />
                <h4 style={{ margin: 0, fontSize: '14px', color: '#f8fafc' }}>
                  Understanding MUNIM Supplier Confidence Tiers
                </h4>
              </div>
              <p style={{ margin: '0 0 14px', fontSize: '12px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                Confidence tiers reflect the volume of verified historical evidence supporting this scorecard. High percentages on low confidence tiers represent early outcomes rather than proven statistical reliability.
              </p>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '10px' }}>
                <div style={{ padding: '10px 12px', background: 'rgba(16, 185, 129, 0.06)', border: '1px solid rgba(16, 185, 129, 0.2)', borderRadius: '8px' }}>
                  <span className="badge badge-approved" style={{ fontSize: '10px', marginBottom: '4px' }}>HIGH CONFIDENCE</span>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: '#f8fafc', marginTop: '4px' }}>≥ 8 Eligible POs</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>Statistically reliable performance baseline.</div>
                </div>

                <div style={{ padding: '10px 12px', background: 'rgba(99, 102, 241, 0.06)', border: '1px solid rgba(99, 102, 241, 0.2)', borderRadius: '8px' }}>
                  <span className="badge badge-confirmed" style={{ fontSize: '10px', marginBottom: '4px' }}>MEDIUM CONFIDENCE</span>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: '#f8fafc', marginTop: '4px' }}>3 to 7 Eligible POs</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>Emerging historical track record.</div>
                </div>

                <div style={{ padding: '10px 12px', background: 'rgba(245, 158, 11, 0.06)', border: '1px solid rgba(245, 158, 11, 0.2)', borderRadius: '8px' }}>
                  <span className="badge badge-pending" style={{ fontSize: '10px', marginBottom: '4px' }}>LOW CONFIDENCE</span>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: '#f8fafc', marginTop: '4px' }}>1 to 2 Eligible POs</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>Preliminary data. Treat metrics with caution.</div>
                </div>

                <div style={{ padding: '10px 12px', background: 'rgba(255, 255, 255, 0.03)', border: '1px solid rgba(255, 255, 255, 0.08)', borderRadius: '8px' }}>
                  <span className="badge badge-stale" style={{ fontSize: '10px', marginBottom: '4px' }}>UNRATED</span>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: '#f8fafc', marginTop: '4px' }}>0 Verified POs</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>No verified deliveries or receipts yet recorded.</div>
                </div>
              </div>
            </div>
          </div>
        )}
      </section>
    );
  }

  // ===========================================================================
  // RENDER: SUPPLIER PERFORMANCE LEADERBOARD DASHBOARD VIEW
  // ===========================================================================
  return (
    <section className="card" aria-label="Supplier Performance Leaderboard" style={{ marginTop: '16px' }}>
      {/* Page Header */}
      <div className="row" style={{ marginBottom: '20px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
            <span className="phase-pill">EMPIRICAL SUPPLIER INTELLIGENCE</span>
            {role === 'dev-evaluator' && (
              <span className="badge badge-stale" style={{ fontSize: '10px' }}>
                READ ONLY
              </span>
            )}
          </div>
          <h2 style={{ margin: '4px 0 6px', fontSize: '24px', fontWeight: 800 }}>Supplier Performance</h2>
          <p className="muted" style={{ margin: 0, fontSize: '13px' }}>
            Evaluate supplier delivery, quality, and cost performance using verified purchase outcomes.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {lastRefreshedAt && (
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
              Refreshed at {lastRefreshedAt}
            </span>
          )}
          <button
            type="button"
            className="btn-sm btn-outline"
            onClick={triggerRefresh}
            disabled={loading}
            title="Refresh scorecards from backend"
          >
            <IconRefresh className={loading ? 'spin' : ''} style={{ marginRight: '6px' }} /> Refresh
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <form onSubmit={handleApplyFilters} className="what-if-panel" style={{ marginTop: 0, marginBottom: '24px', padding: '16px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
          <span style={{ fontSize: '12px', fontWeight: 700, letterSpacing: '0.05em', color: '#a5b4fc', textTransform: 'uppercase', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <IconFilter /> Filter Leaderboard
          </span>
          {(materialFilter || fromDateFilter || toDateFilter) && (
            <span className="badge badge-confirmed" style={{ fontSize: '11px' }}>
              Filters Active
            </span>
          )}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', alignItems: 'flex-end' }}>
          <div>
            <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
              Material Filter
            </label>
            <select
              className="field-input"
              value={materialFilter}
              onChange={e => setMaterialFilter(e.target.value)}
              style={{ width: '100%', fontSize: '12px', padding: '8px 10px' }}
            >
              <option value="">All Materials</option>
              {materials.map((m, idx) => (
                <option key={m.id ? `${m.id}-${idx}` : `mat-${idx}`} value={m.id}>
                  {m.name} ({m.specification})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
              From Date
            </label>
            <input
              type="date"
              className="field-input"
              value={fromDateFilter}
              onChange={e => setFromDateFilter(e.target.value)}
              style={{ width: '100%', fontSize: '12px', padding: '8px 10px' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
              To Date
            </label>
            <input
              type="date"
              className="field-input"
              value={toDateFilter}
              onChange={e => setToDateFilter(e.target.value)}
              style={{ width: '100%', fontSize: '12px', padding: '8px 10px' }}
            />
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="submit" className="btn-sm btn-primary" disabled={loading} style={{ flex: 1 }}>
              Apply
            </button>
            {(materialFilter || fromDateFilter || toDateFilter) && (
              <button type="button" className="btn-sm btn-outline" onClick={handleClearFilters} disabled={loading}>
                Clear
              </button>
            )}
          </div>
        </div>

        {filterError && (
          <div style={{ marginTop: '10px', fontSize: '12px', color: '#f43f5e', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <IconAlertTriangle /> {filterError}
          </div>
        )}
      </form>

      {/* Overview Metrics Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', marginBottom: '24px' }}>
        <MetricCard
          label="Total Suppliers"
          value={`${overviewCounts.total}`}
          subValue="Active registered vendors"
          tooltip="Total active suppliers queried from empirical intelligence repository."
        />
        <MetricCard
          label="High Confidence"
          value={`${overviewCounts.high}`}
          tone="positive"
          subValue="≥ 8 verified POs"
          tooltip="Suppliers with 8 or more eligible purchase orders with verified receipt outcomes."
        />
        <MetricCard
          label="Medium Confidence"
          value={`${overviewCounts.medium}`}
          tone="neutral"
          subValue="3 to 7 verified POs"
          tooltip="Suppliers with 3 to 7 eligible purchase orders with verified receipt outcomes."
        />
        <MetricCard
          label="Low Confidence"
          value={`${overviewCounts.low}`}
          tone="warning"
          subValue="< 3 verified POs"
          tooltip="Suppliers with fewer than 3 eligible purchase orders."
        />
        <MetricCard
          label="Unrated"
          value={`${overviewCounts.unrated}`}
          subValue="No verified receipts"
          tooltip="Suppliers without any verified purchase order history."
        />
      </div>

      {/* Error Banner */}
      {error && (
        <div style={{ background: 'rgba(244, 63, 94, 0.1)', border: '1px solid rgba(244, 63, 94, 0.3)', borderRadius: '12px', padding: '16px 20px', marginBottom: '20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconAlertTriangle style={{ color: '#f43f5e', flexShrink: 0 }} />
            <span style={{ fontSize: '13px', color: '#f8fafc' }}>{error}</span>
          </div>
          <button type="button" className="btn-sm btn-primary" onClick={triggerRefresh}>
            Retry
          </button>
        </div>
      )}

      {/* Loading Indicator */}
      {loading && (
        <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--text-muted)' }}>
          <div className="spin" style={{ display: 'inline-block', width: '24px', height: '24px', border: '2px solid rgba(255,255,255,0.2)', borderTopColor: '#6366f1', borderRadius: '50%', marginBottom: '12px' }} />
          <div>Loading supplier performance rankings...</div>
        </div>
      )}

      {/* Leaderboard Table */}
      {!loading && !error && (
        <div>
          {scorecards.length === 0 ? (
            <div style={{ padding: '48px 24px', textAlign: 'center', background: 'rgba(10, 12, 18, 0.6)', border: '1px dashed var(--border-subtle)', borderRadius: '12px' }}>
              <IconInfo style={{ width: '32px', height: '32px', color: 'var(--text-dim)', marginBottom: '12px' }} />
              <h4 style={{ margin: '0 0 6px', color: '#f8fafc' }}>
                {materialFilter || fromDateFilter || toDateFilter
                  ? 'No Suppliers Match Filter Criteria'
                  : 'No Active Suppliers Found'}
              </h4>
              <p style={{ margin: '0 0 16px', fontSize: '13px', color: 'var(--text-muted)' }}>
                {materialFilter || fromDateFilter || toDateFilter
                  ? 'Try clearing or widening your material and date filters.'
                  : 'Add suppliers and record verified purchase outcomes to generate empirical scorecards.'}
              </p>
              {(materialFilter || fromDateFilter || toDateFilter) && (
                <button type="button" className="btn-sm btn-outline" onClick={handleClearFilters}>
                  Clear Filters
                </button>
              )}
            </div>
          ) : (
            <div>
              {/* Ranking Mode Callout */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px', fontSize: '12px', color: 'var(--text-secondary)' }}>
                <span>
                  {sortField === 'default'
                    ? 'Default Ranking: Confidence Tier (High → Low) · Contract OTD Rate · Supplier Name'
                    : `Sorted by: ${sortField.toUpperCase()} (${sortAsc ? 'Ascending' : 'Descending'})`}
                </span>
                {sortField !== 'default' && (
                  <button
                    type="button"
                    className="btn-sm btn-outline"
                    onClick={() => { setSortField('default'); setSortAsc(false); }}
                    style={{ fontSize: '11px', padding: '2px 8px' }}
                  >
                    Reset to Default Ranking
                  </button>
                )}
              </div>

              {/* Table Wrapper for Horizontal Scrolling on Small Screens */}
              <div style={{ overflowX: 'auto', border: '1px solid var(--border-subtle)', borderRadius: '12px', background: 'rgba(12, 15, 23, 0.9)' }}>
                <table className="comparison-table" style={{ margin: 0 }}>
                  <thead>
                    <tr>
                      <th style={{ cursor: 'pointer' }} onClick={() => toggleSort('name')} title="Click to sort by Name">
                        Supplier {sortField === 'name' ? (sortAsc ? '▲' : '▼') : ''}
                      </th>
                      <th>Confidence Tier</th>
                      <th style={{ cursor: 'pointer' }} onClick={() => toggleSort('evidence')} title="Click to sort by Evidence">
                        Evidence / Sample {sortField === 'evidence' ? (sortAsc ? '▲' : '▼') : ''}
                      </th>
                      <th style={{ cursor: 'pointer' }} onClick={() => toggleSort('otd')} title="Click to sort by Contract OTD">
                        Contract OTD {sortField === 'otd' ? (sortAsc ? '▲' : '▼') : ''}
                      </th>
                      <th>Avg PO Delay</th>
                      <th>Gate Acceptance</th>
                      <th style={{ cursor: 'pointer' }} onClick={() => toggleSort('yield')} title="Click to sort by Net Usable Yield">
                        Net Yield {sortField === 'yield' ? (sortAsc ? '▲' : '▼') : ''}
                      </th>
                      <th style={{ cursor: 'pointer' }} onClick={() => toggleSort('variance')} title="Click to sort by Landed Cost Variance">
                        Cost Variance {sortField === 'variance' ? (sortAsc ? '▲' : '▼') : ''}
                      </th>
                      <th style={{ textAlign: 'right' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sortedScorecards.map((sc, idx) => {
                      const isUnrated = sc.confidenceTier === 'UNRATED';
                      return (
                        <tr
                          key={sc.supplierId}
                          onClick={() => handleSelectSupplier(sc.supplierId)}
                          style={{
                            cursor: 'pointer',
                            opacity: isUnrated ? 0.75 : 1,
                            transition: 'background 0.15s ease'
                          }}
                        >
                          {/* Supplier Name */}
                          <td>
                            <div style={{ fontWeight: 600, color: '#f8fafc', fontSize: '13px' }}>
                              {sc.supplierName}
                            </div>
                            <div style={{ fontSize: '11px', color: 'var(--text-dim)', fontVariantNumeric: 'tabular-nums' }}>
                              Rank #{idx + 1}
                            </div>
                          </td>

                          {/* Confidence Badge */}
                          <td>
                            <SupplierConfidenceBadge tier={sc.confidenceTier} />
                          </td>

                          {/* Evidence Counts */}
                          <td>
                            <div style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600, color: '#f8fafc' }}>
                              {sc.eligiblePoCount} {sc.eligiblePoCount === 1 ? 'PO' : 'POs'}
                            </div>
                            <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontVariantNumeric: 'tabular-nums' }}>
                              {sc.consignmentCount} {sc.consignmentCount === 1 ? 'consignment' : 'consignments'}
                            </div>
                          </td>

                          {/* Contract OTD */}
                          <td>
                            <div style={{
                              fontVariantNumeric: 'tabular-nums',
                              fontWeight: 700,
                              color: sc.contractOtdRate === null ? 'var(--text-dim)' :
                                     sc.contractOtdRate >= 95 ? '#10b981' :
                                     sc.contractOtdRate >= 80 ? '#f59e0b' : '#f43f5e'
                            }}>
                              {formatPercentage(sc.contractOtdRate)}
                            </div>
                            <div style={{ fontSize: '11px', color: 'var(--text-dim)' }}>
                              {sc.contractOtdRate === null ? 'No eligible POs' : `${sc.onTimePoCount}/${sc.eligiblePoCount} on time`}
                            </div>
                          </td>

                          {/* Avg PO Delay */}
                          <td style={{ fontVariantNumeric: 'tabular-nums' }}>
                            {formatDays(sc.averagePoDelayDays)}
                          </td>

                          {/* Gate Acceptance */}
                          <td>
                            <div style={{
                              fontVariantNumeric: 'tabular-nums',
                              fontWeight: 600,
                              color: sc.gateAcceptanceRate === null ? 'var(--text-dim)' :
                                     sc.gateAcceptanceRate >= 98 ? '#10b981' :
                                     sc.gateAcceptanceRate >= 90 ? '#f59e0b' : '#f43f5e'
                            }}>
                              {formatPercentage(sc.gateAcceptanceRate)}
                            </div>
                          </td>

                          {/* Net Usable Yield */}
                          <td>
                            <div style={{
                              fontVariantNumeric: 'tabular-nums',
                              fontWeight: 600,
                              color: sc.netUsableYieldRate === null ? 'var(--text-dim)' :
                                     sc.netUsableYieldRate >= 98 ? '#10b981' :
                                     sc.netUsableYieldRate >= 90 ? '#f59e0b' : '#f43f5e'
                            }}>
                              {formatPercentage(sc.netUsableYieldRate)}
                            </div>
                          </td>

                          {/* Cost Variance */}
                          <td>
                            <div style={{
                              fontVariantNumeric: 'tabular-nums',
                              fontWeight: 600,
                              color: sc.costVariancePercentage === null ? 'var(--text-dim)' :
                                     sc.costVariancePercentage < 0 ? '#10b981' :
                                     sc.costVariancePercentage === 0 ? 'var(--text-secondary)' : '#f43f5e'
                            }}>
                              {sc.costVariancePercentage !== null
                                ? `${sc.costVariancePercentage > 0 ? '+' : ''}${formatPercentage(sc.costVariancePercentage)}`
                                : '—'}
                            </div>
                          </td>

                          {/* Action */}
                          <td style={{ textAlign: 'right' }}>
                            <button
                              type="button"
                              className="btn-sm btn-outline"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleSelectSupplier(sc.supplierId);
                              }}
                              style={{ fontSize: '11px', padding: '4px 10px' }}
                            >
                              Scorecard →
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
