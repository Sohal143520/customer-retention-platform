import assert from 'node:assert/strict'
import test from 'node:test'
import {
  calculateChurnBreakdowns,
  calculateDashboardStats,
  createWhatIfPredictionPayload,
  customerToWhatIfForm,
  predictionRiskPercent,
  riskBand,
  riskPercent,
} from './dashboardMetrics.js'

test('dashboard KPIs follow actual-label and active high-risk definitions', () => {
  const customers = [
    { churned: 'No', churnProbability: 0.5, actionAssigned: true, targeted: true },
    { churned: 'No', churnProbability: 0.7, actionAssigned: false, targeted: true },
    { churned: 'Yes', churnProbability: 0.95, actionAssigned: true },
    { churned: 'No', churnProbability: 0.3, actionAssigned: false },
  ]

  const stats = calculateDashboardStats(
    { totalCustomers: 4, targetedCustomers: 2, targetingCapacity: 0.3 },
    customers,
  )

  assert.equal(stats.actualChurned, 1)
  assert.equal(stats.actualChurnRate, 25)
  assert.equal(stats.actualRetentionRate, 75)
  assert.equal(stats.highRisk, 2)
  assert.equal(stats.actionAssigned, 1)
  assert.equal(stats.actionCoverage, 50)
})

test('what-if form maps a stored customer to the single-prediction API fields', () => {
  const form = customerToWhatIfForm({
    ageGroup: '26-35',
    region: 'North',
    customerSegment: 'Premium',
    tenureMonths: 18,
    monthlySpendInr: 2500,
    transactions90d: 12,
    supportTickets90d: 2,
    avgResponseHours: 4.5,
    satisfactionScore: 4,
    appLogins30d: 8,
    daysSinceLastPurchase: 20,
    discountUsagePct: 30,
    paymentFailures90d: 1,
  })

  assert.deepEqual(createWhatIfPredictionPayload(form), {
    age_group: '26-35',
    region: 'North',
    customer_segment: 'Premium',
    tenure_months: 18,
    monthly_spend_inr: 2500,
    transactions_90d: 12,
    support_tickets_90d: 2,
    avg_response_hours: 4.5,
    satisfaction_score: 4,
    app_logins_30d: 8,
    days_since_last_purchase: 20,
    discount_usage_pct: 30,
    payment_failures_90d: 1,
  })
})

test('what-if payload rejects missing fields and result percent accepts API naming', () => {
  assert.throws(() => createWhatIfPredictionPayload({}), /Enter an age group/)
  assert.equal(predictionRiskPercent({ churn_risk_percent: 62.5 }), 62.5)
  assert.equal(predictionRiskPercent({ churn_probability: 0.375 }), 37.5)
})

test('high-risk threshold is inclusive at 50 percent and ignores churned customers', () => {
  const stats = calculateDashboardStats({}, [
    { churned: 'No', churnRiskPercent: 50, actionAssigned: false },
    { churned: 'No', churnProbability: 0.499, actionAssigned: false },
    { churned: 'Yes', churnRiskPercent: 90, actionAssigned: true },
  ])

  assert.equal(stats.highRisk, 1)
  assert.equal(stats.actionCoverage, 0)
})

test('empty customer list has zero actual rates and no action-coverage denominator', () => {
  const stats = calculateDashboardStats({}, [])

  assert.equal(stats.actualChurnRate, 0)
  assert.equal(stats.actualRetentionRate, 0)
  assert.equal(stats.highRisk, 0)
  assert.equal(stats.actionCoverage, null)
  assert.equal(stats.observedChurnRecall, null)
})

test('observed churn recall uses labeled churners and the model screening decision', () => {
  const stats = calculateDashboardStats({}, [
    { churned: 'Yes', predictedChurn: true },
    { churned: 'Yes', predictedChurn: false },
    { churned: 'No', predictedChurn: true },
    { churned: 'No', predictedChurn: false },
    { churned: '', predictedChurn: true },
  ])

  assert.equal(stats.truePositives, 1)
  assert.equal(stats.falseNegatives, 1)
  assert.equal(stats.observedChurnRecall, 50)
})

test('observed churn recall is unavailable when the loaded labels have no churners', () => {
  const stats = calculateDashboardStats({}, [
    { churned: 'No', predictedChurn: true },
    { churned: 'No', predictedChurn: false },
  ])

  assert.equal(stats.observedChurnRecall, null)
})

test('risk values support percentage fields and probability fallback', () => {
  assert.equal(riskPercent({ churnRiskPercent: 62, churnProbability: 0.1 }), 62)
  assert.equal(riskPercent({ churnRiskPercent: '', churnProbability: 0.62 }), 62)
  assert.equal(riskBand(29.9), 'Low')
  assert.equal(riskBand(50), 'High')
  assert.equal(riskBand(49.9), 'Medium')
  assert.equal(riskBand(40), 'Medium')
  assert.equal(riskBand(30), 'Medium')
})

test('churn breakdowns calculate observed rates by segment, region, and tenure', () => {
  const breakdowns = calculateChurnBreakdowns([
    { churned: 'Yes', customerSegment: 'Value', region: 'East', tenureMonths: 8 },
    { churned: 'No', customerSegment: 'Value', region: 'East', tenureMonths: 18 },
    { churned: 'Yes', customerSegment: 'Premium', region: 'West', tenureMonths: 54 },
  ])

  assert.deepEqual(breakdowns.segments.map(({ label, rate }) => [label, rate]), [
    ['Premium', 100],
    ['Value', 50],
  ])
  assert.deepEqual(breakdowns.regions.map(({ label, rate }) => [label, rate]), [
    ['West', 100],
    ['East', 50],
  ])
  assert.deepEqual(breakdowns.tenure.map(({ label, rate }) => [label, rate]), [
    ['0–12 months', 100],
    ['49+ months', 100],
    ['13–24 months', 0],
  ])
})
