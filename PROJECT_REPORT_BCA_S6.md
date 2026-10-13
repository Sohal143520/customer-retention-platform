# AI-Powered Customer Intelligence & Retention Platform
**Project ID:** BCA-S6-MAJOR-AI-001 | UG Student – BCA | Semester 6 | Major Project

---

## Student Details
- **Student Name:** Harminder Singh Sohal
- **Enrolment Number:** [Enter University Enrolment Number]
- **Program / Batch:** BCA / 2023–2026 (Semester 6)
- **Submission Date:** October 13, 2026
- **Technology Stack:** Spring Boot 3.5 (Java 21) | React 19 + Bootstrap 5 | FastAPI (Python 3.13) | Scikit-Learn | PostgreSQL 18 | Docker Compose

---

## Activity 1: Problem Definition & Scope

### Question 1: Business Outcome and KPIs
Customer churn directly diminishes recurring revenue, inflates customer acquisition costs (CAC), and lowers customer lifetime value (CLV). The business outcome of this platform is to proactively protect enterprise recurring revenue by detecting high-risk churn patterns early, diagnosing underlying friction, and triggering automated retention workflows before service abandonment occurs.

To measure performance, the system tracks four core Key Performance Indicators (KPIs):
1. **Overall Churn Rate:** The proportion of total customers who terminated services during the measurement window:
   $$\text{Churn Rate} = \frac{\text{Number of Churned Customers}}{\text{Total Customers}} \times 100$$
2. **Customer Retention Rate:** The percentage of customers who continue their relationship:
   $$\text{Retention Rate} = 100\% - \text{Churn Rate}$$
3. **High-Risk Customer Count:** The aggregate count of active accounts ($\text{Churned} = \text{'No'}$) whose predicted churn probability meets or exceeds the operational business threshold ($p \ge 0.50$).
4. **Retention-Action Coverage:** The proportion of flagged high-risk accounts assigned an actionable intervention:
   $$\text{Retention Action Coverage} = \frac{\text{Active High-Risk Customers Assigned an Action}}{\text{Total Active High-Risk Customers}} \times 100$$

### Question 2: Measurable Outcome
1. **High Churner Capture:** Identify at least 70% of actual churners (Recall $\ge 0.70$) within the customer base at the preselected operational screening threshold ($p \ge 0.40$), where the balanced model achieves **74.42% recall** on fixed holdout evaluation.
2. **100% Playbook Coverage:** Achieve $100\%$ retention-action coverage for all active high-risk accounts ($p \ge 0.50$, score freshness $\le 24$ hours) via deterministic retention playbooks.
3. **15% Relative Churn Reduction:** Target a net 15% relative reduction in overall churn across high-risk customer segments evaluated against a 90-day historical baseline cohort over a complete 90-day post-intervention cycle.

### Question 3: Out of Scope
The academic prototype strictly excludes:
- Live dispatch of real-world communications (e.g., active SMS gateways, WhatsApp Business APIs, or live SMTP relays).
- Production integration with live core-banking or payment processing gateways.
- Real-time automated ledger billing debits, account freezes, or service contract cancellations.
- Direct replacement of an enterprise multi-departmental CRM suite (e.g., Salesforce Service Cloud, SAP).
- Autonomous execution of contractual legal negotiations without operational human sign-off.

---

## Activity 2: Requirements Engineering

### Question 1: Five Functional Requirements
1. **FR-01: User Authentication & Role-Based Access Control (RBAC):** The system shall authenticate authorized users via JWT and enforce distinct permissions across three roles: `ADMIN`, `OPERATIONS`, and `RETENTION_AGENT`.
2. **FR-02: Customer Batch Data Ingestion:** The system shall parse and validate tabular customer transaction datasets (CSV and XLSX formats), enforcing schema conformity, duplicate-ID rejection, and atomic full-dataset replacement.
3. **FR-03: Customer Profile & Behavioral Inspection:** The system shall render individual customer engagement metrics, support history, spend profiles, risk indicators, and recent action histories in an interactive detail drawer.
4. **FR-04: Dual-Threshold Churn Inference Engine:** The system shall compute churn probabilities, categorize accounts into color-coded risk bands (Low $<0.30$, Medium $0.30–0.499$, High $\ge 0.50$), apply the 0.40 screening cutoff, and isolate the top 30% capacity targets.
5. **FR-05: Prescriptive Retention Recommendations & Lifecycle Tracking:** The system shall evaluate behavioral friction drivers to assign tailored retention strategies, record status transitions (`ASSIGNED` $\rightarrow$ `CONTACTED` $\rightarrow$ `COMPLETED` / `CANCELLED`), log immutable actor audit trails, and enforce 90-day outcome recording (`RETAINED` / `CHURNED`).

### Question 2: Observable Behaviour
- **FR-01:** When an authorized user enters valid administrative credentials, the system authenticates the credentials via Spring Security and BCrypt, generates a signed JWT token, and transitions to the dashboard within 1 second while tailoring UI controls to the user's role.
- **FR-02:** When an administrator or operations user uploads a valid dataset (e.g., the 2,200-row workbook), the system validates all rows upfront, replaces the `customers` table, displays the exact record count loaded (2,200 records), and preserves existing retention actions for retained IDs while removing orphaned actions for omitted IDs.
- **FR-03:** When a user selects a customer row or clicks "View Profile", the system opens a dedicated inspection drawer populating all 15 demographic, transaction, and support attributes alongside historical action audit entries.
- **FR-04:** When bulk scoring is triggered, the Spring Boot backend vectorizes the entire dataset to the FastAPI model service, receives probabilities, updates customer risk timestamps, ranks the top 30% capacity slice, and displays observed recall against the 70% target. Single-customer evaluation returns signed log-odds feature contributions.
- **FR-05:** When an active customer with a fresh model score ($\le 24$ hours) has $p \ge 0.50$, the system dynamically maps their friction profile to a prescriptive action (e.g., `Billing Assistance` for payment failures $\ge 2$, `Priority Support Concierge` for high tickets/low CSAT). Bulk playbooks can assign these idempotently.

### Question 3: Non-Functional Requirements
- **Performance:** Single-record inferencing executes in under 500 ms (median $<50$ ms), and bulk scoring of 5,000 synthetic records through the Spring-to-FastAPI bridge executes in under 2.0 seconds.
- **Security:** Stateless JWT authentication with salted BCrypt password hashing. Role changes are prohibited via public self-registration (defaulting strictly to `RETENTION_AGENT`); administrator promotion requires trusted database access. Actuator health endpoints expose minimal status without leaking environment details.
- **Usability:** Responsive dashboard built with Bootstrap 5, featuring color-coded risk badges (Green/Amber/Red), metric progress bars, search/filter controls, and plain-English non-causal model explanations.
- **Maintainability & Modularity:** Decoupled multi-tier architecture separating the React SPA, the Spring Boot business/security REST API, the FastAPI Python scikit-learn scoring microservice, and the PostgreSQL relational store.

---

## Activity 3: Dataset Understanding & Data Quality

### Question 1: Useful Churn Predictors
Empirical analysis of the 2,200 customer records in `Client Data Set for Student Project Workbook.xlsx` identified five key behavioral variables:
1. **Payment Failures in Last 90 Days (`Payment_Failures_90D`):** Churned customers average **2.27 failures** versus **1.85 failures** for retained accounts. In the balanced model, payment failures carry the strongest positive risk contribution (+0.3347 log-odds), reflecting involuntary and friction-induced churn.
2. **Support Tickets in Last 90 Days (`Support_Tickets_90D`):** Churners logged an average of **3.83 tickets** compared to **3.34** for non-churners (+0.2520 coefficient), signaling unresolved service friction.
3. **Days Since Last Purchase (`Days_Since_Last_Purchase`):** Churners averaged **63.97 days** of inactivity versus **58.03 days** for retained accounts (+0.2285 coefficient), highlighting digital disengagement.
4. **Satisfaction Score (`Satisfaction_Score`):** Retained customers report an average CSAT of **3.12 / 5.0**, whereas churners average **2.82 / 5.0** (-0.2312 coefficient), making CSAT a critical leading sentiment indicator.
5. **App Logins in Last 30 Days (`App_Logins_30D`):** Lower platform logins (17.14 for churners vs. 17.86 for non-churners) indicate fading usage habits.

### Question 2: Behaviour Without Causality
While variables such as `Support_Tickets_90D` and `Payment_Failures_90D` are strongly associated with churn, they represent **statistical correlations, not causal proof**. For example, a customer submitting multiple tickets may simply be an engaged user encountering an edge case, while true churn could stem from competitor pricing or external business failure. The platform's single-customer explanation cards explicitly document:
> *"These describe how model inputs contributed to this score. They are associations, not proof that changing a feature or taking an action will cause churn to change."*

### Question 3: Data Quality and Class Balance Issues
- **Schema Audit:** The workbook contains exactly 2,200 rows and 15 columns with zero missing values across all features. `Customer_ID` values are strictly unique (`C00001` through `C02200`).
- **Class Imbalance Distribution:**
  - Retained Customers (`Churned = 'No'`): **1,557 records (70.77%)**
  - Churned Customers (`Churned = 'Yes'`): **643 records (29.23%)**
- **Business & Modeling Impact:** With a ~71:29 class ratio, an unweighted baseline model predicting "No" achieves an apparent 70.77% accuracy while yielding 0.0% churner recall. To ensure high recall without bias, the model uses `class_weight='balanced'` and stratified partitioning.

---

## Activity 4: Exploratory Business Analytics

### Question 1: Overall and Segment Churn Rate
$$\text{Overall Churn Rate} = \frac{643}{2,200} \times 100 = \mathbf{29.23\%}$$

#### Segment Breakdowns Across Core Dimensions:
1. **Customer Segment:**
   - Growth: 227 churned out of 755 = **30.07%**
   - Premium: 216 churned out of 729 = **29.63%**
   - Value: 200 churned out of 716 = **27.93%**
2. **Geographic Region:**
   - East: 142 churned out of 439 = **32.35%** *(Highest regional risk)*
   - West: 137 churned out of 454 = **30.18%**
   - South: 140 churned out of 477 = **29.35%**
   - North: 112 churned out of 412 = **27.18%**
   - Central: 112 churned out of 418 = **26.79%**
3. **Tenure Cohorts:**
   - 0–12 Months: 141 churned out of 421 = **33.49%** *(Early lifecycle vulnerability)*
   - 13–24 Months: 125 churned out of 428 = **29.21%**
   - 25–48 Months: 197 churned out of 730 = **26.99%** *(Highest stability)*
   - 49+ Months: 180 churned out of 621 = **29.82%**

### Question 2: Formula Explanation
$$\text{Churn Rate} = \frac{\text{Number of Churned Customers}}{\text{Total Customers}} \times 100$$
- **Numerator:** 643 records where `Churned == 'Yes'`.
- **Denominator:** 2,200 total active and churned customer cohort records.
- **Result:** $\frac{643}{2,200} \times 100 = 29.22727\% \approx 29.23\%$.

### Question 3: Visualisations & Business Takeaways

```
Tenure Cohort  | Observed Churn Rate (%)
--------------------------------------------------------------
0 - 12 Months  | [==============================] 33.49%  <-- Early drop-off
13 - 24 Months | [==========================]    29.21%
25 - 48 Months | [========================]      26.99%  <-- Highest stability
49+ Months     | [==========================]    29.82%

Geographic Region | Observed Churn Rate (%)
--------------------------------------------------------------
East              | [==============================] 32.35%  <-- Regional operational alert
West              | [==============================] 30.18%
South             | [=============================] 29.35%
North             | [===========================]   27.18%
Central           | [==========================]    26.79%
```

**Key Business Takeaways:**
1. **Onboarding Fragility:** Customers in their first year experience the highest attrition (33.49%). The business must overhaul initial onboarding and trigger check-in playbooks during the first 90 days.
2. **East Regional Outlier:** Churn in the East region (32.35%) exceeds Central (26.79%) by 5.56 percentage points, warranting investigation into regional payment routing, customer support response latency, or fulfillment partners.

---

## Activity 5: Feature Preparation

### Question 1: Required Transformations
1. **Identifier Separation:** `Customer_ID` is removed from model inputs.
2. **Target Binarization:** String labels mapped to binary integers: $\text{'Yes'} \rightarrow 1$, $\text{'No'} \rightarrow 0$.
3. **Categorical Encoding:** `Age_Group`, `Region`, and `Customer_Segment` are encoded using `OneHotEncoder(drop='first', handle_unknown='ignore')`.
4. **Numerical Standardization:** Continuous features (`Tenure_Months`, `Monthly_Spend_INR`, `Transactions_90D`, `Support_Tickets_90D`, `Avg_Response_Hours`, `Satisfaction_Score`, `App_Logins_30D`, `Days_Since_Last_Purchase`, `Discount_Usage_Pct`, `Payment_Failures_90D`) are scaled using `StandardScaler` combined with median imputation (`SimpleImputer(strategy='median')`).
5. **Pipeline Encapsulation:** All transformations are structured inside a Scikit-Learn `ColumnTransformer` within the serialized pipeline artifact.

### Question 2: Encoding, Missing Values, Scaling, & Leakage Prevention
- **Categorical Encoding:** One-hot encoding avoids imposing an artificial ordinal ranking on nominal categories like `Region` and `Customer_Segment`. Dropping the first dummy column mitigates multicollinearity.
- **Robustness Imputation:** Although the master dataset is clean, the pipeline preprocessor includes `SimpleImputer` to prevent runtime failures on sparse future customer uploads.
- **Scaling Sensitivity:** Logistic Regression relies on gradient optimization; scaling ensures that large monetary amounts (e.g., `Monthly_Spend_INR` up to ₹10,000) do not dominate unit-scale CSAT scores (1–5).
- **Leakage Prevention:** Transformers are fitted strictly on `X_train` during training. Holdout test data and real-time inference payloads are transformed using training-derived statistics only.

### Question 3: Variables Excluded from Model Training
- `Customer_ID`: Unique alphanumeric key (`C00001`–`C02200`); contains no generalized predictive pattern and risks memorization.
- Synthetic UI Groups: Derived UI categories (e.g., tenure bands) are excluded, enabling the model to learn directly from raw continuous `Tenure_Months`.
- Target Column `Churned`: Removed from feature matrix $X$ to prevent target leakage.

---

## Activity 6: Machine Learning Model Development

### Question 1: Selected Algorithm
The primary deployed model is **Balanced Cost-Sensitive Logistic Regression** (`LogisticRegression(class_weight='balanced', max_iter=2000, random_state=42)`), benchmarked against Gradient Boosting and Random Forest alternatives.

**Rationale for Selection:**
1. **Direct Probabilistic Interpretability:** Generates well-calibrated continuous probabilities and signed log-odds coefficients, enabling transparent feature contribution explanations without black-box surrogate approximations.
2. **Ultra-Low Latency:** Inference completes in $<5$ ms per record and processes 5,000 records in $<100$ ms in Python, ensuring compliance with the Spring backend's 2.0-second SLA.
3. **Screening Sensitivity:** When paired with a dual-threshold strategy (0.40 screening cutoff and 0.50 high-risk KPI), it comfortably meets the $>70\%$ churner recall requirement.

### Question 2: Model Comparison (Holdout Evaluation: 440 Records)

| Evaluation Dimension | Balanced Logistic Regression | Balanced Random Forest (`max_depth=6`) | Baseline Logistic Regression (Unweighted) |
|---|---|---|---|
| **Model Interpretability** | High (Direct signed log-odds) | Moderate (Feature importance / SHAP) | High (Direct signed log-odds) |
| **Operational Complexity** | Minimal (Microsecond inference) | Moderate (Tree ensemble traversal) | Minimal (Microsecond inference) |
| **Recall (Churn = 1, $p \ge 0.50$)** | **51.94% (67 / 129)** | 37.21% (48 / 129) | 5.43% (7 / 129) |
| **Recall (Churn = 1, $p \ge 0.40$)** | **74.42% (96 / 129)** | 58.14% (75 / 129) | 12.40% (16 / 129) |
| **Precision (Churn = 1, $p \ge 0.50$)** | 33.33% (67 / 201) | 39.34% (48 / 122) | 41.18% (7 / 17) |
| **F1-Score ($p \ge 0.50$)** | **0.4061** | 0.3825 | 0.0959 |
| **Overall Accuracy ($p \ge 0.50$)** | 55.45% | 64.77% | 70.00% |

**Decision:** Balanced Logistic Regression achieves superior churner capture across both threshold settings and satisfies the operational Recall target ($\ge 70\%$) at the 0.40 screening threshold.

### Question 3: Train-Test Split Architecture
The 2,200 records are partitioned using an **80/20 Stratified Split** (`random_state=42`, `stratify=y`):
- **Training Cohort:** 1,760 records (1,246 Retained [70.80%], 514 Churned [29.20%])
- **Holdout Testing Cohort:** 440 records (311 Retained [70.68%], 129 Churned [29.32%])

Stratification preserves exact class distribution across splits, preventing evaluation skew.

---

## Activity 7: Model Evaluation & Interpretation

### Question 1: Performance Metrics (Holdout Test Set: 440 Records)

#### Operational Screening Threshold ($p \ge 0.40$, Holdout Evaluation from `ml/baseline_holdout_metrics.json`):
- **Accuracy:** $43.86\%$ (193 / 440)
- **Precision:** $30.97\%$ (96 / 310)
- **Recall:** $\mathbf{74.42\%}$ (96 / 129) $\rightarrow$ *Satisfies the $\ge 70\%$ churner recall requirement!*
- **F1-Score:** $\mathbf{0.4374}$

#### Default Operational KPI Threshold ($p \ge 0.50$):
- **Accuracy:** $\frac{67 + 177}{440} = \mathbf{55.45\%}$
- **Precision:** $\frac{67}{67 + 134} = \mathbf{33.33\%}$
- **Recall:** $\frac{67}{129} = \mathbf{51.94\%}$
- **F1-Score:** $\mathbf{0.4061}$

### Question 2: Confusion Matrix Analysis

#### At Screening Cutoff ($p \ge 0.40$, Recall Focus):
```
                       Predicted: No Churn (0)    Predicted: Churn (1)
Actual: No Churn (0)          TN = 97                   FP = 214
Actual: Churn (1)             FN = 33                   TP = 96
```

#### At High-Risk Threshold ($p \ge 0.50$, Playbook Eligibility Focus):
```
                       Predicted: No Churn (0)    Predicted: Churn (1)
Actual: No Churn (0)          TN = 177                  FP = 134
Actual: Churn (1)             FN = 62                   TP = 67
```

- **True Positive (TP):** At-risk customers accurately identified by the model, allowing frontline teams to execute retention playbooks before cancellation.
- **True Negative (TN):** Retained customers correctly classified, requiring no intervention.
- **False Positive (FP):** Customers flagged as at-risk who intended to stay; receiving non-intrusive concierge outreach poses minimal harm.
- **False Negative (FN):** Churners missed by the model who terminate service uncontacted.

### Question 3: Evaluation of Costlier Error
In customer retention, a **False Negative (FN) is substantially costlier than a False Positive (FP)**:
- **Cost of False Negative:** The enterprise loses an account uncontacted, forfeiting all remaining Customer Lifetime Value (CLV). Acquiring a replacement customer typically costs **5 to 7 times** more than retaining an existing one.
- **Cost of False Positive:** The business incurs a negligible outreach cost (an agent check-in or loyalty tier eligibility review) for a customer who would have stayed anyway. This outreach often reinforces customer loyalty rather than causing damage.

Hence, tuning the screening cutoff to $p = 0.40$ prioritizes high recall ($74.42\%$) over precision.

---

## Activity 8: Software & Data Architecture

### Question 1: Components and Data Flow
The platform is deployed as a 4-tier microservice architecture:
1. **Frontend Presentation Tier:** React 19 Single Page Application (Vite, Bootstrap 5) providing executive KPI widgets, dataset uploaders, customer inspection drawers, What-If simulation forms, and playbook assignment controls.
2. **Core Application & Security Tier:** Spring Boot 3.5.6 (Java 21) REST API managing JWT security, role-based authorization, dataset ingestion/validation, retention action lifecycles, and 90-day cohort comparison logic.
3. **Machine Learning Inference Tier:** FastAPI (Python 3.13) microservice hosting the Scikit-Learn `balanced_logistic_final.joblib` pipeline, providing single-record inference with signed log-odds explainability and high-throughput bulk scoring.
4. **Data Persistence Tier:** PostgreSQL 18 relational database managing customers, retention actions, action audit history, baseline segments, and user credentials.

```
┌────────────────────────────────────────────────────────┐
│                   Frontend (React 19)                  │
│               Vite + Bootstrap 5 Dashboard             │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTPS / JSON (JWT)
                           ▼
┌────────────────────────────────────────────────────────┐
│               Backend (Spring Boot 3.5.6)              │
│       Spring Web, Spring Security, Spring Data JPA     │
└─────────────┬────────────────────────────┬─────────────┘
              │ JDBC                       │ HTTP / REST
              ▼                            ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│   PostgreSQL 18 Database │  │    FastAPI Model Service │
│ • customers              │  │ (Python 3.13 / Port 8000)│
│ • retention_actions      │  │ • balanced_logistic.joblib│
│ • retention_action_audit │  │ • /predict (explainable) │
│ • baseline_segments      │  │ • /predict/bulk (vector) │
│ • users                  │  │                          │
└──────────────────────────┘  └──────────────────────────┘
```

### Question 2: Separation of Responsibilities
- **Presentation Layer (`frontend/src/`):** Manages user state, renders UI components, and consumes REST endpoints. Contains zero raw machine learning transformations or direct SQL queries.
- **Application & Service Layer (`src/main/java/.../service/`):** Coordinates business transactions, enforces atomic dataset replacement, validates retention playbook eligibility, and enforces the 90-day follow-up outcome rule.
- **Machine Learning Layer (`ml/`):** Manages model loading, feature scaling, one-hot encoding, churn probability calculation, and log-odds coefficient extraction.
- **Persistence Layer (`src/main/java/.../repository/`):** Spring Data JPA repositories handling database queries, audit persistence, and atomic transactions.

### Question 3: API & Module Boundaries
- `POST /api/v1/auth/login`: Authenticates credentials; returns signed JWT token.
- `POST /api/v1/auth/register`: Registers new accounts (defaults strictly to `RETENTION_AGENT`).
- `GET /api/v1/auth/role`: Returns the authenticated account's username and role.
- `POST /api/v1/customers/upload`: Ingests customer datasets (CSV/XLSX); executes atomic dataset replacement.
- `POST /api/v1/predictions/churn`: Single customer inference returning probability and signed log-odds contributions.
- `POST /api/v1/predictions/bulk`: Vectorized bulk scoring; updates customer score timestamps and identifies top 30% capacity targets.
- `POST /api/v1/customers/{id}/retention-actions`: Assigns a retention action (requires active customer and fresh score $\ge 0.50$).
- `POST /api/v1/retention-actions/assign-high-risk`: Idempotently assigns playbooks to all uncovered eligible high-risk customers.
- `PATCH /api/v1/retention-actions/{id}/status`: Updates action status (`ASSIGNED`, `CONTACTED`, `COMPLETED`, `CANCELLED`).
- `PATCH /api/v1/retention-actions/{id}/outcome`: Records 90-day follow-up outcome (`RETAINED` gated after 90 days, `CHURNED` immediate).
- `POST /api/v1/retention-cohorts/baseline`: Ingests 90-day historical segment baseline CSV.
- `GET /api/v1/retention-cohorts/baseline/comparison`: Compares active 90-day cohort against baseline to benchmark the 15% reduction target.

---

## Activity 9: Application Prototype Development

### Question 1: Prototype Features
1. **Role-Based Authentication:** Protected login gateway enforcing `ADMIN`, `OPERATIONS`, and `RETENTION_AGENT` capabilities.
2. **Executive KPI Dashboard:** Real-time KPI tiles displaying Total Customers, Observed Churn Rate (29.23%), Active Retention Rate (70.77%), Active High-Risk Count, Action Coverage %, and Observed Recall vs. the 70% target.
3. **Atomic Dataset Upload Engine:** Drag-and-drop upload supporting `.csv` and `.xlsx` with schema validation and clean action-history preservation.
4. **Interactive Customer Inspection Drawer:** Searchable and filterable directory displaying complete customer profiles and historical action audit trails.
5. **What-If Scenario Simulation:** Interactive feature modification tool allowing agents to test behavioral adjustments (e.g., reducing response time or resolving payment friction) and view real-time score changes with signed log-odds explanations.
6. **Prescriptive Playbooks & Lifecycle Management:** Rule-based recommendation engine with one-click bulk assignment, action status progression, and audit logging.
7. **90-Day Cohort Baseline Comparison:** Segment-level comparison engine verifying the 15% relative churn reduction target over a complete 90-day cycle.

### Question 2: Feature-to-Requirement Mapping

| Feature Name | Requirement ID | Implementation Description |
|---|---|---|
| Role-Based JWT Auth | FR-01 | Spring Security + BCrypt + JWT filter; role-tailored frontend UI controls. |
| CSV/XLSX Ingestion | FR-02 | `CustomerImportService` reading CSV/XLSX with schema validation and dataset replacement. |
| Customer Profile Drawer | FR-03 | React offcanvas drawer rendering 15 customer attributes and historical audit entries. |
| Bulk & Single Inference | FR-04 | FastAPI scoring service returning probabilities, risk bands, and log-odds contributions. |
| Prescriptive Playbooks | FR-05 | `RetentionActionService` mapping friction rules to interventions with audit logging. |
| Cohort Comparison Engine | FR-06 | `RetentionComparisonService` measuring relative churn reduction against 90-day baselines. |

### Question 3: End-to-End User Flow
1. **Authentication:** The retention officer signs in via `/api/v1/auth/login`. The frontend decodes the JWT and configures role-appropriate navigation.
2. **Dashboard Review:** The officer reviews high-level KPIs: 2,200 total accounts, 29.23% observed churn, active high-risk count, and action coverage percentage.
3. **Directory Filtering:** The officer filters the customer directory for active accounts flagged as High Risk ($p \ge 0.50$).
4. **Profile Drill-Down:** The officer opens customer `C00001` in the drawer, observing 1 payment failure, 6 support tickets, and CSAT = 2.
5. **Inference & Explainability:** The system displays Churn Probability ($68.4\%$, High Risk) and shows top risk drivers: Support Tickets (+0.54) and Low CSAT (+0.42).
6. **Action Assignment:** The rule engine recommends `Priority Support Concierge`. The agent assigns the action, generating an audit entry (`ASSIGNED`).
7. **Action Lifecycle & Follow-up:** Over subsequent weeks, the status is updated to `CONTACTED` and `COMPLETED`. At 90 days post-assignment, the officer records the final follow-up outcome (`RETAINED` or `CHURNED`).

---

## Activity 10: Cloud-Native Design & Packaging

### Question 1: Containerisation and Deployment Architecture
The platform is packaged into 4 decoupled containers orchestrated via Docker Compose:
- **`backend`:** Multi-stage build (Temurin 21 JRE + Maven) running the Spring Boot API on port `8080`.
- **`prediction-api`:** Python 3.13-slim container hosting Uvicorn and FastAPI on port `8000`.
- **`frontend`:** Multi-stage build (Node 24 build stage + Nginx Alpine runtime) serving the React SPA on port `8081`.
- **`postgres`:** PostgreSQL 18 container with persistent volume storage (`postgres_data`) mapped to port `5433` (to avoid conflicting with local installations).

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: customer_retention
      POSTGRES_USER: ${DB_USERNAME:-retention_app}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "127.0.0.1:5433:5432"
    volumes:
      - postgres_data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$$POSTGRES_USER\" -d \"$$POSTGRES_DB\""]

  prediction-api:
    build:
      context: ./ml
      dockerfile: Dockerfile
    expose:
      - "8000"
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)"]

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/customer_retention
      DB_USERNAME: ${DB_USERNAME:-retention_app}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      PREDICTION_API_URL: http://prediction-api:8000
    ports:
      - "127.0.0.1:8080:8080"
    depends_on:
      postgres: { condition: service_healthy }
      prediction-api: { condition: service_healthy }

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "127.0.0.1:8081:80"
    depends_on:
      backend: { condition: service_healthy }

volumes:
  postgres_data:
```

### Question 2: Service, Model, Configuration, and Data Needs
- **Microservices:** Spring Boot 3.5 backend, FastAPI ML service, Nginx frontend, PostgreSQL database.
- **Model Artifacts:** Serialized pipeline (`ml/balanced_logistic_final.joblib`) accompanied by threshold configurations (`ml/screening_threshold.json`).
- **Configuration:** Runtime configuration injected via root `.env` template (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `PREDICTION_API_URL`).
- **Persistence:** Named Docker volume (`postgres_data`) for PostgreSQL database files, ensuring data survives container restarts.

### Question 3: Scalability, Secrets, and Monitoring
- **Horizontal Scalability:** The Spring Boot backend and FastAPI prediction service are fully stateless; multiple container replicas can run behind an AWS ALB or Kubernetes ingress.
- **Secrets Management:** Credentials (`DB_PASSWORD`, `JWT_SECRET`) are excluded from Git and image build contexts via `.dockerignore` and injected strictly via environment variables.
- **Telemetry & Health Monitoring:** Spring Boot Actuator (`/actuator/health`) provides container liveness probes without exposing internal service details (`management.endpoint.health.show-details=never`).

---

## Activity 11: Testing & Validation

### Question 1 & 2: Test Cases and Observable Results

| Test ID | Test Case Description | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| **TC-01** | Valid user authentication | Return 200 OK with signed JWT token and role details | JWT token returned; user role resolved | **PASS** |
| **TC-02** | Invalid password submission | Return 401 Unauthorized with clean error | Rejected with 401 status | **PASS** |
| **TC-03** | Public registration privilege check | Register account; verify role cannot be escalated to ADMIN | Account created strictly as `RETENTION_AGENT` | **PASS** |
| **TC-04** | Master XLSX dataset upload (2,200 rows) | Parse all rows; verify counts, unique IDs, and regional metrics | 2,200 rows loaded (1,557 No / 643 Yes; East: 142/439) | **PASS** |
| **TC-05** | Dataset replacement & action retention | Replace dataset; keep actions for existing IDs, delete removed | Kept actions for retained IDs; removed orphaned actions | **PASS** |
| **TC-06** | Invalid upload atomic rollback | Upload row with corrupted numerical spend; verify zero changes | Transaction rolled back; existing dataset untouched | **PASS** |
| **TC-07** | High-risk action eligibility gate | Attempt action assignment on customer with risk $< 50\%$ | Rejected with 400 Bad Request | **PASS** |
| **TC-08** | Stale score assignment rejection | Attempt action assignment with model score $> 24$ hours old | Rejected: score refresh required | **PASS** |
| **TC-09** | 90-day outcome maturity rule | Record `RETAINED` outcome prior to 90-day mark | Rejected: requires full 90-day follow-up completion | **PASS** |
| **TC-10** | 5,000-customer batch scoring benchmark | Score 5,000 synthetic records through Spring $\rightarrow$ FastAPI | Completed within 2.0 seconds (benchmark passed) | **PASS** |

### Question 3: Defect and Limitation Analysis
- **Identified Defect:** During prototype upload testing, files with `.csv` extensions containing XLSX binary content triggered UTF-8 decoding errors.
  - *Resolution:* Implemented file-signature verification checking the initial ZIP magic bytes (`PK`). The ingestion service seamlessly branches to Apache POI for XLSX and `BufferedReader` for CSV.
- **Identified Defect:** Bulk CSV parsing failed when customer address or region fields contained embedded newlines within quotations.
  - *Resolution:* Implemented stateful multi-line quote tracking (`hasOpenCsvQuote`), correctly assembling multi-line CSV records without corrupting row alignments.
- **Known Limitation:** The dataset represents a static snapshot rather than continuous time-series telemetry. Predictions rely on 30-day and 90-day aggregate features rather than real-time clickstream events.

---

## Activity 12: Final Demonstration & Recommendations

### Question 1 & 2: Evidence-Based Findings
1. **Finding 1: Early-Tenure Lifecycle Vulnerability**
   - *Evidence:* Churn rate in the 0–12 month tenure cohort is **33.49%**, significantly higher than the 25–48 month cohort (**26.99%**).
   - *Business Takeaway:* Early-stage churn is driven by onboarding friction and initial time-to-value deficits.
2. **Finding 2: Recurring Billing Failures Drive Churn**
   - *Evidence:* Churned customers average **2.27 payment failures** versus **1.85** for retained accounts. In the balanced model, `Payment_Failures_90D` exhibited the highest positive predictive coefficient (+0.3347).
   - *Business Takeaway:* Unresolved payment friction leads to involuntary churn and platform abandonment.
3. **Finding 3: Regional Operational Disparity in the East**
   - *Evidence:* The East region leads in churn at **32.35%** (142 / 439 churned), compared to Central at **26.79%** (112 / 418 churned).
   - *Business Takeaway:* Regional performance variations suggest localized payment gateway or delivery logistics friction rather than universal product dissatisfaction.

### Question 3: Strategic Recommendations

#### Business Recommendations:
1. **Automate Smart Dunning & Payment Assistance:** Implement intelligent payment retry logic and immediate automated dunning notifications for payment failures, preventing involuntary churn.
2. **Deploy 90-Day Onboarding Concierge:** Introduce proactive milestone check-ins, product adoption walkthroughs, and onboarding incentives for accounts in their first year.
3. **Form an East Regional Operations Taskforce:** Audit regional logistics partners and payment gateway routing in the East territory to eliminate localized friction.

#### Technical Enhancements:
1. **Explainable AI Integration:** Expand single-customer log-odds contributions to interactive waterfall charts directly within the inspection drawer.
2. **Automated Continuous Retraining (MLOps):** Implement scheduled retraining pipelines using GitHub Actions and MLflow to retrain the model as new transaction batches are ingested.

---

## Final Project Evidence Table

| Evidence Category | Submission Details / Resource Location |
|---|---|
| **Source Code Repository** | Root project repository (Spring Boot `/src`, React `/frontend`, FastAPI `/ml`, `/dataset`) |
| **Working Prototype** | React Dashboard running on `http://localhost:8081`, Spring API on `http://localhost:8080` |
| **Model Artifacts** | `ml/balanced_logistic_final.joblib`, `ml/screening_threshold.json`, `ml/baseline_holdout_metrics.json` |
| **Test Suites** | `CustomerRetentionPlatformApplicationTests.java`, `dashboardMetrics.test.js`, `test_prediction_api.py` |
| **Container Packaging** | Root `docker-compose.yml`, `Dockerfile`, `frontend/Dockerfile`, `ml/Dockerfile` |

---

## Formal Declarations
- [x] I used the supplied client dataset (`Client Data Set for Student Project Workbook.xlsx`) for all calculations and model evaluations.
- [x] I developed and validated the end-to-end prototype pipeline across frontend, backend, machine learning, and database tiers.
- [x] I verified all churn rates, confusion matrix counts, and performance metrics directly from empirical data.
- [x] I documented external tools, software libraries, and architectural components accurately.
- [x] I did not present illustrative workbook values as my own empirical results.
- [x] I completed this project independently in accordance with academic guidelines.
