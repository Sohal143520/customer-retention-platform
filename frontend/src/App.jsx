
import { Fragment, useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'
import {
  calculateChurnBreakdowns,
  calculateDashboardStats,
  createWhatIfPredictionPayload,
  customerToWhatIfForm,
  predictionRiskPercent,
  riskBand,
  riskPercent,
  tenureBand,
} from './dashboardMetrics.js'

const LOGIN_URL = '/api/v1/auth/login'
const BULK_URL = '/api/v1/predictions/bulk'
const UPLOAD_URL = '/api/v1/customers/upload'
const BASELINE_URL = '/api/v1/retention-cohorts/baseline'
const PAGE_SIZE = 50
const EMPTY_CUSTOMERS = []
const WHAT_IF_CATEGORIES = [
  { key: 'ageGroup', apiLabel: 'Age group', listId: 'whatIfAgeGroups' },
  { key: 'region', apiLabel: 'Region', listId: 'whatIfRegions' },
  { key: 'customerSegment', apiLabel: 'Customer segment', listId: 'whatIfSegments' },
]
const WHAT_IF_NUMBERS = [
  { key: 'tenureMonths', label: 'Tenure (months)', min: 0, step: 1 },
  { key: 'monthlySpendInr', label: 'Monthly spend (INR)', min: 0, step: 1 },
  { key: 'transactions90d', label: 'Transactions (90 days)', min: 0, step: 1 },
  { key: 'supportTickets90d', label: 'Support tickets (90 days)', min: 0, step: 1 },
  { key: 'avgResponseHours', label: 'Average response (hours)', min: 0, step: 'any' },
  { key: 'satisfactionScore', label: 'Satisfaction score (1–5)', min: 1, max: 5, step: 1 },
  { key: 'appLogins30d', label: 'App logins (30 days)', min: 0, step: 1 },
  { key: 'daysSinceLastPurchase', label: 'Days since last purchase', min: 0, step: 1 },
  { key: 'discountUsagePct', label: 'Discount use (%)', min: 0, max: 100, step: 1 },
  { key: 'paymentFailures90d', label: 'Payment failures (90 days)', min: 0, step: 1 },
]

function getToken(data) {
  const value =
    typeof data === 'string'
      ? data
      : data?.token ?? data?.accessToken ?? data?.jwt

  if (typeof value !== 'string') return ''
  return value.replace(/^Bearer\s*/i, '').trim()
}

function formatPercent(value) {
  return `${Number(value).toFixed(1)}%`
}

function retentionRecommendation(customer) {
  if (Number(customer.paymentFailures90d) >= 2) {
    return {
      actionName: 'Billing Assistance',
      recommendation: 'Review recent payment failures with the customer and offer guided billing support.',
    }
  }
  if (
    Number(customer.supportTickets90d) >= 3 ||
    Number(customer.satisfactionScore) <= 2
  ) {
    return {
      actionName: 'Priority Support Concierge',
      recommendation: 'Review recent support history and offer a concierge follow-up on unresolved issues.',
    }
  }
  if (Number(customer.tenureMonths) <= 12) {
    return {
      actionName: 'Engagement Check-In',
      recommendation: 'Offer an onboarding check-in and ask what would make the service more useful.',
    }
  }
  if (Number(customer.discountUsagePct) >= 60) {
    return {
      actionName: 'Loyalty Tier Incentive',
      recommendation: 'Review loyalty eligibility and explain available tier benefits for human approval.',
    }
  }
  return {
    actionName: 'Engagement Check-In',
    recommendation: 'Offer a short engagement check-in and ask about the customer’s current priorities.',
  }
}

function StatCard({ icon, label, value, detail, tone }) {
  const tones = {
    purple: 'text-primary bg-primary-subtle',
    blue: 'text-info-emphasis bg-info-subtle',
    orange: 'text-warning-emphasis bg-warning-subtle',
    red: 'text-danger bg-danger-subtle',
  }

  return (
    <div className="col-12 col-sm-6 col-xl-3">
      <article className="card h-100 border-0 shadow-sm stat-card">
        <div className="card-body d-flex align-items-start gap-3 p-4">
          <div
            className={`rounded-3 d-flex align-items-center justify-content-center flex-shrink-0 ${tones[tone] || 'bg-body-secondary'}`}
            style={{ width: 48, height: 48, fontSize: '1.4rem' }}
          >
            {icon}
          </div>

          <div className="flex-grow-1 min-w-0">
            <p className="text-body-secondary small fw-semibold mb-2">
              {label}
            </p>
            <h2 className="h3 fw-bold text-body mb-2">
              {value}
            </h2>
            <p className="small text-body-secondary mb-0">
              {detail}
            </p>
          </div>
        </div>
      </article>
    </div>
  )
}

function ChurnBreakdown({ title, items }) {
  return (
    <div className="col-12 col-xl-4">
      <article className="card h-100 border-0 shadow-sm">
        <div className="card-body p-3 p-md-4">
          <h3 className="h6 fw-bold mb-1">{title}</h3>
          <p className="small text-body-secondary mb-4">
            Observed churn among loaded records
          </p>
          {items.length ? (
            <div className="d-flex flex-column gap-3">
              {items.map((item) => (
                <div key={item.label}>
                  <div className="d-flex justify-content-between gap-2 small mb-1">
                    <span className="text-body fw-semibold">{item.label}</span>
                    <span className="text-body-secondary text-nowrap">
                      {item.churned.toLocaleString()} / {item.total.toLocaleString()} · {formatPercent(item.rate)}
                    </span>
                  </div>
                  <div
                    className="progress"
                    role="progressbar"
                    aria-label={`${item.label} observed churn rate`}
                    aria-valuenow={item.rate}
                    aria-valuemin="0"
                    aria-valuemax="100"
                    style={{ height: 7 }}
                  >
                    <div
                      className="progress-bar bg-primary"
                      style={{ width: `${Math.min(100, item.rate)}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="small text-body-secondary mb-0">No customer records to summarize.</p>
          )}
        </div>
      </article>
    </div>
  )
}

function WhatIfScoring({ customers, token }) {
  const [baseCustomerId, setBaseCustomerId] = useState(
    () => customers[0]?.customerId ?? '',
  )
  const [features, setFeatures] = useState(
    () => customerToWhatIfForm(customers[0]),
  )
  const [prediction, setPrediction] = useState(null)
  const [scoring, setScoring] = useState(false)
  const [error, setError] = useState('')

  const categories = useMemo(() => ({
    ageGroup: [...new Set(customers.map((customer) => customer.ageGroup).filter(Boolean))].sort(),
    region: [...new Set(customers.map((customer) => customer.region).filter(Boolean))].sort(),
    customerSegment: [...new Set(customers.map((customer) => customer.customerSegment).filter(Boolean))].sort(),
  }), [customers])

  const baseCustomer = customers.find(
    (customer) => customer.customerId === baseCustomerId,
  )

  function chooseBaseCustomer(event) {
    const customerId = event.target.value
    setBaseCustomerId(customerId)
    setPrediction(null)
    setError('')

    const customer = customers.find((item) => item.customerId === customerId)
    if (customer) setFeatures(customerToWhatIfForm(customer))
  }

  function updateFeature(key, value) {
    setFeatures((current) => ({ ...current, [key]: value }))
    setPrediction(null)
    setError('')
  }

  async function scoreScenario(event) {
    event.preventDefault()
    setScoring(true)
    setError('')
    setPrediction(null)

    try {
      const payload = createWhatIfPredictionPayload(features)
      const response = await fetch('/api/v1/predictions/churn', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })
      const result = await response.json().catch(() => ({}))

      if (response.status === 401 || response.status === 403) {
        throw new Error('Your session may have expired. Please sign in again.')
      }
      if (!response.ok) {
        throw new Error(result.message || result.detail || `Scenario scoring failed (HTTP ${response.status}).`)
      }

      setPrediction(result)
    } catch (err) {
      setError(err.message || 'Unable to score this scenario.')
    } finally {
      setScoring(false)
    }
  }

  const scenarioRisk = prediction ? predictionRiskPercent(prediction) : null
  const baseRisk = baseCustomer ? riskPercent(baseCustomer) : null
  const riskChange = scenarioRisk !== null && baseRisk !== null
    ? scenarioRisk - baseRisk
    : null
  const screeningPrediction = prediction?.predicted_churn ?? prediction?.predictedChurn

  return (
    <section className="card border-0 shadow-sm mb-4" aria-labelledby="whatIfTitle">
      <div className="card-body p-3 p-md-4">
        <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-start gap-2 mb-3">
          <div>
            <h3 className="h5 fw-bold mb-1" id="whatIfTitle">What-if churn scoring</h3>
            <p className="small text-body-secondary mb-0">
              Change a profile value and ask the existing model to score the scenario.
            </p>
          </div>
          <span className="badge rounded-pill text-bg-secondary">Simulation only</span>
        </div>

        <form onSubmit={scoreScenario}>
          <div className="row g-3">
            <div className="col-12">
              <label className="form-label fw-semibold" htmlFor="whatIfBaseCustomer">
                Start from a loaded customer (optional)
              </label>
              <select
                className="form-select"
                id="whatIfBaseCustomer"
                value={baseCustomerId}
                onChange={chooseBaseCustomer}
              >
                <option value="">Manual profile</option>
                {customers.map((customer) => (
                  <option key={customer.customerId} value={customer.customerId}>
                    {customer.customerId} · {customer.region} · {customer.customerSegment}
                  </option>
                ))}
              </select>
            </div>

            {WHAT_IF_CATEGORIES.map((field) => (
              <div className="col-12 col-sm-6 col-lg-4" key={field.key}>
                <label className="form-label" htmlFor={`whatIf-${field.key}`}>
                  {field.apiLabel}
                </label>
                <input
                  className="form-control"
                  id={`whatIf-${field.key}`}
                  list={field.listId}
                  value={features[field.key]}
                  onChange={(event) => updateFeature(field.key, event.target.value)}
                  required
                />
                <datalist id={field.listId}>
                  {categories[field.key].map((value) => <option key={value} value={value} />)}
                </datalist>
              </div>
            ))}

            {WHAT_IF_NUMBERS.map((field) => (
              <div className="col-12 col-sm-6 col-lg-3" key={field.key}>
                <label className="form-label" htmlFor={`whatIf-${field.key}`}>
                  {field.label}
                </label>
                <input
                  className="form-control"
                  id={`whatIf-${field.key}`}
                  type="number"
                  min={field.min}
                  max={field.max}
                  step={field.step}
                  value={features[field.key]}
                  onChange={(event) => updateFeature(field.key, event.target.value)}
                  required
                />
              </div>
            ))}
          </div>

          {error && <div className="alert alert-danger mt-3 mb-0" role="alert">{error}</div>}

          <div className="d-flex flex-column flex-sm-row align-items-sm-center gap-3 mt-4">
            <button className="btn btn-primary" type="submit" disabled={scoring}>
              {scoring ? 'Scoring scenario…' : 'Score this scenario'}
            </button>
            <p className="small text-body-secondary mb-0">
              Scenario values are sent for scoring only. This does not update the customer or show that an intervention will cause an outcome.
            </p>
          </div>
        </form>

        {prediction && (
          <div className="border-top mt-4 pt-4" aria-live="polite">
            <h4 className="h6 fw-bold mb-3">Scenario result</h4>
            <div className="row g-3">
              <div className="col-12 col-sm-6 col-lg-3">
                <div className="rounded-3 bg-body-tertiary p-3 h-100">
                  <div className="small text-body-secondary">Scenario churn risk</div>
                  <div className="h3 fw-bold mb-0">{formatPercent(scenarioRisk)}</div>
                  <div className="small">{riskBand(scenarioRisk)} risk</div>
                </div>
              </div>
              <div className="col-12 col-sm-6 col-lg-3">
                <div className="rounded-3 bg-body-tertiary p-3 h-100">
                  <div className="small text-body-secondary">Loaded customer risk</div>
                  <div className="h3 fw-bold mb-0">{baseRisk === null ? '—' : formatPercent(baseRisk)}</div>
                  <div className="small">{baseCustomer ? 'Original model score' : 'Manual profile has no baseline'}</div>
                </div>
              </div>
              <div className="col-12 col-sm-6 col-lg-3">
                <div className="rounded-3 bg-body-tertiary p-3 h-100">
                  <div className="small text-body-secondary">Change from baseline</div>
                  <div className="h3 fw-bold mb-0">
                    {riskChange === null ? '—' : `${riskChange > 0 ? '+' : ''}${riskChange.toFixed(1)} pp`}
                  </div>
                  <div className="small">Percentage-point difference</div>
                </div>
              </div>
              <div className="col-12 col-sm-6 col-lg-3">
                <div className="rounded-3 bg-body-tertiary p-3 h-100">
                  <div className="small text-body-secondary">40% screening result</div>
                  <div className="fw-bold mt-1">
                    {screeningPrediction === undefined ? 'Not returned' : screeningPrediction ? 'Flagged by model' : 'Below screening threshold'}
                  </div>
                  <div className="small">This is separate from the 50% high-risk KPI.</div>
                </div>
              </div>
            </div>
            {prediction.note && <p className="small text-body-secondary mt-3 mb-0">{prediction.note}</p>}
            {prediction.model_explanation && (
              <div className="mt-4">
                <h5 className="h6 fw-bold">Model factors behind this score</h5>
                <p className="small text-body-secondary">
                  {prediction.model_explanation.method}
                </p>
                <div className="row g-3">
                  <div className="col-12 col-md-6">
                    <div className="rounded-3 border p-3 h-100">
                      <h6 className="small fw-bold text-danger">Factors that raised the model score</h6>
                      {prediction.model_explanation.risk_increasing?.length ? (
                        <ul className="list-unstyled mb-0">
                          {prediction.model_explanation.risk_increasing.map((item) => (
                            <li className="d-flex justify-content-between gap-3 py-1" key={item.feature}>
                              <span>{item.feature}</span>
                              <span className="text-body-secondary text-nowrap">+{Number(item.log_odds_contribution).toFixed(3)} log-odds</span>
                            </li>
                          ))}
                        </ul>
                      ) : <p className="small text-body-secondary mb-0">No score-raising factor was available for this profile.</p>}
                    </div>
                  </div>
                  <div className="col-12 col-md-6">
                    <div className="rounded-3 border p-3 h-100">
                      <h6 className="small fw-bold text-success">Factors that lowered the model score</h6>
                      {prediction.model_explanation.risk_reducing?.length ? (
                        <ul className="list-unstyled mb-0">
                          {prediction.model_explanation.risk_reducing.map((item) => (
                            <li className="d-flex justify-content-between gap-3 py-1" key={item.feature}>
                              <span>{item.feature}</span>
                              <span className="text-body-secondary text-nowrap">{Number(item.log_odds_contribution).toFixed(3)} log-odds</span>
                            </li>
                          ))}
                        </ul>
                      ) : <p className="small text-body-secondary mb-0">No score-lowering factor was available for this profile.</p>}
                    </div>
                  </div>
                </div>
                <p className="small text-body-secondary mt-3 mb-0">
                  {prediction.model_explanation.note}
                </p>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  )
}

function Login({ onLogin, loading, error }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  function submit(event) {
    event.preventDefault()
    onLogin(username, password)
  }

  return (
    <main className="container-fluid min-vh-100 d-flex align-items-center justify-content-center bg-body-tertiary py-5">
      <section className="card border-0 shadow-lg w-100" style={{ maxWidth: 440 }}>
        <div className="card-body p-4 p-md-5">
          <div
            className="rounded-3 bg-primary text-white d-flex align-items-center justify-content-center fw-bold mb-4"
            style={{ width: 52, height: 52, fontSize: '1.15rem' }}
          >
            CR
          </div>

          <p className="text-primary fw-bold small text-uppercase mb-2">
            Customer Intelligence
          </p>
          <h1 className="h3 fw-bold mb-2">Welcome back</h1>
          <p className="text-body-secondary mb-4">
            Sign in to explore churn risk and customer retention opportunities.
          </p>

          <form onSubmit={submit}>
            <div className="mb-3">
              <label htmlFor="username" className="form-label fw-semibold">
                Username
              </label>
              <input
                id="username"
                className="form-control form-control-lg"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Enter your username"
                autoComplete="username"
                required
              />
            </div>

            <div className="mb-3">
              <label htmlFor="password" className="form-label fw-semibold">
                Password
              </label>
              <input
                id="password"
                type="password"
                className="form-control form-control-lg"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Enter your password"
                autoComplete="current-password"
                required
              />
            </div>

            {error && (
              <div className="alert alert-danger" role="alert">
                {error}
              </div>
            )}

            <button
              className="btn btn-primary btn-lg w-100"
              disabled={loading}
              type="submit"
            >
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <p className="small text-body-secondary text-center mt-4 mb-0">
            Secure access · Powered by your retention platform
          </p>
        </div>
      </section>
    </main>
  )
}

function Dashboard({ token, role, onLogout, theme, onToggleTheme }) {
  const canManageDataset = role === 'ADMIN' || role === 'OPERATIONS'
  const canManageActions = role === 'ADMIN' || role === 'RETENTION_AGENT'
  const [data, setData] = useState(null)
  const [outcomeSummary, setOutcomeSummary] = useState(null)
  const [baselineCohort, setBaselineCohort] = useState(null)
  const [comparison, setComparison] = useState(null)
  const [comparisonStart, setComparisonStart] = useState('')
  const [comparisonLoading, setComparisonLoading] = useState(false)
  const [comparisonError, setComparisonError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [regionFilter, setRegionFilter] = useState('all')
  const [segmentFilter, setSegmentFilter] = useState('all')
  const [tenureFilter, setTenureFilter] = useState('all')
  const [selectedCustomerId, setSelectedCustomerId] = useState('')
  const [retentionActions, setRetentionActions] = useState({})
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')
  const [playbookMessage, setPlaybookMessage] = useState('')
  const [page, setPage] = useState(1)
  const [lastUpdated, setLastUpdated] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [uploadMessage, setUploadMessage] = useState('')
  const [uploadError, setUploadError] = useState('')
  const [baselineUploading, setBaselineUploading] = useState(false)
  const [baselineMessage, setBaselineMessage] = useState('')
  const [baselineError, setBaselineError] = useState('')

  const loadBaselineCohort = useCallback(async () => {
    try {
      const response = await fetch(`${BASELINE_URL}/latest`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      })
      if (response.status === 404) {
        setBaselineCohort(null)
        return
      }
      if (!response.ok) throw new Error('Historical baseline is unavailable.')
      setBaselineCohort(await response.json())
    } catch {
      setBaselineCohort(null)
    }
  }, [token])

  const loadOutcomeSummary = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/retention-actions/outcome-summary', {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      })
      if (!response.ok) throw new Error('Outcome summary is unavailable.')
      setOutcomeSummary(await response.json())
    } catch {
      setOutcomeSummary(null)
    }
  }, [token])

  const loadPredictions = useCallback(async () => {
    try {
      const response = await fetch(BULK_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
        },
      })

      if (response.status === 401 || response.status === 403) {
        throw new Error('Your session may have expired. Please sign in again.')
      }

      if (!response.ok) {
        throw new Error(`Could not load predictions (HTTP ${response.status}).`)
      }

      const result = await response.json()

      if (!Array.isArray(result.customers)) {
        throw new Error('The bulk prediction response has no customers list.')
      }

      setData(result)
      setLastUpdated(new Date())
      await loadOutcomeSummary()
      await loadBaselineCohort()
    } catch (err) {
      setError(err.message || 'Unable to connect to the backend.')
    } finally {
      setLoading(false)
    }
  }, [loadBaselineCohort, loadOutcomeSummary, token])

  function refreshPredictions() {
    setLoading(true)
    setError('')
    loadPredictions()
  }

  async function toggleCustomerProfile(customerId) {
    setSelectedCustomerId((current) => current === customerId ? '' : customerId)
    setActionError('')
    if (Object.prototype.hasOwnProperty.call(retentionActions, customerId)) return

    setActionLoading(true)
    try {
      const response = await fetch(`/api/v1/customers/${encodeURIComponent(customerId)}/retention-actions`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      })
      if (!response.ok) throw new Error(`Could not load action history (HTTP ${response.status}).`)
      const actions = await response.json()
      setRetentionActions((current) => ({ ...current, [customerId]: actions }))
    } catch (err) {
      setActionError(err.message || 'Could not load retention action history.')
    } finally {
      setActionLoading(false)
    }
  }

  async function assignRecommendation(customer, recommendation) {
    setActionLoading(true)
    setActionError('')
    try {
      const response = await fetch(
        `/api/v1/customers/${encodeURIComponent(customer.customerId)}/retention-actions`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(recommendation),
        },
      )
      const result = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(result.message || `Could not assign action (HTTP ${response.status}).`)
      setRetentionActions((current) => ({
        ...current,
        [customer.customerId]: [result, ...(current[customer.customerId] ?? [])],
      }))
      await loadPredictions()
    } catch (err) {
      setActionError(err.message || 'Could not assign this retention action.')
    } finally {
      setActionLoading(false)
    }
  }

  async function runRetentionPlaybooks() {
    setActionLoading(true)
    setActionError('')
    setPlaybookMessage('')
    try {
      const response = await fetch('/api/v1/retention-actions/assign-high-risk', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      })
      const result = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(result.message || `Could not run playbooks (HTTP ${response.status}).`)
      const count = Number(result.assignedCount ?? 0)
      setPlaybookMessage(count
        ? `${count} recommended retention ${count === 1 ? 'action was' : 'actions were'} assigned to uncovered active high-risk customers.`
        : 'No eligible uncovered customers needed an action. Refresh predictions before assigning actions to newly imported records.')
      await loadPredictions()
    } catch (err) {
      setActionError(err.message || 'Could not run the retention playbooks.')
    } finally {
      setActionLoading(false)
    }
  }

  async function updateRetentionAction(actionId, customerId, status) {
    setActionLoading(true)
    setActionError('')
    try {
      const response = await fetch(`/api/v1/retention-actions/${actionId}/status`, {
        method: 'PATCH',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ status }),
      })
      const result = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(result.message || `Could not update action (HTTP ${response.status}).`)
      setRetentionActions((current) => ({
        ...current,
        [customerId]: (current[customerId] ?? []).map((action) =>
          action.id === actionId ? result : action,
        ),
      }))
      await loadPredictions()
    } catch (err) {
      setActionError(err.message || 'Could not update this retention action.')
    } finally {
      setActionLoading(false)
    }
  }

  async function updateFollowUpOutcome(actionId, customerId, outcome) {
    setActionLoading(true)
    setActionError('')
    try {
      const response = await fetch(`/api/v1/retention-actions/${actionId}/outcome`, {
        method: 'PATCH',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ outcome }),
      })
      const result = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(result.message || `Could not record follow-up outcome (HTTP ${response.status}).`)
      setRetentionActions((current) => ({
        ...current,
        [customerId]: (current[customerId] ?? []).map((action) =>
          action.id === actionId ? result : action,
        ),
      }))
      await loadOutcomeSummary()
    } catch (err) {
      setActionError(err.message || 'Could not record this follow-up outcome.')
    } finally {
      setActionLoading(false)
    }
  }

  useEffect(() => {
    queueMicrotask(() => {
      loadPredictions()
    })
  }, [loadPredictions])

  async function uploadDataset(event) {
    event.preventDefault()
    const form = event.currentTarget
    const file = form.elements.datasetFile.files?.[0]

    if (!file) {
      setUploadError('Choose a CSV or XLSX file first.')
      setUploadMessage('')
      return
    }

    setUploading(true)
    setUploadError('')
    setUploadMessage('')

    try {
      const body = new FormData()
      body.append('file', file)

      const response = await fetch(UPLOAD_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
        },
        body,
      })
      const result = await response.json().catch(() => ({}))

      if (!response.ok) {
        throw new Error(result.message || `Upload failed (HTTP ${response.status}).`)
      }

      setUploadMessage(
        `${result.recordsLoaded ?? 0} customer records loaded. Refreshing predictions…`,
      )
      form.reset()
      setLoading(true)
      setError('')
      await loadPredictions()
    } catch (err) {
      setUploadError(err.message || 'Unable to upload this dataset.')
    } finally {
      setUploading(false)
    }
  }

  async function uploadHistoricalBaseline(event) {
    event.preventDefault()
    const form = event.currentTarget
    const file = form.elements.baselineFile.files?.[0]
    if (!file) {
      setBaselineError('Choose a baseline CSV file first.')
      setBaselineMessage('')
      return
    }

    setBaselineUploading(true)
    setBaselineError('')
    setBaselineMessage('')
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await fetch(BASELINE_URL, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
        body,
      })
      const result = await response.json().catch(() => ({}))
      if (!response.ok) {
        throw new Error(result.message || `Baseline upload failed (HTTP ${response.status}).`)
      }
      setBaselineMessage(
        `Saved ${result.eligibleHighRiskCustomers.toLocaleString()} high-risk customers across ${result.segmentsLoaded} segments (${result.periodStart} to ${result.periodEnd}). Live customer data was not changed.`,
      )
      form.reset()
      await loadBaselineCohort()
    } catch (err) {
      setBaselineError(err.message || 'Unable to save this historical baseline.')
    } finally {
      setBaselineUploading(false)
    }
  }

  async function loadCohortComparison(event) {
    event.preventDefault()
    if (!comparisonStart) {
      setComparisonError('Choose the first day of the 90-day intervention window.')
      return
    }
    setComparisonLoading(true)
    setComparisonError('')
    setComparison(null)
    try {
      const query = new URLSearchParams({ interventionStart: comparisonStart })
      const response = await fetch(`${BASELINE_URL}/comparison?${query}`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      })
      const result = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(result.message || `Comparison failed (HTTP ${response.status}).`)
      setComparison(result)
    } catch (err) {
      setComparisonError(err.message || 'Unable to calculate the cohort comparison.')
    } finally {
      setComparisonLoading(false)
    }
  }

  const customers = data?.customers ?? EMPTY_CUSTOMERS

  const stats = useMemo(() => {
    return calculateDashboardStats(data, customers)
  }, [data, customers])

  const distribution = useMemo(() => {
    const counts = { Low: 0, Medium: 0, High: 0 }

    customers.forEach((customer) => {
      counts[riskBand(riskPercent(customer))] += 1
    })

    const total = customers.length || 1

    return [
      { label: 'Low risk', count: counts.Low, color: 'success' },
      { label: 'Medium risk', count: counts.Medium, color: 'warning' },
      { label: 'High risk', count: counts.High, color: 'danger' },
    ].map((item) => ({
      ...item,
      percent: (item.count / total) * 100,
    }))
  }, [customers])

  const churnBreakdowns = useMemo(
    () => calculateChurnBreakdowns(customers),
    [customers],
  )

  const filteredCustomers = useMemo(() => {
    const query = search.trim().toLowerCase()

    return customers.filter((customer) => {
      const id = String(customer.customerId ?? '').toLowerCase()
      const region = String(customer.region ?? '').toLowerCase()
      const segment = String(customer.customerSegment ?? '').toLowerCase()
      const tenure = tenureBand(Number(customer.tenureMonths ?? 0))
      const risk = riskPercent(customer)

      const matchesSearch =
        !query || id.includes(query) || region.includes(query) || segment.includes(query)
      const matchesFilter =
        filter === 'all' ||
        (filter === 'targeted' && customer.targeted) ||
        (filter === 'high' && String(customer.churned ?? '').toLowerCase() === 'no' && risk >= 50) ||
        (filter === 'actual-churn' && String(customer.churned).toLowerCase() === 'yes') ||
        (filter === 'churn' && customer.predictedChurn)

      return (
        matchesSearch &&
        matchesFilter &&
        (regionFilter === 'all' || customer.region === regionFilter) &&
        (segmentFilter === 'all' || customer.customerSegment === segmentFilter) &&
        (tenureFilter === 'all' || tenure === tenureFilter)
      )
    })
  }, [customers, search, filter, regionFilter, segmentFilter, tenureFilter])

  const pageCount = Math.max(1, Math.ceil(filteredCustomers.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)

  const visibleCustomers = filteredCustomers.slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE,
  )

  function changeSearch(value) {
    setSearch(value)
    setPage(1)
  }

  function changeFilter(value) {
    setFilter(value)
    setPage(1)
  }

  function changeProfileFilter(setter, value) {
    setter(value)
    setPage(1)
  }

  if (loading && !data) {
    return (
      <main className="container min-vh-100 d-flex flex-column align-items-center justify-content-center text-center">
        <div className="spinner-border text-primary mb-3" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
        <h2 className="h4 fw-bold">Preparing your dashboard</h2>
        <p className="text-body-secondary">
          Retrieving customer churn predictions…
        </p>
        {error && <div className="alert alert-danger">{error}</div>}
        {error && (
          <button className="btn btn-primary mb-2" onClick={refreshPredictions}>
            Try again
          </button>
        )}
        <button className="btn btn-outline-secondary" onClick={onLogout}>
          Sign out
        </button>
      </main>
    )
  }

  return (
    <div className="container-fluid py-3 py-lg-4 px-3 px-lg-4">
      <header className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3 border-bottom pb-3 mb-4">
        <div className="d-flex align-items-center gap-3">
          <div
            className="rounded-3 bg-primary text-white fw-bold d-flex align-items-center justify-content-center"
            style={{ width: 46, height: 46 }}
          >
            CR
          </div>
          <div>
            <h1 className="h5 fw-bold mb-0">RetainIQ</h1>
            <span className="small text-body-secondary">
              Customer intelligence
            </span>
          </div>
        </div>

        <div className="d-flex flex-wrap align-items-center gap-2">
          <span className="badge text-bg-secondary rounded-pill px-3 py-2">
            {role ? role.replaceAll('_', ' ') : 'Loading permissions…'}
          </span>
          <span className="badge text-bg-success rounded-pill px-3 py-2">
            <span className="me-1">●</span>
            {error ? 'Showing saved results' : 'Prediction dashboard'}
          </span>
          <button
            type="button"
            className="btn btn-outline-secondary"
            onClick={onToggleTheme}
            aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
          >
            {theme === 'dark' ? '☀️ Light mode' : '🌙 Dark mode'}
          </button>
          <button className="btn btn-outline-danger" onClick={onLogout}>
            Sign out
          </button>
        </div>
      </header>

      <main>
        <section className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3 mb-4">
          <div>
            <p className="text-primary fw-bold small text-uppercase mb-2">
              Customer Retention
            </p>
            <h2 className="display-6 fw-bold mb-2">Churn intelligence</h2>
            <p className="text-body-secondary mb-0">
              Understand customer risk and prioritize your retention efforts.
            </p>
          </div>

          <button
            className="btn btn-primary px-4 py-2"
            onClick={refreshPredictions}
            disabled={loading}
          >
            {loading ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" />
                Refreshing…
              </>
            ) : (
              '↻ Refresh predictions'
            )}
          </button>
        </section>

        {canManageDataset && <section className="card border-0 shadow-sm mb-4">
          <div className="card-body p-3 p-md-4">
            <div className="row g-3 align-items-end">
              <div className="col-12 col-lg">
                <h3 className="h5 fw-bold mb-1">Upload customer data</h3>
                <p className="small text-body-secondary mb-0">
                  Choose a validated CSV or Excel workbook to load customer records.
                </p>
              </div>
              <div className="col-12 col-lg-7">
                <form className="d-flex flex-column flex-sm-row gap-2" onSubmit={uploadDataset}>
                  <label className="visually-hidden" htmlFor="datasetFile">
                    Customer dataset CSV or XLSX file
                  </label>
                  <input
                    className="form-control"
                    id="datasetFile"
                    name="datasetFile"
                    type="file"
                    accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    disabled={uploading}
                    required
                  />
                  <button className="btn btn-outline-primary text-nowrap" type="submit" disabled={uploading}>
                    {uploading ? 'Uploading…' : 'Upload dataset'}
                  </button>
                </form>
              </div>
            </div>
            {uploadError && (
              <div className="alert alert-danger mt-3 mb-0" role="alert">
                {uploadError}
              </div>
            )}
            {uploadMessage && (
              <div className="alert alert-success mt-3 mb-0" role="status">
                {uploadMessage}
              </div>
            )}
          </div>
        </section>}

        <section className="card border-0 shadow-sm mb-4" aria-labelledby="historicalBaselineTitle">
          <div className="card-body p-3 p-md-4">
            <div className="row g-3 align-items-start">
              <div className="col-12 col-lg">
                <h3 className="h5 fw-bold mb-1" id="historicalBaselineTitle">Historical 90-day comparison baseline</h3>
                <p className="small text-body-secondary mb-0">
                  Upload segment-level counts from a completed historical 90-day period. This is stored separately and does not replace live customer data.
                </p>
                <p className="small text-body-secondary mt-2 mb-0">
                  CSV headers: <code>Period_Start,Period_End,Customer_Segment,High_Risk_Customers,Churned_Within_90D</code>. Use ISO dates, the same dates on every row, unique customers per segment, and high-risk eligibility at probability ≥ 50%. Churned customers must be included in the high-risk count.
                </p>
                <p className="small text-body-secondary mt-2 mb-0">Historical baseline uploads are available to ADMIN and OPERATIONS accounts.</p>
              </div>
              {canManageDataset && <div className="col-12 col-lg-5">
                <form className="d-flex flex-column flex-sm-row gap-2" onSubmit={uploadHistoricalBaseline}>
                  <label className="visually-hidden" htmlFor="baselineFile">Historical baseline CSV file</label>
                  <input
                    className="form-control"
                    id="baselineFile"
                    name="baselineFile"
                    type="file"
                    accept=".csv,text/csv"
                    disabled={baselineUploading}
                    required
                  />
                  <button className="btn btn-outline-primary text-nowrap" type="submit" disabled={baselineUploading}>
                    {baselineUploading ? 'Saving…' : 'Save baseline'}
                  </button>
                </form>
                {baselineError && <div className="alert alert-danger mt-3 mb-0" role="alert">{baselineError}</div>}
                {baselineMessage && <div className="alert alert-success mt-3 mb-0" role="status">{baselineMessage}</div>}
              </div>}
            </div>
            {baselineCohort && (
              <div className="border rounded-3 p-3 mt-3">
                <div className="d-flex flex-wrap justify-content-between gap-2">
                  <strong>Saved baseline: {baselineCohort.periodStart} to {baselineCohort.periodEnd}</strong>
                  <span className="badge text-bg-secondary">
                    {formatPercent(baselineCohort.observedChurnRatePercent)} historical churn rate
                  </span>
                </div>
                <p className="small text-body-secondary mt-2 mb-2">
                  {baselineCohort.churnedCustomers.toLocaleString()} of {baselineCohort.eligibleHighRiskCustomers.toLocaleString()} high-risk customers churned in the historical period. Uploaded by {baselineCohort.importedBy}.
                </p>
                <div className="d-flex flex-wrap gap-2">
                  {baselineCohort.segments.map((segment) => (
                    <span className="badge rounded-pill text-bg-light border" key={segment.customerSegment}>
                      {segment.customerSegment}: {segment.churnedCustomers}/{segment.eligibleHighRiskCustomers} churned · {formatPercent(segment.observedChurnRatePercent)}
                    </span>
                  ))}
                </div>
              </div>
            )}
            {baselineCohort && (
              <div className="border-top mt-3 pt-3">
                <h4 className="h6 fw-bold">Compare a completed action cohort</h4>
                <p className="small text-body-secondary">
                  Select the first day customers received an intervention. The next 90 days are compared with the saved baseline. Each customer is counted once, using their first assigned action in that window. A result appears only when every baseline segment has matching customer counts, recorded outcomes, and a saved model score of at least 50% captured within 24 hours before assignment. Keep segment definitions consistent.
                </p>
                <form className="d-flex flex-column flex-sm-row gap-2 align-items-sm-end" onSubmit={loadCohortComparison}>
                  <div>
                    <label className="form-label small fw-semibold" htmlFor="comparisonStart">Intervention window start</label>
                    <input className="form-control" id="comparisonStart" type="date" value={comparisonStart} onChange={(event) => setComparisonStart(event.target.value)} required />
                  </div>
                  <button className="btn btn-primary" type="submit" disabled={comparisonLoading}>
                    {comparisonLoading ? 'Calculating…' : 'Compare 90-day cohorts'}
                  </button>
                </form>
                {comparisonError && <div className="alert alert-danger mt-3 mb-0" role="alert">{comparisonError}</div>}
                {comparison && (
                  <div className="rounded-3 bg-body-tertiary p-3 mt-3" aria-live="polite">
                    <div className="d-flex flex-wrap justify-content-between gap-2">
                      <strong>Comparison status: {comparison.status}</strong>
                      {comparison.relativeReductionPercent !== null && (
                        <span className={`badge ${comparison.metFifteenPercentTarget ? 'text-bg-success' : 'text-bg-warning'}`}>
                          {formatPercent(comparison.relativeReductionPercent)} observed relative reduction · 15% benchmark {comparison.metFifteenPercentTarget ? 'reached' : 'not reached'}
                        </span>
                      )}
                    </div>
                    <p className="small text-body-secondary mt-2">{comparison.statusMessage}</p>
                    <p className="small mb-2">
                      Model high-risk eligibility: <strong>{comparison.riskEligibilityVerified ? 'verified from saved assignment scores' : 'not verified for every action'}</strong>
                    </p>
                    {comparison.status === 'COMPLETE' && (
                      <p className="mb-2">
                        Historical {formatPercent(comparison.baselineChurnRatePercent)} ({comparison.baselineChurnedCustomers}/{comparison.baselineEligibleCustomers}) → intervention {formatPercent(comparison.interventionChurnRatePercent)} ({comparison.interventionChurnedCustomers}/{comparison.interventionCustomers}).
                      </p>
                    )}
                    <div className="d-flex flex-wrap gap-2">
                      {comparison.segments.map((segment) => (
                        <span className="badge rounded-pill text-bg-light border" key={segment.customerSegment}>
                          {segment.customerSegment}: {segment.interventionChurnedCustomers}/{segment.interventionCustomers} intervention churn · {segment.pendingOutcomes} pending
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
            <p className="small text-body-secondary mt-3 mb-0">
              Comparison is descriptive and does not prove actions caused the change. Baseline membership comes from uploaded high-risk counts; intervention membership uses saved model scores on the first assigned action per customer.
            </p>
          </div>
        </section>

        {error && (
          <div className="alert alert-warning d-flex justify-content-between align-items-start gap-3">
            <div>
              <strong>Unable to refresh predictions.</strong>
              <div>{error}</div>
              {data && (
                <small>Showing the last successfully loaded results.</small>
              )}
            </div>
            <button
              className="btn btn-sm btn-outline-dark"
              onClick={refreshPredictions}
              disabled={loading}
            >
              Retry
            </button>
          </div>
        )}

        {canManageActions && <section className="card border-0 shadow-sm mb-3" aria-label="Automated retention playbooks">
          <div className="card-body d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3 p-3 p-md-4">
            <div>
              <h3 className="h6 fw-bold mb-1">Retention playbooks</h3>
              <p className="small text-body-secondary mb-0">
                Assign recommended actions to {Math.max(stats.highRisk - stats.actionAssigned, 0).toLocaleString()} uncovered active high-risk customers. This creates internal assignments only; it does not contact customers.
              </p>
            </div>
            <button
              className="btn btn-outline-primary text-nowrap"
              onClick={runRetentionPlaybooks}
              disabled={actionLoading || loading || stats.highRisk <= stats.actionAssigned}
            >
              {actionLoading ? 'Assigning…' : 'Run retention playbooks'}
            </button>
          </div>
          {actionError && <div className="alert alert-danger m-3 mt-0 mb-0" role="alert">{actionError}</div>}
          {playbookMessage && <div className="alert alert-success m-3 mt-0 mb-0" role="status">{playbookMessage}</div>}
        </section>}

        <section className="row g-3 mb-4">
          <StatCard
            icon="↘"
            label="Observed churn rate"
            value={formatPercent(stats.actualChurnRate)}
            detail={`${stats.actualChurned.toLocaleString()} of ${stats.total.toLocaleString()} loaded records labeled churned`}
            tone="purple"
          />
          <StatCard
            icon="✓"
            label="Observed retention rate"
            value={formatPercent(stats.actualRetentionRate)}
            detail="Complement of the observed churn rate in this dataset"
            tone="blue"
          />
          <StatCard
            icon="⚑"
            label="High-risk customers"
            value={stats.highRisk.toLocaleString()}
            detail="Active customers with model probability at least 50%"
            tone="orange"
          />
          <StatCard
            icon="◎"
            label="Retention-action coverage"
            value={stats.actionCoverage === null ? 'N/A' : formatPercent(stats.actionCoverage)}
            detail={stats.actionCoverage === null
              ? 'No active high-risk customers in this dataset'
              : `${stats.actionAssigned} of ${stats.highRisk} active high-risk customers have an assigned action`}
            tone="red"
          />
        </section>

        <section className="card border-0 shadow-sm mb-4" aria-labelledby="followUpSummaryTitle">
          <div className="card-body p-4">
            <div className="mb-3">
              <h3 className="h5 fw-bold mb-1" id="followUpSummaryTitle">
                Retention-action follow-up outcomes
              </h3>
              <p className="small text-body-secondary mb-0">
                Descriptive counts per assigned action; repeat actions for one customer are counted separately.
              </p>
            </div>
            <div className="row g-3">
              <div className="col-12 col-md-4">
                <article className="h-100 rounded-3 border p-3">
                  <p className="small fw-semibold text-body-secondary mb-2">Observed retained share</p>
                  <div className="h3 fw-bold mb-1">
                    {outcomeSummary?.observedRetainedSharePercent == null
                      ? 'N/A'
                      : formatPercent(outcomeSummary.observedRetainedSharePercent)}
                  </div>
                  <p className="small text-body-secondary mb-0">
                    {outcomeSummary
                      ? `${outcomeSummary.retained} retained of ${outcomeSummary.outcomesRecorded} recorded outcomes`
                      : 'Outcome summary unavailable'}
                  </p>
                </article>
              </div>
              <div className="col-12 col-md-4">
                <article className="h-100 rounded-3 border p-3">
                  <p className="small fw-semibold text-body-secondary mb-2">Recorded churn outcomes</p>
                  <div className="h3 fw-bold mb-1">{outcomeSummary?.churned ?? '—'}</div>
                  <p className="small text-body-secondary mb-0">Churn observed after an action was assigned</p>
                </article>
              </div>
              <div className="col-12 col-md-4">
                <article className="h-100 rounded-3 border p-3">
                  <p className="small fw-semibold text-body-secondary mb-2">Outcomes not recorded</p>
                  <div className="h3 fw-bold mb-1">{outcomeSummary?.pendingOutcomes ?? '—'}</div>
                  <p className="small text-body-secondary mb-0">
                    {outcomeSummary
                      ? `${outcomeSummary.outcomesRecorded} of ${outcomeSummary.totalActions} actions have a recorded outcome`
                      : 'Outcome summary unavailable'}
                  </p>
                </article>
              </div>
            </div>
            <p className="small text-body-secondary mt-3 mb-0">
              Pending actions are excluded from the retained share; churn may be recorded when observed, while retention is recorded after 90 days. These observations do not show that an action caused retention or measure the 15% relative-reduction target, which still requires a defined cohort and comparable baseline or control group.
            </p>
          </div>
        </section>

        <section className="card border-0 shadow-sm mb-4" aria-labelledby="projectTargetsTitle">
          <div className="card-body p-4">
            <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-start gap-2 mb-3">
              <div>
                <h3 className="h5 fw-bold mb-1" id="projectTargetsTitle">
                  Project targets and measurement status
                </h3>
                <p className="small text-body-secondary mb-0">
                  Targets below are goals from the project brief. The cards above show measurements from this loaded dataset.
                </p>
              </div>
              <span className="badge rounded-pill text-bg-secondary">
                Targets, not current results
              </span>
            </div>

            <div className="row g-3">
              <div className="col-12 col-md-4">
                <article className="h-100 rounded-3 border p-3">
                  <h4 className="h6 fw-bold mb-2">Churner recall</h4>
                  <p className="mb-1"><strong>Target: at least 70%</strong></p>
                  <p className="mb-1">
                    <strong>Loaded-snapshot recall: {stats.observedChurnRecall === null ? 'N/A' : formatPercent(stats.observedChurnRecall)}</strong>
                  </p>
                  <p className="small text-body-secondary mb-2">
                    {stats.observedChurnRecall === null
                      ? 'No churned customer labels are available to calculate recall.'
                      : `${stats.truePositives} of ${stats.actualChurned} labeled churners were flagged at the model’s 40% screening threshold.`}
                  </p>
                  <p className="small text-body-secondary mb-0">
                    {stats.observedChurnRecall === null
                      ? 'Offline fixed-holdout recall is 74.4%; the loaded snapshot has no positive cases to compare.'
                      : stats.observedChurnRecall >= 70
                        ? 'This loaded snapshot is at or above the target. The 74.4% offline fixed-holdout result is a separate measurement.'
                        : 'This loaded snapshot is below the target. The 74.4% offline fixed-holdout result is a separate measurement.'}
                    {' '}A loaded snapshot is not independent validation unless it is a separate labeled evaluation dataset.
                  </p>
                </article>
              </div>
              <div className="col-12 col-md-4">
                <article className="h-100 rounded-3 border p-3">
                  <h4 className="h6 fw-bold mb-2">Retention-action coverage</h4>
                  <p className="mb-1"><strong>Target: 100%</strong></p>
                  <p className="small text-body-secondary mb-0">
                    Current coverage is shown above for active customers with predicted churn risk of at least 50%.
                  </p>
                </article>
              </div>
              <div className="col-12 col-md-4">
                <article className="h-100 rounded-3 border p-3">
                  <h4 className="h6 fw-bold mb-2">High-risk churn reduction</h4>
                  <p className="mb-1"><strong>Target: 15% relative reduction over 90 days</strong></p>
                  <p className="small text-body-secondary mb-0">
                    Not measured yet. It requires a defined campaign cohort and 90-day follow-up outcomes.
                  </p>
                </article>
              </div>
            </div>
          </div>
        </section>

        <WhatIfScoring customers={customers} token={token} />

        <section className="mb-4" aria-labelledby="observedBreakdownsTitle">
          <div className="mb-3">
            <h3 className="h5 fw-bold mb-1" id="observedBreakdownsTitle">
              Observed churn by customer group
            </h3>
            <p className="small text-body-secondary mb-0">
              Calculated from stored churn labels; these are associations, not proof of cause.
            </p>
          </div>
          <div className="row g-3">
            <ChurnBreakdown title="Customer segment" items={churnBreakdowns.segments} />
            <ChurnBreakdown title="Region" items={churnBreakdowns.regions} />
            <ChurnBreakdown title="Customer tenure" items={churnBreakdowns.tenure} />
          </div>
        </section>

        <section className="row g-3 mb-4">
          <div className="col-12 col-xl-7">
            <article className="card border-0 shadow-sm h-100">
              <div className="card-body p-3 p-md-4">
                <div className="d-flex justify-content-between align-items-start gap-3 mb-4">
                  <div>
                    <h3 className="h5 fw-bold mb-1">Churn risk distribution</h3>
                    <p className="text-body-secondary small mb-0">
                      Low &lt;30% · Medium 30–&lt;50% · High ≥50%. Predicted churn uses a separate 40% cutoff.
                    </p>
                  </div>
                  <span className="badge bg-body-secondary text-body">
                    All customers
                  </span>
                </div>

                <div className="d-flex align-items-baseline gap-2 mb-4">
                  <span className="display-6 fw-bold">
                    {customers.length.toLocaleString()}
                  </span>
                  <span className="text-body-secondary">scored customers</span>
                </div>

                <div className="d-flex flex-column gap-4">
                  {distribution.map((item) => (
                    <div key={item.label}>
                      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
                        <span className="fw-semibold">{item.label}</span>
                        <span className="text-body-secondary small">
                          <strong className="text-body me-2">
                            {item.count.toLocaleString()}
                          </strong>
                          {item.percent.toFixed(1)}%
                        </span>
                      </div>
                      <div
                        className="progress"
                        role="progressbar"
                        aria-label={item.label}
                        aria-valuenow={item.percent}
                        aria-valuemin="0"
                        aria-valuemax="100"
                        style={{ height: 10 }}
                      >
                        <div
                          className={`progress-bar bg-${item.color}`}
                          style={{ width: `${item.percent}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>

                <p className="small text-body-secondary border-top pt-3 mt-4 mb-0">
                  Risk bands are dashboard categories, not the model's
                  classification threshold.
                </p>
              </div>
            </article>
          </div>

          <div className="col-12 col-xl-5">
            <article className="card border-0 shadow-sm h-100">
              <div className="card-body p-3 p-md-4">
                <div className="d-flex justify-content-between align-items-start gap-3 mb-4">
                  <div>
                    <h3 className="h5 fw-bold mb-1">Targeting capacity</h3>
                    <p className="text-body-secondary small mb-0">
                      Retention outreach allocation
                    </p>
                  </div>
                  <span className="badge text-bg-primary fs-6">◎</span>
                </div>

                <div className="display-4 fw-bold text-primary mb-2">
                  {formatPercent(stats.capacity * 100)}
                </div>
                <p className="text-body-secondary">
                  Targeting capacity configured by the bulk prediction service.
                </p>

                <div
                  className="progress mb-4"
                  role="progressbar"
                  aria-label="Targeting capacity"
                  aria-valuenow={Math.max(
                    0,
                    Math.min(100, stats.capacity * 100),
                  )}
                  aria-valuemin="0"
                  aria-valuemax="100"
                  style={{ height: 12 }}
                >
                  <div
                    className="progress-bar"
                    style={{
                      width: `${Math.max(0, Math.min(100, stats.capacity * 100))}%`,
                    }}
                  />
                </div>

                <div className="row g-3">
                  <div className="col-6">
                    <div className="rounded-3 bg-body-tertiary p-3">
                      <div className="h3 fw-bold mb-1">
                        {stats.targeted.toLocaleString()}
                      </div>
                      <div className="small text-body-secondary">Targeted</div>
                    </div>
                  </div>
                  <div className="col-6">
                    <div className="rounded-3 bg-body-tertiary p-3">
                      <div className="h3 fw-bold mb-1">
                        {Math.max(0, stats.total - stats.targeted).toLocaleString()}
                      </div>
                      <div className="small text-body-secondary">
                        Not targeted
                      </div>
                    </div>
                  </div>
                </div>

                <p className="small text-body-secondary border-top pt-3 mt-4 mb-0">
                  Customer selection follows the backend's ranking and
                  allocation rules.
                </p>
              </div>
            </article>
          </div>
        </section>

        <section className="card border-0 shadow-sm">
          <div className="card-body p-3 p-md-4">
            <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3 mb-4">
              <div>
                <h3 className="h5 fw-bold mb-1">Customer risk register</h3>
                <p className="text-body-secondary small mb-0">
                  Ranked results returned by the prediction service
                </p>
              </div>
              <span className="badge bg-body-secondary text-body rounded-pill px-3 py-2">
                {filteredCustomers.length.toLocaleString()} results
              </span>
            </div>

            <div className="row g-2 mb-3">
              <div className="col-12 col-lg-4">
                <label htmlFor="customerSearch" className="visually-hidden">
                  Search ID, region, or segment
                </label>
                <input
                  id="customerSearch"
                  className="form-control"
                  value={search}
                  onChange={(event) => changeSearch(event.target.value)}
                  placeholder="Search ID, region, or segment…"
                />
              </div>

              <div className="col-12 col-sm-6 col-lg-2">
                <label htmlFor="customerFilter" className="visually-hidden">
                  Filter customers
                </label>
                <select
                  id="customerFilter"
                  className="form-select"
                  value={filter}
                  onChange={(event) => changeFilter(event.target.value)}
                >
                  <option value="all">All customers</option>
                  <option value="targeted">Targeted only</option>
                  <option value="high">High risk (50%+)</option>
                  <option value="actual-churn">Actually churned</option>
                  <option value="churn">Predicted churn</option>
                </select>
              </div>

              <div className="col-12 col-sm-6 col-lg-2">
                <label htmlFor="regionFilter" className="visually-hidden">Filter by region</label>
                <select
                  id="regionFilter"
                  className="form-select"
                  value={regionFilter}
                  onChange={(event) => changeProfileFilter(setRegionFilter, event.target.value)}
                >
                  <option value="all">All regions</option>
                  {[...new Set(customers.map((customer) => customer.region).filter(Boolean))].sort().map((region) => (
                    <option key={region} value={region}>{region}</option>
                  ))}
                </select>
              </div>

              <div className="col-12 col-sm-6 col-lg-2">
                <label htmlFor="segmentFilter" className="visually-hidden">Filter by segment</label>
                <select
                  id="segmentFilter"
                  className="form-select"
                  value={segmentFilter}
                  onChange={(event) => changeProfileFilter(setSegmentFilter, event.target.value)}
                >
                  <option value="all">All segments</option>
                  {[...new Set(customers.map((customer) => customer.customerSegment).filter(Boolean))].sort().map((segment) => (
                    <option key={segment} value={segment}>{segment}</option>
                  ))}
                </select>
              </div>

              <div className="col-12 col-sm-6 col-lg-2">
                <label htmlFor="tenureFilter" className="visually-hidden">Filter by tenure</label>
                <select
                  id="tenureFilter"
                  className="form-select"
                  value={tenureFilter}
                  onChange={(event) => changeProfileFilter(setTenureFilter, event.target.value)}
                >
                  <option value="all">All tenure</option>
                  <option value="0–12 months">0–12 months</option>
                  <option value="13–24 months">13–24 months</option>
                  <option value="25–48 months">25–48 months</option>
                  <option value="49+ months">49+ months</option>
                </select>
              </div>
            </div>

            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light">
                  <tr>
                    <th scope="col">Customer ID</th>
                    <th scope="col">Observed churn</th>
                    <th scope="col">Region</th>
                    <th scope="col">Segment</th>
                    <th scope="col">Tenure</th>
                    <th scope="col">Churn risk</th>
                    <th scope="col">Risk level</th>
                    <th scope="col">Predicted churn</th>
                    <th scope="col">Targeting status</th>
                    <th scope="col">Profile</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleCustomers.map((customer, index) => {
                    const risk = riskPercent(customer)
                    const band = riskBand(risk)
                    const riskClass = {
                      High: 'danger',
                      Medium: 'warning',
                      Low: 'success',
                    }[band]
                    const profileFields = [
                      ['Age group', customer.ageGroup],
                      ['Monthly spend', `₹${Number(customer.monthlySpendInr).toLocaleString()}`],
                      ['Transactions (90 days)', customer.transactions90d],
                      ['Support tickets (90 days)', customer.supportTickets90d],
                      ['Average response time', `${customer.avgResponseHours} hours`],
                      ['Satisfaction score', `${customer.satisfactionScore} / 5`],
                      ['App logins (30 days)', customer.appLogins30d],
                      ['Days since last purchase', customer.daysSinceLastPurchase],
                      ['Discount usage', `${customer.discountUsagePct}%`],
                      ['Payment failures (90 days)', customer.paymentFailures90d],
                    ]
                    const recommendation = retentionRecommendation(customer)
                    const isActiveHighRisk =
                      String(customer.churned ?? '').toLowerCase() === 'no' && risk >= 50

                    return (
                      <Fragment key={customer.customerId ?? index}>
                      <tr>
                        <td>
                          <span className="fw-semibold">
                            #{customer.customerId ?? '—'}
                          </span>
                        </td>
                        <td>{customer.churned}</td>
                        <td>{customer.region}</td>
                        <td>{customer.customerSegment}</td>
                        <td>{customer.tenureMonths} months</td>
                        <td style={{ minWidth: 145 }}>
                          <div className="d-flex justify-content-between mb-1">
                            <span className="fw-semibold">
                              {formatPercent(risk)}
                            </span>
                          </div>
                          <div
                            className="progress"
                            style={{ height: 6 }}
                            role="progressbar"
                            aria-label={`Churn risk ${formatPercent(risk)}`}
                            aria-valuenow={Math.max(0, Math.min(100, risk))}
                            aria-valuemin="0"
                            aria-valuemax="100"
                          >
                            <div
                              className={`progress-bar bg-${riskClass}`}
                              style={{
                                width: `${Math.max(0, Math.min(100, risk))}%`,
                              }}
                            />
                          </div>
                        </td>
                        <td>
                          <span
                            className={`badge rounded-pill text-bg-${riskClass}`}
                          >
                            {band}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`badge rounded-pill ${
                              customer.predictedChurn
                                ? 'text-bg-danger'
                                : 'text-bg-secondary'
                            }`}
                          >
                            {customer.predictedChurn ? 'Yes' : 'No'}
                          </span>
                        </td>
                        <td>
                          {customer.targeted ? (
                            <span className="badge rounded-pill text-bg-success">
                              ✓ Targeted
                            </span>
                          ) : (
                            <span className="text-body-secondary small">
                              Not targeted
                            </span>
                          )}
                        </td>
                        <td>
                          <button
                            className="btn btn-sm btn-outline-primary text-nowrap"
                            type="button"
                            aria-expanded={selectedCustomerId === customer.customerId}
                            onClick={() => toggleCustomerProfile(customer.customerId)}
                          >
                            {selectedCustomerId === customer.customerId ? 'Hide profile' : 'View profile'}
                          </button>
                        </td>
                      </tr>
                      {selectedCustomerId === customer.customerId && (
                        <tr>
                          <td colSpan="10" className="bg-body-tertiary">
                            <h4 className="h6 fw-bold mb-3">Customer profile · #{customer.customerId}</h4>
                            <dl className="row row-cols-1 row-cols-sm-2 row-cols-xl-3 mb-0">
                              {profileFields.map(([label, value]) => (
                                <div className="col mb-2" key={label}>
                                  <dt className="small text-body-secondary">{label}</dt>
                                  <dd className="mb-0 fw-semibold">{value}</dd>
                                </div>
                              ))}
                            </dl>
                            <div className="border-top mt-3 pt-3">
                              <h5 className="h6 fw-bold">Suggested retention playbook</h5>
                              {isActiveHighRisk ? (
                                <>
                                  <p className="mb-2">
                                    <strong>{recommendation.actionName}:</strong> {recommendation.recommendation}
                                  </p>
                                  <p className="small text-body-secondary">
                                    This rule-based suggestion uses observed profile indicators. Review it before assignment; no message is sent automatically.
                                  </p>
                                  {canManageActions && <button
                                    className="btn btn-sm btn-primary"
                                    type="button"
                                    disabled={actionLoading}
                                    onClick={() => assignRecommendation(customer, recommendation)}
                                  >
                                    {actionLoading ? 'Saving…' : 'Assign this action'}
                                  </button>}
                                </>
                              ) : (
                                <p className="small text-body-secondary mb-0">
                                  Recommendations are shown for active customers at or above the 50% risk threshold.
                                </p>
                              )}
                            </div>
                            <div className="border-top mt-3 pt-3">
                              <h5 className="h6 fw-bold">Assigned actions and audit history</h5>
                              {actionError && <div className="alert alert-danger" role="alert">{actionError}</div>}
                              {retentionActions[customer.customerId] === undefined ? (
                                <p className="small text-body-secondary">{actionLoading ? 'Loading action history…' : 'Action history has not loaded.'}</p>
                              ) : retentionActions[customer.customerId].length ? (
                                <div className="d-flex flex-column gap-3">
                                  {retentionActions[customer.customerId].map((action) => (
                                    <article className="border rounded-3 p-3" key={action.id}>
                                      <div className="d-flex flex-wrap justify-content-between gap-2">
                                        <strong>{action.actionName}</strong>
                                        {canManageActions && <label className="small">
                                          Status{' '}
                                          <select
                                            className="form-select form-select-sm d-inline-block w-auto"
                                            value={action.status}
                                            disabled={actionLoading}
                                            onChange={(event) => updateRetentionAction(action.id, customer.customerId, event.target.value)}
                                          >
                                            <option value="ASSIGNED">Assigned</option>
                                            <option value="CONTACTED">Contacted</option>
                                            <option value="COMPLETED">Completed</option>
                                            <option value="CANCELLED">Cancelled</option>
                                          </select>
                                        </label>}
                                      </div>
                                      <p className="small text-body-secondary mb-2">
                                        {action.riskPercentAtAssignment == null
                                          ? 'Model risk at assignment was not captured for this older action.'
                                          : `Model risk at assignment: ${formatPercent(action.riskPercentAtAssignment)} · scored ${action.riskScoredAtAssignment ? new Date(action.riskScoredAtAssignment).toLocaleString() : 'time unavailable'}`}
                                      </p>
                                      <p className="small text-body-secondary mb-2">{action.recommendation}</p>
                                      {canManageActions && <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
                                        <label className="small fw-semibold" htmlFor={`follow-up-${action.id}`}>
                                          90-day follow-up outcome
                                        </label>
                                        <select
                                          id={`follow-up-${action.id}`}
                                          className="form-select form-select-sm w-auto"
                                          value={action.followUpOutcome || 'PENDING'}
                                          disabled={actionLoading}
                                          onChange={(event) => updateFollowUpOutcome(action.id, customer.customerId, event.target.value)}
                                        >
                                          <option value="PENDING">Not recorded</option>
                                          <option value="RETAINED">Retained after 90 days</option>
                                          <option value="CHURNED">Churned</option>
                                        </select>
                                        <span className="small text-body-secondary">
                                          {action.outcomeRecordedAt
                                            ? `Recorded ${new Date(action.outcomeRecordedAt).toLocaleDateString()}`
                                            : 'Retained can be recorded after 90 days; record churn when it occurs.'}
                                        </span>
                                      </div>}
                                      <ul className="small mb-0">
                                        {action.history.map((event, eventIndex) => (
                                          <li key={`${event.createdAt}-${eventIndex}`}>
                                            {event.eventType}: {event.details} · {event.actor} · {new Date(event.createdAt).toLocaleString()}
                                          </li>
                                        ))}
                                      </ul>
                                    </article>
                                  ))}
                                </div>
                              ) : (
                                <p className="small text-body-secondary mb-0">No actions have been assigned to this customer.</p>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                      </Fragment>
                    )
                  })}

                  {!visibleCustomers.length && (
                    <tr>
                      <td colSpan="10" className="text-center py-5">
                        {loading
                          ? 'Loading customer results…'
                          : 'No customers match your search or filter.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3 pt-3 mt-3 border-top">
              <span className="small text-body-secondary">
                Showing{' '}
                {filteredCustomers.length
                  ? (currentPage - 1) * PAGE_SIZE + 1
                  : 0}
                –
                {Math.min(currentPage * PAGE_SIZE, filteredCustomers.length)}{' '}
                of {filteredCustomers.length.toLocaleString()} customers
              </span>

              <nav aria-label="Customer table pagination">
                <ul className="pagination pagination-sm mb-0">
                  <li
                    className={`page-item ${currentPage <= 1 ? 'disabled' : ''}`}
                  >
                    <button
                      className="page-link"
                      disabled={currentPage <= 1}
                      onClick={() =>
                        setPage((value) => Math.max(1, value - 1))
                      }
                    >
                      Previous
                    </button>
                  </li>
                  <li className="page-item disabled">
                    <span className="page-link">
                      {currentPage} / {pageCount}
                    </span>
                  </li>
                  <li
                    className={`page-item ${
                      currentPage >= pageCount ? 'disabled' : ''
                    }`}
                  >
                    <button
                      className="page-link"
                      disabled={currentPage >= pageCount}
                      onClick={() =>
                        setPage((value) => Math.min(pageCount, value + 1))
                      }
                    >
                      Next
                    </button>
                  </li>
                </ul>
              </nav>
            </div>
          </div>
        </section>

        <footer className="d-flex flex-column flex-sm-row justify-content-between gap-2 text-body-secondary small py-4">
          <span>Customer Retention Platform</span>
          <span>
            {lastUpdated
              ? `Last updated ${lastUpdated.toLocaleTimeString()}`
              : 'Waiting for data'}
          </span>
        </footer>
      </main>
    </div>
  )
}

export default function App() {
  const [theme, setTheme] = useState(
    () => localStorage.getItem('retention_theme') || 'dark',
  )
  const [token, setToken] = useState(
    () => sessionStorage.getItem('retention_jwt') || '',
  )
  const [role, setRole] = useState(
    () => sessionStorage.getItem('retention_role') || '',
  )
  const [loginLoading, setLoginLoading] = useState(false)
  const [loginError, setLoginError] = useState('')

  useEffect(() => {
    if (!token || role) return
    let cancelled = false
    fetch('/api/v1/auth/role', {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
    })
      .then(async (response) => {
        if (!response.ok) throw new Error('Unable to load account permissions.')
        return response.json()
      })
      .then((identity) => {
        if (cancelled) return
        const loadedRole = String(identity.role || '')
        sessionStorage.setItem('retention_role', loadedRole)
        setRole(loadedRole)
      })
      .catch(() => {
        if (!cancelled) setRole('UNKNOWN')
      })
    return () => { cancelled = true }
  }, [token, role])

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }

  async function fetchWithRetry(input, init, maxAttempts = 5) {
    let lastError
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        const response = await fetch(input, init)
        // Retry on: 429 (rate limit), 502/503/504 (cold start/unavailable)
        // Do not retry on 401/403/404 — those are real failures.
        const retryStatus = [429, 502, 503, 504]
        if (retryStatus.includes(response.status) && attempt < maxAttempts) {
          // Exponential backoff with jitter: 2s, 4s, 8s, 16s
          const delay = Math.min(2000 * Math.pow(2, attempt - 1), 30000) + Math.random() * 1000
          lastError = new Error(
            `Service ${response.status === 429 ? 'rate limited' : 'waking up'} (HTTP ${response.status}). Retrying in ${Math.round(delay/1000)}s…`,
          )
          await sleep(delay)
          continue
        }
        return response
      } catch (err) {
        // Retry network errors too (service still spinning up / DNS issues).
        if (attempt < maxAttempts) {
          lastError = err
          const delay = Math.min(2000 * Math.pow(2, attempt - 1), 30000) + Math.random() * 1000
          await sleep(delay)
          continue
        }
        throw err
      }
    }
    throw lastError
  }

  async function handleLogin(username, password) {
    setLoginLoading(true)
    setLoginError('')

    try {
      const response = await fetchWithRetry(LOGIN_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({ username, password }),
      })

      const raw = await response.text()
      let result

      try {
        result = raw ? JSON.parse(raw) : {}
      } catch {
        result = raw
      }

      if (!response.ok) {
        // Special handling for common Render free tier issues
        if (response.status === 429) {
          throw new Error(
            'Too many login attempts. Render free tier rate limit hit. Please wait 30–60 seconds and try again.'
          )
        }
        if (response.status === 502 || response.status === 503 || response.status === 504) {
          throw new Error(
            'Backend service is starting up (cold start). Please wait 30–60 seconds and try again.'
          )
        }
        throw new Error(
          typeof result === 'object'
            ? result.message ||
                result.error ||
                `Login failed (HTTP ${response.status}).`
            : `Login failed (HTTP ${response.status}).`,
        )
      }

      const jwt = getToken(result)

      if (!jwt) {
        throw new Error(
          'Login succeeded, but the response did not contain a recognized token field.',
        )
      }

      sessionStorage.setItem('retention_jwt', jwt)
      setToken(jwt)
    } catch (err) {
      setLoginError(
        err.message || 'Unable to sign in. Check the backend connection.',
      )
    } finally {
      setLoginLoading(false)
    }
  }

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.setAttribute(
      'data-bs-theme',
      theme === 'dark' ? 'dark' : 'light',
    )
    localStorage.setItem('retention_theme', theme)
  }, [theme])

  function toggleTheme() {
    setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
  }

  function handleLogout() {
    sessionStorage.removeItem('retention_jwt')
    sessionStorage.removeItem('retention_role')
    setToken('')
    setRole('')
    setLoginError('')
  }

  return token ? (
    <Dashboard
      token={token}
      role={role}
      onLogout={handleLogout}
      theme={theme}
      onToggleTheme={toggleTheme}
    />
  ) : (
    <Login
      onLogin={handleLogin}
      loading={loginLoading}
      error={loginError}
    />
  )
}
