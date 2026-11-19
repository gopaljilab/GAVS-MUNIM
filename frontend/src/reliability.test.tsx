import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from './ui';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('Reliability, Error Handling, and Audit Quality Regression Suite', () => {
  // Test 1: API unavailable / network error display
  it('displays non-crashing network error banner with retry button when backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/approvals')) {
        throw new TypeError('Failed to fetch');
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    // Switch to Approvals tab
    fireEvent.click(screen.getByRole('button', { name: /Approvals/ }));

    await waitFor(() => {
      expect(screen.getByText(/Failed to load proposals:/)).toBeTruthy();
    });

    expect(screen.getByText(/Network connectivity failure: backend unreachable/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeTruthy();
  });

  // Test 2: Unauthorized (401) and Forbidden (403) handling
  it('handles 403 Forbidden with descriptive error details and non-owner notice', async () => {
    localStorage.setItem('munim-token', 'dev-evaluator');

    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/approvals')) {
        return new Response(
          JSON.stringify({
            type: 'about:blank',
            title: 'Forbidden',
            status: 403,
            code: 'FORBIDDEN',
            detail: 'Insufficient persona permissions'
          }),
          { status: 403, headers: { 'Content-Type': 'application/problem+json' } }
        );
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Approvals/ }));

    await waitFor(() => {
      expect(screen.getByText(/Access forbidden \(403\): Insufficient persona permissions/)).toBeTruthy();
    });

    // Check non-owner informational notice banner
    expect(screen.getByText(/Read-Only Mode:/)).toBeTruthy();
    expect(screen.getAllByText(/dev-evaluator/).length).toBeGreaterThan(0);
  });

  // Test 3: Validation error (422) handling
  it('handles validation errors and prevents invalid operations with explicit guidance', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/customer-enquiries') && !urlStr.includes('/boms')) {
        return new Response(
          JSON.stringify({
            id: 'enq-1',
            customerName: 'Test Corp',
            requiredDeliveryDate: new Date().toISOString(),
            status: 'OPEN'
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      if (urlStr.includes('/boms')) {
        return new Response(
          JSON.stringify({ id: 'bom-1', bomVersion: 1, batchQuantity: '100.0000', items: [] }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      if (urlStr.includes('/materials')) {
        return new Response(
          JSON.stringify({ items: [{ id: 'mat-1', name: 'IS 2062 MS Sheet' }] }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    // Switch to Commercial tab
    fireEvent.click(screen.getByRole('button', { name: /Commercial/ }));
    fireEvent.click(screen.getByRole('button', { name: /Create Customer Enquiry & Initialize BOM/ }));

    // Wait for BOM line items section to appear
    await waitFor(() => expect(screen.getByText('Add Material Item to BOM')).toBeTruthy());

    // Switch sourcing basis to AWARDED_QUOTE when no quote is active
    const select = screen.getByDisplayValue(/ESTIMATE/);
    fireEvent.change(select, { target: { value: 'AWARDED_QUOTE' } });

    // Warning banner should be rendered
    expect(screen.getByText(/No Awarded Quote Active:/)).toBeTruthy();

    // Trying to add item with AWARDED_QUOTE without active quote throws validation error
    fireEvent.click(screen.getByRole('button', { name: 'Add Item to BOM' }));
    await waitFor(() => {
      expect(screen.getByText(/Cannot link awarded quote: No awarded quote revision found in current session/)).toBeTruthy();
    });
  });

  // Test 4: State conflict (409) handling
  it('handles 409 snapshot conflict with clear conflict message', async () => {
    const prop = { id: 'prop-stale', type: 'CUSTOMER_QUOTE', status: 'PENDING', snapshotHash: 'stale-hash' };
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/decision')) {
        return new Response(
          JSON.stringify({
            status: 409,
            code: 'SNAPSHOT_HASH_MISMATCH',
            detail: 'Cryptographic snapshot hash mismatch. Proposal state has changed concurrently.'
          }),
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } }
        );
      }
      if (urlStr.includes('/approvals/prop-stale')) {
        return new Response(JSON.stringify({ proposal: prop, decisions: [], snapshot: { totalCost: '50000' } }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/approvals')) {
        return new Response(JSON.stringify({ items: [{ proposal: prop, summary: { customerName: 'Stale Customer' } }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Approvals/ }));
    await waitFor(() => expect(screen.getByText('Stale Customer')).toBeTruthy());

    // Click Approve
    fireEvent.click(screen.getByRole('button', { name: 'Approve as Owner' }));

    await waitFor(() => {
      expect(screen.getByText(/State conflict \(409\): Cryptographic snapshot hash mismatch/)).toBeTruthy();
    });
  });

  // Test 5: Dashboard metric consistency (zero breaches = '0 Alerts' and green compliant)
  it('renders 0 Alerts and compliant status in Dashboard when all margins satisfy floor', async () => {
    const queueItems = [
      {
        proposal: { id: 'p1', type: 'CUSTOMER_QUOTE', status: 'PENDING', riskFlag: null },
        summary: { customerName: 'Alpha Corp', marginPercent: '24.50' }
      },
      {
        proposal: { id: 'p2', type: 'CUSTOMER_QUOTE', status: 'APPROVED', riskFlag: null },
        summary: { customerName: 'Beta Ltd', marginPercent: '20.00' }
      }
    ];

    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/approvals')) {
        return new Response(JSON.stringify({ items: queueItems }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Dashboard/ }));
    await waitFor(() => expect(screen.getByText('MUNIM Business Operations Dashboard')).toBeTruthy());

    // Alert badge should be 0 Alerts
    expect(screen.getByText('0 Alerts')).toBeTruthy();
    // Compliant message should be visible
    expect(screen.getByText('Compliant: Zero active margin breaches')).toBeTruthy();
    expect(screen.getByText(/All costed proposals satisfy the mandatory 10% financial floor policy/)).toBeTruthy();
  });

  // Test 6: Active enquiry count (WON and LOST excluded)
  it('excludes WON and LOST enquiries from active enquiry count', async () => {
    const mockEnquiries = [
      { id: '1', customerName: 'Active 1', status: 'OPEN', requiredDeliveryDate: new Date().toISOString() },
      { id: '2', customerName: 'Active 2', status: 'COSTED', requiredDeliveryDate: new Date().toISOString() },
      { id: '3', customerName: 'Won Client', status: 'WON', requiredDeliveryDate: new Date().toISOString() },
      { id: '4', customerName: 'Lost Client', status: 'LOST', requiredDeliveryDate: new Date().toISOString() }
    ];

    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/customer-enquiries')) {
        return new Response(JSON.stringify({ items: mockEnquiries }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Dashboard/ }));
    await waitFor(() => expect(screen.getByText('MUNIM Business Operations Dashboard')).toBeTruthy());

    expect(screen.getByText('2 Active')).toBeTruthy();
    expect(screen.getByText('4 Total Registered')).toBeTruthy();
  });

  // Test 7: Empty approval queue states
  it('displays filter-aware empty states in Approvals Gateway', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Approvals/ }));
    await waitFor(() => expect(screen.getByText('Commercial Approvals Gateway')).toBeTruthy());

    // By default, filter is PENDING -> should show "No proposals matching filter PENDING"
    expect(screen.getByText('No proposals matching filter "PENDING".')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'View All Proposals' })).toBeTruthy();

    // Click ALL filter
    fireEvent.click(screen.getByRole('button', { name: 'ALL' }));
    await waitFor(() => {
      expect(screen.getByText(/No commercial proposals have been submitted yet/)).toBeTruthy();
      expect(screen.getByRole('button', { name: 'Go to Guided Demo' })).toBeTruthy();
    });
  });

  // Test 8: Guided Demo preflight failure
  it('displays preflight failure banner when backend is reported DOWN', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/actuator/health')) {
        return new Response(JSON.stringify({ status: 'DOWN' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Guided Demo/ }));
    expect(screen.getByText('Guided End-to-End Demonstration')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Run Full Guided Demonstration/ }));

    await waitFor(() => {
      expect(screen.getByText(/Step 1 Execution Interrupted/)).toBeTruthy();
      expect(screen.getAllByText(/Preflight Failure/).length).toBeGreaterThan(0);
    });
  });

  // Test 9: Guided Demo step failure and retry without resetting completed steps
  it('handles step execution failure, renders retry button, and does not reset completed steps', async () => {
    let addQuoteAttempts = 0;

    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      const method = init?.method || 'GET';

      if (urlStr.includes('/actuator/health')) {
        return new Response(JSON.stringify({ status: 'UP' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/demo/seed')) {
        return new Response(JSON.stringify({ status: 'SUCCESS', message: 'Demo data seeded' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/materials')) {
        return new Response(JSON.stringify({ items: [{ id: 'mat-1', name: 'IS 2062 MS Sheet' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/suppliers')) {
        return new Response(JSON.stringify({ id: 'sup-1', name: 'Synthetic Supplier' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/rfqs') && method === 'POST') {
        return new Response(JSON.stringify({ id: 'rfq-1', materialId: 'mat-1', status: 'OPEN' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/quotes') && method === 'POST') {
        addQuoteAttempts++;
        if (addQuoteAttempts === 1) {
          // Fail on first attempt to test Step 2 error recovery
          return new Response(JSON.stringify({
            status: 500,
            code: 'INTERNAL_ERROR',
            detail: 'Simulated temporary failure on supplier quote ingestion'
          }), { status: 500, headers: { 'Content-Type': 'application/problem+json' } });
        }
        return new Response(JSON.stringify({ id: 'quote-1', revision: 1, revisionId: 'rev-1' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Guided Demo/ }));
    fireEvent.click(screen.getByRole('button', { name: /Run Full Guided Demonstration/ }));

    // Wait for Step 2 execution failure banner
    await waitFor(() => {
      expect(screen.getByText(/Step 2 Execution Interrupted/)).toBeTruthy();
    });

    // Verify Step 1 remains marked completed
    expect(screen.getByText('✓ 1. RFQ Creation')).toBeTruthy();
    // Verify Step 2 is marked failed
    expect(screen.getByText('⚠ 2. AI Extraction (Failed)')).toBeTruthy();

    // Verify retry button exists and is labeled for step 2
    const retryBtn = screen.getByRole('button', { name: '🔄 Retry Step 2' });
    expect(retryBtn).toBeTruthy();

    const restartBtn = screen.getByRole('button', { name: '↺ Restart from Step 1' });
    expect(restartBtn).toBeTruthy();
  });

  // Test 10: Synthetic data indicators
  it('renders global synthetic demonstration indicators and banners across UI', () => {
    render(<App />);

    // Global banner is present
    expect(screen.getByText(/Synthetic Demonstration Environment:/)).toBeTruthy();
    expect(screen.getByText('DEV / AUDIT READY')).toBeTruthy();

    // Header badge is present
    expect(screen.getByText('SYNTHETIC DEMO DATA')).toBeTruthy();

    // Preset chips exist
    expect(screen.getByRole('button', { name: /Clean Quote/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Messy Hinglish/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Prompt Injection Attempt/ })).toBeTruthy();
  });

  // Test 11: Financial correctness — Arithmetic Mean vs Aggregate Realized Margin
  it('correctly calculates both arithmetic mean and aggregate financial margin for differently sized quotations', async () => {
    // Quote A: Small job (₹10,000 selling price, ₹3,000 profit -> 30% margin)
    // Quote B: Large factory contract (₹1,000,000 selling price, ₹100,000 profit -> 10% margin)
    // Arithmetic Mean: (30% + 10%) / 2 = 20.0%
    // Aggregate Financial Margin: (103,000 / 1,010,000) * 100 = 10.198% ≈ 10.2%
    const queueItems = [
      {
        proposal: { id: 'p1', type: 'CUSTOMER_QUOTE', status: 'PENDING', riskFlag: null },
        summary: {
          customerName: 'Small Batch Customer',
          marginPercent: '30.00',
          sellingPrice: '10000.00',
          profit: '3000.00'
        }
      },
      {
        proposal: { id: 'p2', type: 'CUSTOMER_QUOTE', status: 'APPROVED', riskFlag: null },
        summary: {
          customerName: 'Heavy Industrial Client',
          marginPercent: '10.00',
          sellingPrice: '1000000.00',
          profit: '100000.00'
        }
      },
      // Non-customer quote or rejected quote should be excluded from margin calculation
      {
        proposal: { id: 'p3', type: 'SUPPLIER_AWARD', status: 'APPROVED' },
        summary: { marginPercent: '50.00', sellingPrice: '50000.00', profit: '25000.00' }
      },
      {
        proposal: { id: 'p4', type: 'CUSTOMER_QUOTE', status: 'REJECTED' },
        summary: { marginPercent: '5.00', sellingPrice: '20000.00', profit: '1000.00' }
      }
    ];

    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/approvals')) {
        return new Response(JSON.stringify({ items: queueItems }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Dashboard/ }));
    await waitFor(() => expect(screen.getByText('Quotation Margin (Arithmetic Mean)')).toBeTruthy());

    // Arithmetic mean must be 20.0%
    expect(screen.getByText('20.0%')).toBeTruthy();

    // Aggregate financial companion must be 10.2%
    expect(screen.getByText('Target ≥ 20% | Aggregate: 10.2%')).toBeTruthy();
  });

  // Test 12: Database readiness preflight check — Halts when PostgreSQL is DOWN
  it('halts Guided Demo before creating synthetic resources when database is DOWN', async () => {
    let seedCalled = false;
    let rfqCreated = false;

    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      const method = init?.method || 'GET';

      if (urlStr.includes('/actuator/health')) {
        return new Response(
          JSON.stringify({
            status: 'UP',
            components: {
              db: { status: 'DOWN' },
              diskSpace: { status: 'UP' }
            }
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      if (urlStr.includes('/demo/seed')) {
        seedCalled = true;
        return new Response(JSON.stringify({ status: 'SUCCESS' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/rfqs') && method === 'POST') {
        rfqCreated = true;
        return new Response(JSON.stringify({ id: 'rfq-unwanted' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Guided Demo/ }));
    expect(screen.getByText('Guided End-to-End Demonstration')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Run Full Guided Demonstration/ }));

    await waitFor(() => {
      expect(screen.getByText(/Step 1 Execution Interrupted/)).toBeTruthy();
      expect(screen.getAllByText(/Database Readiness Failure: PostgreSQL status is reported as "DOWN"/).length).toBeGreaterThan(0);
    });

    // Halting verified: No synthetic demo data was seeded or created
    expect(seedCalled).toBe(false);
    expect(rfqCreated).toBe(false);
  });

  // Test 13: Preflight check — Halts when Backend itself is DOWN
  it('halts Guided Demo preflight with clear connectivity guidance when backend is DOWN', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      if (urlStr.includes('/actuator/health')) {
        return new Response(JSON.stringify({ status: 'DOWN' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Guided Demo/ }));
    expect(screen.getByText('Guided End-to-End Demonstration')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Run Full Guided Demonstration/ }));

    await waitFor(() => {
      expect(screen.getByText(/Step 1 Execution Interrupted/)).toBeTruthy();
      expect(screen.getAllByText(/Backend status reported: DOWN/).length).toBeGreaterThan(0);
    });
  });

  // Test 14: Guided Demo session persistence and resumption without duplicate resource creation
  it('persists demo session in localStorage and resumes without recreating existing resources', async () => {
    // Simulate browser refresh after Step 4 was completed
    const savedSession = {
      step: 5,
      failedStep: null,
      resources: {
        rfqId: 'rfq-existing-123',
        supplierApexId: 'sup-apex-123',
        supplierMahalaxmiId: 'sup-maha-123',
        mahaQuoteRevId: 'rev-maha-123',
        apexQuoteRevId: 'rev-apex-123',
        enquiryId: 'enq-existing-456'
      },
      logs: ['[12:00:00] Step 4 Complete: Customer Enquiry created.'],
      savings: 7780,
      scrap: null,
      margin: null
    };
    localStorage.setItem('munim_demo_session_v1', JSON.stringify(savedSession));

    let createEnquiryCalls = 0;
    let createBomCalls = 0;

    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      const urlStr = typeof url === 'string' ? url : (url as Request).url;
      const method = init?.method || 'GET';

      if (urlStr.includes('/actuator/health')) {
        return new Response(JSON.stringify({ status: 'UP', components: { db: { status: 'UP' } } }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/materials')) {
        return new Response(JSON.stringify({ items: [{ id: 'mat-1', name: 'IS 2062 MS Sheet' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/customer-enquiries') && !urlStr.includes('/boms') && method === 'POST') {
        createEnquiryCalls++;
        return new Response(JSON.stringify({ id: 'enq-duplicate' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/boms') && method === 'POST' && !urlStr.includes('/costings') && !urlStr.includes('/items')) {
        createBomCalls++;
        return new Response(JSON.stringify({ id: 'bom-resumed-789', bomVersion: 1, batchQuantity: '100.0000', items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/items') && method === 'POST') {
        return new Response(JSON.stringify({ id: 'item-1', partName: 'Armoured Chassis Bracket 5mm' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/boms/bom-resumed-789') && method === 'GET') {
        return new Response(JSON.stringify({ id: 'bom-resumed-789', items: [{ id: 'item-1' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/costings') && method === 'POST') {
        return new Response(JSON.stringify({
          id: 'cost-resumed-1',
          totalCost: '50000.00',
          targetPrice: '62500.00',
          targetMargin: '0.2000',
          marginClassification: 'HEALTHY',
          snapshotSha256: 'sha-cost-123'
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/customer-quotations') && method === 'POST') {
        return new Response(JSON.stringify({
          id: 'quote-resumed-1',
          sellingPrice: '62500.00',
          customerPayable: '73750.00',
          approvalStatus: 'PENDING'
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/submit-approval')) {
        return new Response(JSON.stringify({
          id: 'prop-resumed-1',
          snapshotHash: 'sha-cost-123',
          status: 'PENDING'
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/decision')) {
        return new Response(JSON.stringify({ id: 'prop-resumed-1', decision: 'APPROVE', status: 'APPROVED' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (urlStr.includes('/customer-quotations/quote-resumed-1')) {
        return new Response(JSON.stringify({ id: 'quote-resumed-1', approvalStatus: 'APPROVED' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /Guided Demo/ }));
    await waitFor(() => expect(screen.getByRole('button', { name: '▶ Resume Demonstration (from Step 5)' })).toBeTruthy());

    // Click Resume
    fireEvent.click(screen.getByRole('button', { name: '▶ Resume Demonstration (from Step 5)' }));

    await waitFor(() => {
      expect(screen.getByText(/Full 7-stage workflow verified end-to-end with owner cryptographic audit trail/)).toBeTruthy();
    });

    // Verified: Step 4 was skipped/reused from saved session; no duplicate enquiry was created!
    expect(createEnquiryCalls).toBe(0);
    // BOM and subsequent steps were executed
    expect(createBomCalls).toBe(1);
  });
});
