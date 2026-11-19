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
});
