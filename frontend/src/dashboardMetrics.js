export function riskPercent(customer) {
  const supplied = Number(customer.churnRiskPercent)

  if (
    customer.churnRiskPercent !== null &&
    customer.churnRiskPercent !== undefined &&
    customer.churnRiskPercent !== '' &&
    Number.isFinite(supplied)
  ) {
    return supplied
  }

  const probability = Number(customer.churnProbability)
  return Number.isFinite(probability) ? probability * 100 : 0
}

const WHAT_IF_NUMBER_FIELDS = [
  ['tenureMonths', 'tenure_months'],
  ['monthlySpendInr', 'monthly_spend_inr'],
  ['transactions90d', 'transactions_90d'],
  ['supportTickets90d', 'support_tickets_90d'],
  ['avgResponseHours', 'avg_response_hours'],
  ['satisfactionScore', 'satisfaction_score'],
  ['appLogins30d', 'app_logins_30d'],
  ['daysSinceLastPurchase', 'days_since_last_purchase'],
  ['discountUsagePct', 'discount_usage_pct'],
  ['paymentFailures90d', 'payment_failures_90d'],
]

export function customerToWhatIfForm(customer = {}) {
  return {
    ageGroup: String(customer.ageGroup ?? ''),
    region: String(customer.region ?? ''),
    customerSegment: String(customer.customerSegment ?? ''),
    tenureMonths: String(customer.tenureMonths ?? ''),
    monthlySpendInr: String(customer.monthlySpendInr ?? ''),
    transactions90d: String(customer.transactions90d ?? ''),
    supportTickets90d: String(customer.supportTickets90d ?? ''),
    avgResponseHours: String(customer.avgResponseHours ?? ''),
    satisfactionScore: String(customer.satisfactionScore ?? ''),
    appLogins30d: String(customer.appLogins30d ?? ''),
    daysSinceLastPurchase: String(customer.daysSinceLastPurchase ?? ''),
    discountUsagePct: String(customer.discountUsagePct ?? ''),
    paymentFailures90d: String(customer.paymentFailures90d ?? ''),
  }
}

export function createWhatIfPredictionPayload(form) {
  const payload = {
    age_group: String(form.ageGroup ?? '').trim(),
    region: String(form.region ?? '').trim(),
    customer_segment: String(form.customerSegment ?? '').trim(),
  }

  if (!payload.age_group || !payload.region || !payload.customer_segment) {
    throw new Error('Enter an age group, region, and customer segment.')
  }

  WHAT_IF_NUMBER_FIELDS.forEach(([formKey, apiKey]) => {
    const rawValue = String(form[formKey] ?? '').trim()
    const value = Number(rawValue)
    if (rawValue === '' || !Number.isFinite(value)) {
      throw new Error('Complete every numeric profile field with a valid number.')
    }
    payload[apiKey] = value
  })

  return payload
}

export function predictionRiskPercent(prediction) {
  const supplied = Number(prediction?.churn_risk_percent ?? prediction?.churnRiskPercent)
  if (Number.isFinite(supplied)) return supplied

  const probability = Number(prediction?.churn_probability ?? prediction?.churnProbability)
  return Number.isFinite(probability) ? probability * 100 : 0
}

export function riskBand(value) {
  if (value >= 50) return 'High'
  if (value >= 30) return 'Medium'
  return 'Low'
}

export function tenureBand(months) {
  if (months <= 12) return '0–12 months'
  if (months <= 24) return '13–24 months'
  if (months <= 48) return '25–48 months'
  return '49+ months'
}

export function calculateDashboardStats(data, customers) {
  const total = Number(data?.totalCustomers ?? customers.length)
  const targeted = Number(
    data?.targetedCustomers ??
      customers.filter((customer) => customer.targeted).length,
  )
  const averageRisk = customers.length
    ? customers.reduce((sum, customer) => sum + riskPercent(customer), 0) /
      customers.length
    : 0
  const actualChurned = customers.filter(
    (customer) => String(customer.churned ?? '').toLowerCase() === 'yes',
  ).length
  const truePositives = customers.filter(
    (customer) =>
      String(customer.churned ?? '').toLowerCase() === 'yes' &&
      customer.predictedChurn === true,
  ).length

  const highRiskCustomers = customers.filter(
    (customer) =>
      String(customer.churned ?? '').toLowerCase() === 'no' &&
      riskPercent(customer) >= 50,
  )
  const highRisk = highRiskCustomers.length
  const actionAssigned = highRiskCustomers.filter(
    (customer) => customer.actionAssigned,
  ).length

  return {
    total,
    targeted,
    capacity: Number(data?.targetingCapacity ?? 0.3),
    averageRisk,
    highRisk,
    actualChurned,
    truePositives,
    falseNegatives: actualChurned - truePositives,
    observedChurnRecall: actualChurned ? (truePositives / actualChurned) * 100 : null,
    actualChurnRate: total ? (actualChurned / total) * 100 : 0,
    actualRetentionRate: total ? ((total - actualChurned) / total) * 100 : 0,
    actionAssigned,
    actionCoverage: highRisk ? (actionAssigned / highRisk) * 100 : null,
  }
}

export function calculateChurnBreakdowns(customers) {
  function summarize(keyFor) {
    const groups = new Map()

    customers.forEach((customer) => {
      const label = keyFor(customer) || 'Unknown'
      const group = groups.get(label) ?? { label, total: 0, churned: 0 }
      group.total += 1
      if (String(customer.churned ?? '').toLowerCase() === 'yes') {
        group.churned += 1
      }
      groups.set(label, group)
    })

    return [...groups.values()]
      .map((group) => ({
        ...group,
        rate: group.total ? (group.churned / group.total) * 100 : 0,
      }))
      .sort((a, b) => b.rate - a.rate)
  }

  return {
    segments: summarize((customer) => customer.customerSegment),
    regions: summarize((customer) => customer.region),
    tenure: summarize((customer) => tenureBand(Number(customer.tenureMonths ?? 0))),
  }
}
