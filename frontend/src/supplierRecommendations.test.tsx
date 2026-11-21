import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { api, type SupplierRecommendationList } from './api';
import { SupplierRecommendations } from './SupplierRecommendations';
import { App } from './ui';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
});

const mockRecommendationsData: SupplierRecommendationList = {
  rfqId: 'rfq-1111-1111',
  rfqMaterialName: 'IS 2062 Hot Rolled Steel Sheet',
  rfqQuantity: 500,
  rfqUnit: 'kg',
  requiredDeliveryAt: '2026-12-01T12:00:00Z',
  overallProcurementBrief: 'Apex Steel Industries is recommended as the primary supplier due to high Contract OTD (96.0%) and low landed cost. Mahalaxmi Metals presents higher price and delivery risk.',
  aiProviderUsed: 'openai-gpt4o-json',
  aiGeneratedExplanation: true,
  recommendations: [
    {
      supplierId: 'sup-1111',
      supplierName: 'Apex Steel Industries',
      rank: 1,
      recommendationTier: 'RECOMMENDED',
      recommendationHeadline: 'Top reliability and landed cost efficiency',
      quoteId: 'quote-1111',
      quoteRevisionId: 'rev-1111',
      quotedUnitPrice: 95.0,
      quotedEconomicLandedCost: 48500.0,
      quotedCashPayable: 57230.0,
      quotedPromisedDeliveryDate: '2026-11-28T18:00:00Z',
      meetsRequiredDeliveryDate: true,
      historicalScorecard: {
        supplierId: 'sup-1111',
        supplierName: 'Apex Steel Industries',
        contractOtdRate: 96.0,
        eligiblePoCount: 12,
        onTimePoCount: 11,
        delayedPoCount: 1,
        shortClosedPoCount: 0,
        averagePoDelayDays: 1.0,
        consignmentCount: 14,
        consignmentOnTimeRate: 92.8,
        averageConsignmentsPerCompletedPo: 1.2,
        partialDeliveryOrderRate: 20.0,
        gateAcceptanceRate: 99.2,
        netUsableYieldRate: 98.8,
        latentDefectRate: 0.4,
        inspectedQuantityTotal: 15000,
        costVariancePercentage: -1.5,
        totalActualLandedCost: 1200000,
        confidenceTier: 'HIGH',
        confidenceExplanation: 'High empirical confidence based on 12 verified POs.'
      },
      verifiedStrengths: [
        'High Contract OTD (96.0%) across 12 verified order(s)',
        'High gate inspection acceptance (99.2%)',
        'MUNIM Verified Performer: Exceptional verified delivery and quality track record'
      ],
      identifiedRisks: [],
      missingEvidence: [],
      advisoryExplanation: 'Apex Steel has consistently met contractual delivery dates with negligible defect rates and landed costs below quoted baseline.',
      verifiedPerformerBadge: true,
      costDriftWarning: false,
      deliveryRiskWarning: false,
      isMaterialSpecificEvidence: true,
      compositeSuitabilityScore: 92.4
    },
    {
      supplierId: 'sup-2222',
      supplierName: 'Mahalaxmi Metals & Alloys',
      rank: 2,
      recommendationTier: 'CAUTION_RISK',
      recommendationHeadline: 'Delivery risk and historical cost overrun drift',
      quoteId: 'quote-2222',
      quoteRevisionId: 'rev-2222',
      quotedUnitPrice: 98.0,
      quotedEconomicLandedCost: 56780.0,
      quotedCashPayable: 67000.0,
      quotedPromisedDeliveryDate: '2026-12-03T18:00:00Z',
      meetsRequiredDeliveryDate: false,
      historicalScorecard: {
        supplierId: 'sup-2222',
        supplierName: 'Mahalaxmi Metals & Alloys',
        contractOtdRate: 75.0,
        eligiblePoCount: 6,
        onTimePoCount: 4,
        delayedPoCount: 2,
        shortClosedPoCount: 0,
        averagePoDelayDays: 3.5,
        consignmentCount: 8,
        consignmentOnTimeRate: 62.5,
        averageConsignmentsPerCompletedPo: 1.3,
        partialDeliveryOrderRate: 30.0,
        gateAcceptanceRate: 95.0,
        netUsableYieldRate: 93.5,
        latentDefectRate: 1.5,
        inspectedQuantityTotal: 5000,
        costVariancePercentage: 6.8,
        totalActualLandedCost: 650000,
        confidenceTier: 'MEDIUM',
        confidenceExplanation: 'Medium empirical confidence based on 6 verified POs.'
      },
      verifiedStrengths: [
        'Experienced regional supplier with 6 verified order(s)'
      ],
      identifiedRisks: [
        'Quoted delivery promise is after required procurement delivery deadline',
        'Historical delivery reliability below benchmark (75.0% Contract OTD)',
        'Historical landed cost overrun drift of +6.8% above baseline'
      ],
      missingEvidence: [
        'No historical deliveries for this specific material; general factory history used'
      ],
      advisoryExplanation: 'Quoted delivery date exceeds required deadline by 2 days, and historical invoices show +6.8% cost drift.',
      verifiedPerformerBadge: false,
      costDriftWarning: true,
      deliveryRiskWarning: true,
      isMaterialSpecificEvidence: false,
      compositeSuitabilityScore: 54.1
    },
    {
      supplierId: 'sup-3333',
      supplierName: 'New Horizon Industrial Co',
      rank: 3,
      recommendationTier: 'INSUFFICIENT_EVIDENCE',
      recommendationHeadline: 'Unrated new supplier without empirical delivery history',
      quoteId: 'quote-3333',
      quoteRevisionId: 'rev-3333',
      quotedUnitPrice: 91.0,
      quotedEconomicLandedCost: 46000.0,
      quotedCashPayable: 54280.0,
      quotedPromisedDeliveryDate: '2026-11-29T12:00:00Z',
      meetsRequiredDeliveryDate: true,
      historicalScorecard: {
        supplierId: 'sup-3333',
        supplierName: 'New Horizon Industrial Co',
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
        confidenceExplanation: 'Unrated supplier with 0 completed purchase orders.'
      },
      verifiedStrengths: [
        'Competitive quoted unit price'
      ],
      identifiedRisks: [],
      missingEvidence: [
        'Zero verified purchase orders on record',
        'No verified delivery gate arrivals recorded',
        'No physical quality inspection records'
      ],
      advisoryExplanation: 'Supplier has attractive quoted pricing but zero historical verified delivery or quality track record.',
      verifiedPerformerBadge: false,
      costDriftWarning: false,
      deliveryRiskWarning: false,
      isMaterialSpecificEvidence: false,
      compositeSuitabilityScore: 68.0
    }
  ]
};

describe('SupplierRecommendations Component', () => {
  it('1. Renders executive brief, disclaimer banner, and AI provider tag', async () => {
    vi.spyOn(api, 'getSupplierRecommendations').mockResolvedValue(mockRecommendationsData);

    render(<SupplierRecommendations rfqId="rfq-1111-1111" />);

    // Check loading indicator first
    expect(screen.getByText(/Synthesizing supplier history/i)).toBeTruthy();

    // Wait for data load
    await waitFor(() => {
      expect(screen.getByText('AI Supplier Recommendations & Advisory')).toBeTruthy();
    });

    // Human-in-the-loop disclaimer banner
    expect(screen.getByText(/Advisory Decision Support — Human Owner Approval Required/i)).toBeTruthy();
    expect(screen.getByText(/perform autonomous awards/i)).toBeTruthy();

    // Executive brief
    expect(screen.getByText(/IS 2062 Hot Rolled Steel Sheet — 500 kg/i)).toBeTruthy();
    expect(screen.getByText(/AI-Synthesized Narrative/i)).toBeTruthy();
    expect(screen.getByText(/\(openai-gpt4o-json\)/i)).toBeTruthy();
    expect(screen.getByText(/Apex Steel Industries is recommended as the primary supplier/i)).toBeTruthy();
  });

  it('2. Renders ranked candidate cards and recommendation tiers correctly', async () => {
    vi.spyOn(api, 'getSupplierRecommendations').mockResolvedValue(mockRecommendationsData);

    render(<SupplierRecommendations rfqId="rfq-1111-1111" />);

    await waitFor(() => {
      expect(screen.getByTestId('recommendation-card-1')).toBeTruthy();
    });

    // Check Candidate 1 (Apex Steel)
    expect(screen.getByText('Apex Steel Industries')).toBeTruthy();
    expect(screen.getByText('Top Recommendation')).toBeTruthy();
    expect(screen.getByText(/92.4/)).toBeTruthy();

    // Check Candidate 2 (Mahalaxmi Metals)
    expect(screen.getByText('Mahalaxmi Metals & Alloys')).toBeTruthy();
    expect(screen.getByText('Caution / High Risk')).toBeTruthy();
    expect(screen.getByText(/54.1/)).toBeTruthy();

    // Check Candidate 3 (New Horizon)
    expect(screen.getByText('New Horizon Industrial Co')).toBeTruthy();
    expect(screen.getByText('Insufficient Evidence')).toBeTruthy();
  });

  it('3. Renders VERIFIED PERFORMER badge and warning badges', async () => {
    vi.spyOn(api, 'getSupplierRecommendations').mockResolvedValue(mockRecommendationsData);

    render(<SupplierRecommendations rfqId="rfq-1111-1111" />);

    await waitFor(() => {
      expect(screen.getByText('VERIFIED PERFORMER')).toBeTruthy();
    });

    // Evidence scope badges: Material-Specific vs Cross-Material vs Unrated
    expect(screen.getByText(/VERIFIED MATERIAL HISTORY/i)).toBeTruthy();
    expect(screen.getByText(/CROSS-MATERIAL CONTEXT ONLY/i)).toBeTruthy();
    expect(screen.getByText(/UNRATED MATERIAL/i)).toBeTruthy();

    // Warning badges on Candidate 2
    expect(screen.getByText('DELIVERY RISK WARNING')).toBeTruthy();
    expect(screen.getByText('COST DRIFT WARNING (>+5%)')).toBeTruthy();
  });

  it('4. Preserves unknown metrics as "—" and displays missing evidence chips', async () => {
    vi.spyOn(api, 'getSupplierRecommendations').mockResolvedValue(mockRecommendationsData);

    render(<SupplierRecommendations rfqId="rfq-1111-1111" />);

    await waitFor(() => {
      expect(screen.getByText('New Horizon Industrial Co')).toBeTruthy();
    });

    // Missing evidence chips on Candidate 3
    expect(screen.getByText('Zero verified purchase orders on record')).toBeTruthy();
    expect(screen.getByText('No verified delivery gate arrivals recorded')).toBeTruthy();
    expect(screen.getByText('No physical quality inspection records')).toBeTruthy();

    // Missing evidence chip on Candidate 2
    expect(screen.getByText('No historical deliveries for this specific material; general factory history used')).toBeTruthy();
  });

  it('5. Handles empty recommendations response gracefully', async () => {
    vi.spyOn(api, 'getSupplierRecommendations').mockResolvedValue({
      ...mockRecommendationsData,
      recommendations: []
    });

    render(<SupplierRecommendations rfqId="rfq-empty" />);

    await waitFor(() => {
      expect(screen.getByText('No Supplier Quotes Available')).toBeTruthy();
    });
    expect(screen.getByText(/There are no evaluated supplier quotes recorded for this RFQ yet/i)).toBeTruthy();
  });

  it('6. Handles API errors and provides retry option', async () => {
    const errorSpy = vi.spyOn(api, 'getSupplierRecommendations').mockRejectedValue(new Error('Network error loading recommendations'));

    render(<SupplierRecommendations rfqId="rfq-fail" />);

    await waitFor(() => {
      expect(screen.getByText('Network error loading recommendations')).toBeTruthy();
    });

    const retryBtn = screen.getByRole('button', { name: /Try Again/i });
    expect(retryBtn).toBeTruthy();

    // Mock success on retry
    errorSpy.mockResolvedValueOnce(mockRecommendationsData);
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText('Apex Steel Industries')).toBeTruthy();
    });
  });

  it('7. Calls onBack, onViewScorecard, and onSelectQuote callback props', async () => {
    vi.spyOn(api, 'getSupplierRecommendations').mockResolvedValue(mockRecommendationsData);
    const onBackMock = vi.fn();
    const onViewScorecardMock = vi.fn();
    const onSelectQuoteMock = vi.fn();

    render(
      <SupplierRecommendations
        rfqId="rfq-1111-1111"
        onBack={onBackMock}
        onViewScorecard={onViewScorecardMock}
        onSelectQuote={onSelectQuoteMock}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('recommendation-card-1')).toBeTruthy();
    });

    // Test back button
    const backBtn = screen.getByRole('button', { name: /Back to RFQ/i });
    fireEvent.click(backBtn);
    expect(onBackMock).toHaveBeenCalledTimes(1);

    // Test view full scorecard button
    const scorecardBtns = screen.getAllByRole('button', { name: /View Full Scorecard/i });
    fireEvent.click(scorecardBtns[0]);
    expect(onViewScorecardMock).toHaveBeenCalledWith('sup-1111');

    // Test inspect quote button
    const inspectBtns = screen.getAllByRole('button', { name: /Inspect Quote/i });
    fireEvent.click(inspectBtns[0]);
    expect(onSelectQuoteMock).toHaveBeenCalledWith('rev-1111');
  });
});

describe('Supplier Recommendations Navigation Integration in App', () => {
  it('8. Renders AI Recommendations navigation tab and handles empty RFQ state', async () => {
    localStorage.setItem('munim-token', 'dev-owner');
    render(<App />);

    // Check Recommendations tab is in the navbar
    const recTab = screen.getByTestId('nav-tab-recommendations');
    expect(recTab).toBeTruthy();

    // Click recommendations tab when no RFQ is loaded
    fireEvent.click(recTab);

    // Expect empty state prompt
    expect(screen.getByTestId('recommendations-empty-rfq')).toBeTruthy();
    expect(screen.getByText('No Active RFQ Selected')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Go to Procurement/i })).toBeTruthy();
  });
});
