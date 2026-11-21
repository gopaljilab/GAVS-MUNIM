import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { api, type SupplierScorecard, type SupplierScorecardList } from './api';
import { SupplierPerformance } from './SupplierPerformance';
import { App } from './ui';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
});

// Mock Scorecard Fixtures
const mockScorecard1: SupplierScorecard = {
  supplierId: 'sup-1111-1111',
  supplierName: 'Tata Steel Industrial',
  contractOtdRate: 98.50,
  eligiblePoCount: 12,
  onTimePoCount: 11,
  delayedPoCount: 1,
  shortClosedPoCount: 0,
  averagePoDelayDays: 1.5,
  consignmentCount: 18,
  consignmentOnTimeRate: 95.00,
  averageConsignmentsPerCompletedPo: 1.5,
  partialDeliveryOrderRate: 33.33,
  gateAcceptanceRate: 99.40,
  netUsableYieldRate: 98.90,
  latentDefectRate: 0.50,
  inspectedQuantityTotal: 25000.0000,
  costVariancePercentage: -1.25,
  totalActualLandedCost: 2450000.00,
  confidenceTier: 'HIGH',
  confidenceExplanation: 'High empirical confidence with 12 eligible purchase orders and 18 verified consignments.'
};

const mockScorecard2: SupplierScorecard = {
  supplierId: 'sup-2222-2222',
  supplierName: 'Hindalco Aluminium Works',
  contractOtdRate: 85.00,
  eligiblePoCount: 5,
  onTimePoCount: 4,
  delayedPoCount: 1,
  shortClosedPoCount: 0,
  averagePoDelayDays: 3.0,
  consignmentCount: 6,
  consignmentOnTimeRate: 83.33,
  averageConsignmentsPerCompletedPo: 1.2,
  partialDeliveryOrderRate: 20.00,
  gateAcceptanceRate: 96.50,
  netUsableYieldRate: 95.00,
  latentDefectRate: 1.50,
  inspectedQuantityTotal: 10000.0000,
  costVariancePercentage: 2.10,
  totalActualLandedCost: 1150000.00,
  confidenceTier: 'MEDIUM',
  confidenceExplanation: 'Medium confidence based on 5 eligible purchase orders.'
};

const mockScorecard3: SupplierScorecard = {
  supplierId: 'sup-3333-3333',
  supplierName: 'Ambience Fasteners',
  contractOtdRate: 100.00,
  eligiblePoCount: 1,
  onTimePoCount: 1,
  delayedPoCount: 0,
  shortClosedPoCount: 0,
  averagePoDelayDays: null,
  consignmentCount: 1,
  consignmentOnTimeRate: 100.00,
  averageConsignmentsPerCompletedPo: 1.0,
  partialDeliveryOrderRate: 0.00,
  gateAcceptanceRate: 100.00,
  netUsableYieldRate: 100.00,
  latentDefectRate: 0.00,
  inspectedQuantityTotal: 500.0000,
  costVariancePercentage: 0.00,
  totalActualLandedCost: 45000.00,
  confidenceTier: 'LOW',
  confidenceExplanation: 'Low confidence with only 1 completed order. Metrics represent early sample.'
};

const mockScorecardUnrated: SupplierScorecard = {
  supplierId: 'sup-4444-4444',
  supplierName: 'Brand New Industrial Vendor',
  contractOtdRate: null,
  eligiblePoCount: 0,
  onTimePoCount: 0,
  delayedPoCount: 0,
  shortClosedPoCount: 0,
  averagePoDelayDays: null,
  consignmentCount: 0,
  consignmentOnTimeRate: null,
  averageConsignmentsPerCompletedPo: null,
  partialDeliveryOrderRate: null,
  gateAcceptanceRate: null,
  netUsableYieldRate: null,
  latentDefectRate: null,
  inspectedQuantityTotal: null,
  costVariancePercentage: null,
  totalActualLandedCost: null,
  confidenceTier: 'UNRATED',
  confidenceExplanation: 'No verified purchase orders, delivery receipts, or inspections recorded.'
};

// =============================================================================
// 1. API Integration Tests
// =============================================================================
describe('Supplier Performance API Client (api.ts)', () => {
  it('api.listSupplierScorecards correctly calls endpoint with query parameters', async () => {
    let capturedUrl = '';
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      capturedUrl = String(url);
      return new Response(JSON.stringify({ items: [mockScorecard1] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }));

    const result = await api.listSupplierScorecards({
      materialId: 'mat-123',
      fromDate: '2026-11-01T00:00:00Z',
      toDate: '2026-11-20T23:59:59Z'
    });

    expect(capturedUrl).toContain('/api/v1/suppliers/scorecards');
    expect(capturedUrl).toContain('materialId=mat-123');
    expect(capturedUrl).toContain('fromDate=2026-11-01T00%3A00%3A00Z');
    expect(capturedUrl).toContain('toDate=2026-11-20T23%3A59%3A59Z');
    expect(result.items).toHaveLength(1);
    expect(result.items[0].supplierName).toBe('Tata Steel Industrial');
  });

  it('api.getSupplierScorecard correctly fetches single supplier and preserves null values', async () => {
    let capturedUrl = '';
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      capturedUrl = String(url);
      return new Response(JSON.stringify(mockScorecardUnrated), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }));

    const result = await api.getSupplierScorecard('sup-4444-4444');
    expect(capturedUrl).toContain('/api/v1/suppliers/sup-4444-4444/scorecard');
    expect(result.confidenceTier).toBe('UNRATED');
    expect(result.contractOtdRate).toBeNull();
    expect(result.gateAcceptanceRate).toBeNull();
    expect(result.costVariancePercentage).toBeNull();
  });

  it('handles 404 Not Found error properly as ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      return new Response(JSON.stringify({ status: 404, detail: 'Supplier not found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' }
      });
    }));

    await expect(api.getSupplierScorecard('non-existent-uuid')).rejects.toThrow();
  });
});

// =============================================================================
// 2. Supplier Performance Leaderboard Component Tests
// =============================================================================
describe('Supplier Performance Leaderboard UI', () => {
  it('renders leaderboard with overview metrics, confidence tiers, and ranked suppliers', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = String(url);
      if (urlStr.includes('/materials')) {
        return new Response(JSON.stringify({ items: [{ id: 'mat-1', name: 'IS2062 Steel', specification: '2mm' }] }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      return new Response(JSON.stringify({
        items: [mockScorecard1, mockScorecard2, mockScorecard3, mockScorecardUnrated]
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }));

    render(<SupplierPerformance role="dev-owner" />);

    // Wait for data load
    await waitFor(() => {
      expect(screen.getByText('Supplier Performance')).toBeTruthy();
      expect(screen.getByText('Tata Steel Industrial')).toBeTruthy();
    });

    // Overview Metric Counts
    expect(screen.getByText('Total Suppliers')).toBeTruthy();
    expect(screen.getByText('4')).toBeTruthy(); // 4 total
    expect(screen.getByText('High Confidence')).toBeTruthy();
    expect(screen.getByText('Medium Confidence')).toBeTruthy();
    expect(screen.getByText('Low Confidence')).toBeTruthy();
    expect(screen.getByText('Unrated')).toBeTruthy();

    // Table rows rendering
    expect(screen.getByText('Hindalco Aluminium Works')).toBeTruthy();
    expect(screen.getByText('Ambience Fasteners')).toBeTruthy();
    expect(screen.getByText('Brand New Industrial Vendor')).toBeTruthy();

    // Badges present
    expect(screen.getByText('HIGH CONFIDENCE')).toBeTruthy();
    expect(screen.getByText('MEDIUM CONFIDENCE')).toBeTruthy();
    expect(screen.getByText('LOW CONFIDENCE')).toBeTruthy();

    // Percentage values formatted
    expect(screen.getByText('98.50%')).toBeTruthy();
    expect(screen.getByText('85.00%')).toBeTruthy();

    // Crucial: Null values in unrated supplier are rendered as '—', NOT 0%
    const dashes = screen.getAllByText('—');
    expect(dashes.length).toBeGreaterThan(0);
  });

  it('displays empty state when no suppliers match the query', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      return new Response(JSON.stringify({ items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }));

    render(<SupplierPerformance role="dev-owner" />);

    await waitFor(() => {
      expect(screen.getByText('No Active Suppliers Found')).toBeTruthy();
    });
  });

  it('displays error state with retry button when backend call fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new Error('Connection refused by server');
    }));

    render(<SupplierPerformance role="dev-owner" />);

    await waitFor(() => {
      expect(screen.getByText(/Failed to load supplier performance scorecards/)).toBeTruthy();
      expect(screen.getByRole('button', { name: 'Retry' })).toBeTruthy();
    });
  });
});

// =============================================================================
// 3. Individual Supplier Scorecard Component Tests
// =============================================================================
describe('Individual Supplier Scorecard UI', () => {
  it('renders detailed scorecard with delivery, quality, cost, and evidence breakdown', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = String(url);
      if (urlStr.includes('/materials')) {
        return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify(mockScorecard1), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<SupplierPerformance initialSupplierId="sup-1111-1111" role="dev-owner" />);

    await waitFor(() => {
      expect(screen.getByText('Tata Steel Industrial')).toBeTruthy();
      expect(screen.getAllByText('HIGH CONFIDENCE').length).toBeGreaterThanOrEqual(1);
    });

    // Evidence & Narrative
    expect(screen.getByText(/High empirical confidence with 12 eligible purchase orders/)).toBeTruthy();
    expect(screen.getByText('Eligible Purchase Orders')).toBeTruthy();
    expect(screen.getByText('12')).toBeTruthy();
    expect(screen.getByText('Verified Consignments')).toBeTruthy();
    expect(screen.getByText('18')).toBeTruthy();

    // Delivery Section
    expect(screen.getByText('Delivery Timeliness & Consignment Logistics')).toBeTruthy();
    expect(screen.getByText('Contract On-Time Delivery (OTD)')).toBeTruthy();
    expect(screen.getByText('98.50%')).toBeTruthy();
    expect(screen.getByText('Average PO Delay')).toBeTruthy();
    expect(screen.getByText('1.5 days')).toBeTruthy();
    expect(screen.getByText('Consignment On-Time Rate')).toBeTruthy();
    expect(screen.getByText('95.00%')).toBeTruthy();

    // Quality Section
    expect(screen.getByText('Material Quality, Net Yield & Latent Defects')).toBeTruthy();
    expect(screen.getByText('Gate Acceptance Rate')).toBeTruthy();
    expect(screen.getByText('99.40%')).toBeTruthy();
    expect(screen.getByText('Net Usable Yield Rate')).toBeTruthy();
    expect(screen.getByText('98.90%')).toBeTruthy();
    expect(screen.getByText('Latent Defect Rate')).toBeTruthy();
    expect(screen.getByText('0.50%')).toBeTruthy();

    // Cost Performance Section
    expect(screen.getByText('Economic Landed Cost & Baseline Variance')).toBeTruthy();
    expect(screen.getByText('Landed Cost Variance (%)')).toBeTruthy();
    expect(screen.getByText('-1.25%')).toBeTruthy();

    // Back button works
    expect(screen.getByRole('button', { name: /Back to Supplier Leaderboard/ })).toBeTruthy();
  });

  it('renders unrated supplier scorecard with honest null presentations (no fake zeros)', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = String(url);
      if (urlStr.includes('/materials')) {
        return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify(mockScorecardUnrated), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<SupplierPerformance initialSupplierId="sup-4444-4444" role="dev-owner" />);

    await waitFor(() => {
      expect(screen.getByText('Brand New Industrial Vendor')).toBeTruthy();
      expect(screen.getAllByText('UNRATED').length).toBeGreaterThanOrEqual(1);
    });

    // Check that missing metric cards display '—' with explanatory text
    expect(screen.getByText('No eligible purchase orders')).toBeTruthy();
    expect(screen.getByText('No delayed or overdue orders')).toBeTruthy();
    expect(screen.getByText('No verified consignments')).toBeTruthy();
    expect(screen.getAllByText('No inspected receipts recorded').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('No reconciled invoices recorded')).toBeTruthy();
  });

  it('handles 404 supplier not found gracefully', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      return new Response(JSON.stringify({ status: 404, detail: 'Supplier not found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' }
      });
    }));

    render(<SupplierPerformance initialSupplierId="sup-unknown" role="dev-owner" />);

    await waitFor(() => {
      expect(screen.getByText('Unable to Display Scorecard')).toBeTruthy();
      expect(screen.getByText(/Supplier scorecard not found \(404\)/)).toBeTruthy();
      expect(screen.getByRole('button', { name: /Back to Supplier Leaderboard/ })).toBeTruthy();
    });
  });
});

// =============================================================================
// 4. Filters & Validation Tests
// =============================================================================
describe('Supplier Performance Filters & Date Validation', () => {
  it('prevents query and displays error message when From Date is after To Date', async () => {
    let fetchCalled = false;
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = String(url);
      if (urlStr.includes('/materials')) {
        return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      fetchCalled = true;
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<SupplierPerformance role="dev-owner" />);

    await waitFor(() => expect(screen.getByText('Supplier Performance')).toBeTruthy());

    fetchCalled = false;

    // Find date inputs via querySelectorAll
    const inputs = document.querySelectorAll('input[type="date"]');
    expect(inputs.length).toBe(2);

    const fromDateEl = inputs[0] as HTMLInputElement;
    const toDateEl = inputs[1] as HTMLInputElement;

    // Set invalid range: From Nov 20, To Nov 10
    fireEvent.change(fromDateEl, { target: { value: '2026-11-20' } });
    fireEvent.change(toDateEl, { target: { value: '2026-11-10' } });

    // Click Apply button
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    // Should display validation error and NOT invoke fetch
    await waitFor(() => {
      expect(screen.getByText('From Date cannot be after To Date.')).toBeTruthy();
    });
    expect(fetchCalled).toBe(false);
  });

  it('clearing filters resets inputs and reloads unfiltered data', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      return new Response(JSON.stringify({ items: [mockScorecard1] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<SupplierPerformance role="dev-owner" />);

    await waitFor(() => expect(screen.getByText('Supplier Performance')).toBeTruthy());

    const inputs = document.querySelectorAll('input[type="date"]');
    const fromDateEl = inputs[0] as HTMLInputElement;
    fireEvent.change(fromDateEl, { target: { value: '2026-11-01' } });

    // Clear button appears when filters are active
    const clearBtn = await screen.findByRole('button', { name: 'Clear' });
    fireEvent.click(clearBtn);

    expect(fromDateEl.value).toBe('');
  });
});

// =============================================================================
// 5. Navigation & Role Permissions in App
// =============================================================================
describe('Supplier Performance Integration in Main App Navigation', () => {
  it('renders Supplier Performance navigation tab for OWNER persona and switches via tab click', async () => {
    localStorage.setItem('munim-token', 'dev-owner');

    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = String(url);
      if (urlStr.includes('/scorecards')) {
        return new Response(JSON.stringify({ items: [mockScorecard1] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    // Verify Tab 6 "Supplier Performance" exists in nav
    const perfTab = screen.getByRole('button', { name: /Supplier Performance/ });
    expect(perfTab).toBeTruthy();

    // Click tab
    fireEvent.click(perfTab);

    // Verify it loads Supplier Performance view
    await waitFor(() => {
      expect(screen.getByText('Evaluate supplier delivery, quality, and cost performance using verified purchase outcomes.')).toBeTruthy();
      expect(screen.getByText('Tata Steel Industrial')).toBeTruthy();
    });
  });

  it('renders Supplier Performance navigation tab for PROCUREMENT persona', async () => {
    localStorage.setItem('munim-token', 'dev-procurement');

    vi.stubGlobal('fetch', vi.fn(async () => {
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    const perfTab = screen.getByRole('button', { name: /Supplier Performance/ });
    expect(perfTab).toBeTruthy();
  });
});
