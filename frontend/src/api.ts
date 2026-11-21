export type Supplier = { id:string; name:string; location?:string; isSynthetic:boolean; active:boolean };
export type Rfq = { id:string; materialId:string; specification:string; quantity:string; unit:string; requiredProcurementDeliveryAt:string; referenceAt:string; timezone:string; status:string };
export type Quote = { id:string; supplierId:string; revisionId:string; revision:number; state:string; rawMessage:string; completenessStatus:string; missingFields:string[]; fields:Record<string,unknown> };
export type Evaluation = { id:string; rfqId:string; quoteRevisionId:string; status:string; calculationVersion:string; snapshotSha256:string; inputSnapshot:Record<string,unknown>; result:Record<string,string> };
export type Proposal = { id:string; type:string; status:string; snapshotHash:string; invalidatedAt?:string|null; riskFlag?:string|null; savingsVsNextBest?:number|string|null };
export type Approval = { id:string; proposalId:string; actorId:string; decision:string; snapshotHash:string; decidedAt:string };
export type ApprovalQueueItem = { proposal:Proposal; createdAt:string; ownerActionRequired:boolean; summary:Record<string, any> };
export type ApprovalPage = { items:ApprovalQueueItem[]; total:number };
export type ApprovalDetail = { proposal:Proposal; decisions:Approval[]; snapshot:Record<string, any> };

export type CustomerEnquiry = {
  id: string;
  customerName: string;
  customerReference?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  requiredDeliveryDate: string;
  status: 'OPEN' | 'COSTED' | 'QUOTED' | 'WON' | 'LOST';
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type BomItem = {
  id: string;
  bomId: string;
  materialId: string;
  partName: string;
  netQuantity: string;
  unit: string;
  scrapPercentage: string;
  scrapRecoverySupported: boolean;
  scrapRecoveryPrice?: string | null;
  sourcingBasis: 'AWARDED_QUOTE' | 'ESTIMATE';
  awardedQuoteRevisionId?: string | null;
  estimatedUnitCost?: string | null;
  estimateRationale?: string | null;
  createdAt: string;
};

export type Bom = {
  id: string;
  enquiryId: string;
  bomVersion: number;
  batchQuantity: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  items: BomItem[];
};

export type CostingSheet = {
  id: string;
  enquiryId: string;
  bomId: string;
  batchQuantity: string;
  materialCost: string;
  conversionCost: string;
  overheadCost: string;
  packagingCost: string;
  freightCost: string;
  totalCost: string;
  targetMargin: string;
  targetPrice: string;
  quotedPrice?: string | null;
  realizedMargin?: string | null;
  marginClassification: 'HEALTHY_MARGIN' | 'ACCEPTABLE_MARGIN' | 'BELOW_MINIMUM_MARGIN' | 'LOSS_MAKING';
  calculationVersion: string;
  calculationSnapshot: Record<string, unknown>;
  snapshotSha256: string;
  createdAt: string;
};

export type CustomerQuotation = {
  id: string;
  enquiryId: string;
  costingSheetId: string;
  sellingPrice: string;
  outputGstRate: string;
  customerPayable: string;
  validFrom: string;
  validUntil: string;
  paymentTerms: string;
  marginClassification: string;
  approvalStatus: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXPIRED';
  commercialProposalId?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type FieldValue = {
  value: unknown;
  confidence: number | null;
  evidence: string | null;
  source: string;
  status: 'EXTRACTED' | 'MISSING' | 'AMBIGUOUS' | 'LOW_CONFIDENCE' | 'OWNER_CONFIRMED' | 'UNKNOWN';
};

export type ExtractionResult = {
  quoteRevisionId: string;
  candidates: Record<string, FieldValue>;
  missingFields: string[];
  ambiguities: string[];
  provider: string;
  model: string;
  promptVersion: string;
  validationStatus: string;
  validationErrors: string[];
};

export type ClarificationDraft = {
  id: string;
  quoteRevisionId: string;
  draft: string;
  expectedFields: string[];
  createdAt: string;
  status: string;
};

export type ConfidenceTier = 'UNRATED' | 'LOW' | 'MEDIUM' | 'HIGH';

export interface SupplierScorecard {
  supplierId: string;
  supplierName: string;
  contractOtdRate: number | null;
  eligiblePoCount: number;
  onTimePoCount: number;
  delayedPoCount: number;
  shortClosedPoCount: number;
  averagePoDelayDays: number | null;
  consignmentCount: number;
  consignmentOnTimeRate: number | null;
  averageConsignmentsPerCompletedPo: number | null;
  partialDeliveryOrderRate: number | null;
  gateAcceptanceRate: number | null;
  netUsableYieldRate: number | null;
  latentDefectRate: number | null;
  inspectedQuantityTotal: number | null;
  costVariancePercentage: number | null;
  totalActualLandedCost: number | null;
  confidenceTier: ConfidenceTier;
  confidenceExplanation: string;
}

export interface SupplierScorecardList {
  items: SupplierScorecard[];
}

export interface SupplierPerformanceFilters {
  materialId?: string;
  fromDate?: string;
  toDate?: string;
}

export type RecommendationTier = 'RECOMMENDED' | 'VIABLE_ALTERNATIVE' | 'CAUTION_RISK' | 'INSUFFICIENT_EVIDENCE';

export interface SupplierRecommendationCandidateView {
  supplierId: string;
  supplierName: string;
  rank: number;
  recommendationTier: RecommendationTier;
  recommendationHeadline?: string;
  quoteId?: string;
  quoteRevisionId?: string;
  quotedUnitPrice?: number | null;
  quotedEconomicLandedCost?: number | null;
  quotedCashPayable?: number | null;
  quotedPromisedDeliveryDate?: string | null;
  meetsRequiredDeliveryDate?: boolean | null;
  historicalScorecard?: SupplierScorecard;
  verifiedStrengths: string[];
  identifiedRisks: string[];
  missingEvidence: string[];
  advisoryExplanation?: string;
  verifiedPerformerBadge: boolean;
  costDriftWarning: boolean;
  deliveryRiskWarning: boolean;
  isMaterialSpecificEvidence?: boolean;
  compositeSuitabilityScore?: number | null;
}

export interface SupplierRecommendationList {
  rfqId: string;
  rfqMaterialName: string;
  rfqQuantity: number | string;
  rfqUnit: string;
  requiredDeliveryAt: string;
  recommendations: SupplierRecommendationCandidateView[];
  overallProcurementBrief: string;
  aiProviderUsed: string;
  aiGeneratedExplanation: boolean;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  fieldErrors?: Record<string, string>;
  traceId?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly detail?: string;
  readonly fieldErrors?: Record<string, string>;
  readonly isNetworkError: boolean;

  constructor(message: string, status: number, code?: string, detail?: string, fieldErrors?: Record<string, string>, isNetworkError: boolean = false) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.detail = detail;
    this.fieldErrors = fieldErrors;
    this.isNetworkError = isNetworkError;
  }
}

const base = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api/v1';
const h = (key?: string) => ({
  'Content-Type': 'application/json',
  Authorization: 'Bearer ' + (localStorage.getItem('munim-token') ?? 'dev-owner'),
  ...(key ? { 'Idempotency-Key': key } : {})
});

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const url = path.startsWith('http') ? path : base + path;
  let r: Response;
  try {
    r = await fetch(url, init);
  } catch (err) {
    const rawMsg = err instanceof Error ? err.message : String(err);
    throw new ApiError(
      `Network connectivity failure: backend unreachable at ${url}. Ensure Spring Boot is running on port 8080 (${rawMsg}).`,
      0,
      'NETWORK_UNAVAILABLE',
      `Failed to connect to ${url}. Check local Docker containers or Spring Boot server.`,
      undefined,
      true
    );
  }

  if (!r.ok) {
    const text = await r.text();
    let problem: ProblemDetails | null = null;
    try {
      problem = JSON.parse(text);
    } catch {
      // not JSON
    }

    const code = problem?.code || `HTTP_${r.status}`;
    const detail = problem?.detail || text || r.statusText;
    const fieldErrors = problem?.fieldErrors;

    let friendly = detail;
    if (r.status === 401) {
      friendly = `Authentication required (401): ${detail || 'Valid Bearer token required'}`;
    } else if (r.status === 403) {
      friendly = `Access forbidden (403): ${detail || 'Insufficient persona permissions'}`;
    } else if (r.status === 404) {
      friendly = `Resource not found (404): ${detail || 'The requested resource does not exist'}`;
    } else if (r.status === 409) {
      friendly = `State conflict (409): ${detail || 'Cryptographic snapshot hash mismatch or concurrent edit conflict'}`;
    } else if (r.status === 422 || r.status === 400) {
      friendly = `Validation error (${r.status}): ${detail || 'Incomplete or invalid request data'}`;
    } else if (r.status >= 500) {
      friendly = `Server error (${r.status}): ${detail || 'Internal backend processing error'}`;
    }

    throw new ApiError(friendly, r.status, code, detail, fieldErrors, false);
  }

  if (r.status === 204 || r.headers.get('content-length') === '0') {
    return {} as T;
  }

  const text = await r.text();
  if (!text || text.trim().length === 0) {
    return {} as T;
  }
  return JSON.parse(text) as T;
}

function formatIsoDateTime(val?: string, endOfDay = false): string | undefined {
  if (!val) return undefined;
  if (val.includes('T')) return val;
  return endOfDay ? `${val}T23:59:59Z` : `${val}T00:00:00Z`;
}

export const api = {
  checkHealth: async () => {
    const rootUrl = base.replace(/\/api(\/v1)?$/, '');
    return req<{
      status: string;
      components?: {
        db?: { status: string };
        diskSpace?: { status: string };
        ping?: { status: string };
      };
    }>(`${rootUrl}/actuator/health`);
  },
  materials: () => req<{ items: Array<{ id: string; name: string; grade?: string | null; specification: string; baseUnit: string; active: boolean }> }>('/materials', { headers: h() }),
  addSupplier: (name: string) => req<Supplier>('/suppliers', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ name, location: 'Synthetic demo', isSynthetic: true }) }),
  createRfq: (data: object) => req<Rfq>('/rfqs', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify(data) }),
  addQuote: (id: string, data: object) => req<Quote>('/rfqs/' + id + '/quotes', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ manualFields: {}, ...data }) }),
  getQuote: (id: string, revision: number) => req<Quote>('/quotes/' + id + '/revisions/' + revision, { headers: h() }),
  extractQuote: (quoteId: string, revision: number) =>
    req<ExtractionResult>('/quotes/' + quoteId + '/revisions/' + revision + '/extract', { method: 'POST', headers: h(crypto.randomUUID()), body: '{}' }),
  getExtraction: (quoteId: string, revision: number) =>
    req<ExtractionResult>('/quotes/' + quoteId + '/revisions/' + revision + '/extraction', { headers: h() }),
  correctFields: (quoteId: string, revision: number, fields: Record<string, unknown>, reason: string) =>
    req<Quote>('/quotes/' + quoteId + '/revisions/' + revision + '/fields', { method: 'PUT', headers: h(), body: JSON.stringify({ fields, reason }) }),
  createClarification: (quoteId: string, revision: number, expectedFields: string[]) =>
    req<ClarificationDraft>('/quotes/' + quoteId + '/revisions/' + revision + '/clarifications', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ expectedFields }) }),
  listClarifications: (quoteId: string, revision: number) =>
    req<{ items: ClarificationDraft[] }>('/quotes/' + quoteId + '/revisions/' + revision + '/clarifications', { headers: h() }),
  respondClarification: (quoteId: string, revision: number, clarificationId: string, responseText: string, simulated: boolean = true) =>
    req<Quote>('/quotes/' + quoteId + '/revisions/' + revision + '/clarifications/' + clarificationId + '/response', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ responseText, receivedAt: new Date().toISOString(), simulated }) }),
  completeness: (id: string, revision: string) => req<{ complete: boolean; missingFields: string[] }>('/rfqs/' + id + '/completeness?quoteRevisionId=' + revision, { headers: h() }),
  evaluate: (id: string, revision: string) => req<Evaluation>('/rfqs/' + id + '/evaluations', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ quoteRevisionIds: [revision], policyVersion: 1 }) }),
  getRfq: (id: string) => req<Rfq>('/rfqs/' + id, { headers: h() }),
  createAwardProposal: (evaluationId: string, quoteRevisionId: string, expectedSnapshotHash: string) =>
    req<Proposal>('/evaluations/' + evaluationId + '/award-proposals', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ quoteRevisionId, expectedSnapshotHash }) }),
  listApprovals: (status?: string) =>
    req<ApprovalPage>('/approvals' + (status ? '?status=' + status : ''), { headers: h() }),
  getApprovalDetail: (proposalId: string) =>
    req<ApprovalDetail>('/approvals/' + proposalId, { headers: h() }),
  decideProposal: (proposalId: string, decision: 'APPROVE' | 'REJECT', reason: string, expectedSnapshotHash: string) =>
    req<Approval>('/proposals/' + proposalId + '/decision', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ decision, reason: reason || null, expectedSnapshotHash }) }),
  listEnquiries: (status?: string) =>
    req<{ items: CustomerEnquiry[] }>('/customer-enquiries' + (status ? '?status=' + status : ''), { headers: h() }),
  createEnquiry: (data: object) =>
    req<CustomerEnquiry>('/customer-enquiries', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify(data) }),
  getEnquiry: (id: string) =>
    req<CustomerEnquiry>('/customer-enquiries/' + id, { headers: h() }),
  listBoms: (enquiryId: string) =>
    req<{ items: Bom[] }>('/customer-enquiries/' + enquiryId + '/boms', { headers: h() }),
  createBom: (enquiryId: string, data: object) =>
    req<Bom>('/customer-enquiries/' + enquiryId + '/boms', { method: 'POST', headers: h(), body: JSON.stringify(data) }),
  getBom: (id: string) =>
    req<Bom>('/boms/' + id, { headers: h() }),
  addBomItem: (bomId: string, data: object) =>
    req<BomItem>('/boms/' + bomId + '/items', { method: 'POST', headers: h(), body: JSON.stringify(data) }),
  deleteBomItem: (bomId: string, itemId: string) =>
    req<void>('/boms/' + bomId + '/items/' + itemId, { method: 'DELETE', headers: h() }),
  runCosting: (bomId: string, data: object) =>
    req<CostingSheet>('/boms/' + bomId + '/costings', { method: 'POST', headers: h(), body: JSON.stringify(data) }),
  getCosting: (id: string) =>
    req<CostingSheet>('/costing-sheets/' + id, { headers: h() }),
  listCostings: (bomId: string) =>
    req<{ items: CostingSheet[] }>('/boms/' + bomId + '/costings', { headers: h() }),
  createQuotation: (data: object) =>
    req<CustomerQuotation>('/customer-quotations', { method: 'POST', headers: h(), body: JSON.stringify(data) }),
  listQuotations: (enquiryId?: string) =>
    req<{ items: CustomerQuotation[] }>('/customer-quotations' + (enquiryId ? '?enquiryId=' + enquiryId : ''), { headers: h() }),
  getQuotation: (id: string) =>
    req<CustomerQuotation>('/customer-quotations/' + id, { headers: h() }),
  submitQuotationApproval: (id: string, expectedSnapshotHash?: string) =>
    req<Proposal>('/customer-quotations/' + id + '/submit-approval', { method: 'POST', headers: h(crypto.randomUUID()), body: JSON.stringify({ expectedSnapshotHash: expectedSnapshotHash || null }) }),
  seedDemoData: () =>
    req<{ status: string; message: string }>('/demo/seed', { method: 'POST', headers: h() }),
  simulateCosting: (bomId: string, scrapPct?: number, targetMarginPct?: number) =>
    req<any>('/boms/' + bomId + '/simulate-costing?' + (scrapPct !== undefined ? 'scrapPercentage=' + scrapPct : '') + (targetMarginPct !== undefined ? '&targetMarginPct=' + targetMarginPct : ''), { method: 'POST', headers: h() }),
  listSupplierScorecards: (filters?: SupplierPerformanceFilters) => {
    const params = new URLSearchParams();
    if (filters?.materialId) params.append('materialId', filters.materialId);
    if (filters?.fromDate) {
      const dt = formatIsoDateTime(filters.fromDate, false);
      if (dt) params.append('fromDate', dt);
    }
    if (filters?.toDate) {
      const dt = formatIsoDateTime(filters.toDate, true);
      if (dt) params.append('toDate', dt);
    }
    const qs = params.toString();
    return req<SupplierScorecardList>('/suppliers/scorecards' + (qs ? '?' + qs : ''), { headers: h() });
  },
  getSupplierScorecard: (supplierId: string, filters?: SupplierPerformanceFilters) => {
    const params = new URLSearchParams();
    if (filters?.materialId) params.append('materialId', filters.materialId);
    if (filters?.fromDate) {
      const dt = formatIsoDateTime(filters.fromDate, false);
      if (dt) params.append('fromDate', dt);
    }
    if (filters?.toDate) {
      const dt = formatIsoDateTime(filters.toDate, true);
      if (dt) params.append('toDate', dt);
    }
    const qs = params.toString();
    return req<SupplierScorecard>('/suppliers/' + supplierId + '/scorecard' + (qs ? '?' + qs : ''), { headers: h() });
  },
  getSupplierRecommendations: (rfqId: string) =>
    req<SupplierRecommendationList>('/rfqs/' + rfqId + '/supplier-recommendations', { headers: h() })
};
