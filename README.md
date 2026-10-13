# Customer Retention Platform

## Run the full platform with Docker Compose

Docker Compose starts PostgreSQL, the existing Spring Boot API, the existing FastAPI model service, and the React dashboard. The database is a separate Compose service with its own persistent volume; it does not connect to a PostgreSQL server already installed on the computer. Container readiness checks use the minimal `/actuator/health` status; the endpoint does not expose component details.

1. Install and start Docker Desktop.
2. From the project root, make a private local environment file from the template:

   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```

3. Replace both example secrets in `.env`. Use a database password of your choice and a private JWT secret with at least 32 characters. Keep `.env` on this computer; it is excluded from Git and Docker build contexts.
4. Build and start the services:

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

5. Open the dashboard at `http://localhost:8081`. The backend API is available at `http://localhost:8080`; PostgreSQL is bound to `localhost:5433` to avoid colliding with a local PostgreSQL installation.
6. To stop the services while preserving uploaded customers, use `docker compose down`. To intentionally erase the Compose database and all uploaded data, use `docker compose down -v`.

The initial administrator still needs to be bootstrapped using the existing **Access roles** instructions below. Do not reuse production database credentials or a production JWT key for this local stack. This Compose setup is for local development; it does not add TLS, production secrets management, backups, or deployment monitoring.

## Local backend setup (PowerShell)

Run these commands from the project root. The Spring Boot application reads its database password and JWT signing key from environment variables. Set these in the PowerShell window that will run the backend; replace the two placeholder values with your own local values:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/customer_retention"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-local-postgres-password>"
$env:JWT_SECRET = "<at-least-32-random-characters>"
.\mvnw.cmd spring-boot:run
```

Keep `JWT_SECRET` private and use the same value for the lifetime of local tokens. Changing it invalidates existing login tokens. Do not commit real credentials or signing keys.

## Local prediction API

In a separate PowerShell window, first go to the project root and activate the Python environment that has the project's ML dependencies installed, then run:

```powershell
Set-Location .\ml
python -m uvicorn prediction_api:app --host 127.0.0.1 --port 8000
```

The prediction API uses `balanced_logistic_final.joblib` and `screening_threshold.json` from the `ml` folder. Its bulk route accepts up to 5,000 customers per request. Single-customer scoring also returns the strongest signed Logistic Regression feature contributions behind that score; these describe model associations, not causes. Bulk responses omit the explanation to keep batch scoring compact.

To run the local model explanation checks from the `ml` folder:

```powershell
python -m unittest -v test_prediction_api
```

## Access roles

- `ADMIN`: may upload customer data and assign or update retention actions.
- `OPERATIONS`: may upload customer data.
- `RETENTION_AGENT`: may assign or update retention actions.

Public registration creates only `RETENTION_AGENT` accounts. Do not expose role changes to public registration. To bootstrap the first local administrator:

1. Start the backend with the PostgreSQL settings from **Local backend setup**.
2. Register a normal account through `POST /api/v1/auth/register`. For a local setup, PowerShell can prompt for the password without putting it in the command history:

   ```powershell
   $adminUsername = "project-admin"
   $adminPassword = Read-Host "Choose a strong password" -AsSecureString
   $adminPasswordPlain = [System.Net.NetworkCredential]::new("", $adminPassword).Password
   $registration = @{ username = $adminUsername; password = $adminPasswordPlain } | ConvertTo-Json
   Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/auth/register" `
     -ContentType "application/json" -Body $registration
   Remove-Variable adminPasswordPlain, registration, adminPassword
   ```

3. Open another PowerShell window and connect to the same PostgreSQL database using the trusted database account configured for the backend. The prompts keep the password out of command history:

   ```powershell
   $databaseUsername = Read-Host "PostgreSQL username"
   $databasePort = Read-Host "PostgreSQL port (5432 local install, 5433 Docker Compose)"
   $databasePassword = Read-Host "PostgreSQL password" -AsSecureString
   $env:PGPASSWORD = [System.Net.NetworkCredential]::new("", $databasePassword).Password
   psql -h localhost -p $databasePort -U $databaseUsername -d customer_retention
   Remove-Item Env:PGPASSWORD
   Remove-Variable databaseUsername, databasePort, databasePassword
   ```

4. In the `psql` prompt, promote only the account you just registered and verify the result:

   ```sql
   \set admin_username 'project-admin'
   UPDATE users SET role = 'ADMIN' WHERE username = :'admin_username';
   SELECT username, role FROM users WHERE username = :'admin_username';
   ```

   The update should affect exactly one row and the query should show `ADMIN`. If it affects zero rows, check the username and database before continuing. Sign in to the dashboard with this account. Keep the database credentials private and use this trusted database procedure only for initial local setup or approved account administration.

## Customer dataset upload behavior

Each successful CSV or XLSX upload replaces the full customer dataset. The service reads and validates every row, including duplicate-ID checks, before changing stored data; an invalid upload leaves the existing customer dataset unchanged. Retention actions and audit history are kept for customer IDs that remain in the replacement file. Actions and their audit history for IDs removed by the replacement are deleted in the same database transaction so the dashboard does not count orphaned actions. The upload response reports the number of replacement records loaded.

## Model evaluation

The active model's fixed-holdout metrics are recorded in `ml/baseline_holdout_metrics.json`. The 0.40 screening threshold and 0.50 high-risk dashboard KPI serve different purposes. The holdout result is offline model evidence, not measured live retention impact.

## Frontend KPI checks

Run these commands from the `frontend` folder to check the dashboard KPI calculations, lint rules, and production build:

```powershell
npm test
npm run lint
npm run build
```

The KPI tests use Node's built-in test runner and do not require an additional test library.

## Optional 5,000-record integration benchmark

Start the Python prediction API first. Then run this from the project root:

```powershell
.\mvnw.cmd "-Dml.integration=true" "-Dtest=CustomerRetentionPlatformApplicationTests#scoresFiveThousandCustomersThroughSpringWithinTwoSeconds" test
```

This opt-in check seeds 5,000 synthetic records into an in-memory H2 database, calls the Spring bulk endpoint and the running Python model API, and checks that the request/response path stays under two seconds. It does not measure PostgreSQL performance or a production network.

## Optional PostgreSQL upload and audit integration check

This opt-in check connects to a **dedicated disposable PostgreSQL database** and verifies CSV replacement, cleanup of removed-customer actions/audits, preservation of retained-customer audit history, rollback when an upload contains an invalid row, and idempotent bulk playbook assignment for eligible active high-risk customers. It uses synthetic rows and does not call the running backend or prediction API. Hibernate creates and drops the test schema as part of the run, so never point `PG_TEST_DB_URL` at the application database or any database containing data you need.

1. In PostgreSQL, create an empty database named `customer_retention_test` (or another dedicated database whose name ends in `_test`).
2. In a PowerShell window at the project root, set the test connection values. Enter the test database password only at the secure prompt:

   ```powershell
   $env:PG_TEST_DB_URL = "jdbc:postgresql://localhost:5432/customer_retention_test"
   $env:PG_TEST_DB_USERNAME = "postgres"
   $postgresTestPassword = Read-Host "Password for the disposable test database" -AsSecureString
   $env:PG_TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $postgresTestPassword).Password
   ```

3. Run only the opt-in PostgreSQL workflow check:

   ```powershell
   .\mvnw.cmd "-Dpostgres.integration=true" "-Dtest=PostgresCustomerWorkflowIntegrationTest" test
   ```

4. Clear the password and connection variables when the command finishes:

   ```powershell
   Remove-Item Env:PG_TEST_DB_PASSWORD, Env:PG_TEST_DB_URL, Env:PG_TEST_DB_USERNAME
   Remove-Variable postgresTestPassword
   ```

The test refuses to start unless the JDBC database name ends in `_test`. It is disabled during the normal test run.

To also measure 5,000-customer bulk scoring and single-customer scoring, keep the Python prediction API running on port 8000 and add the `ml.integration` flag:

```powershell
.\mvnw.cmd "-Dpostgres.integration=true" "-Dml.integration=true" "-Dtest=PostgresCustomerWorkflowIntegrationTest" test
```

The benchmark seeds synthetic customers before timing the bulk prediction request, reports elapsed milliseconds, and checks the 2-second target. It also warms the single-customer prediction route, takes 10 measurements, and checks the median against the 500 ms target. These checks measure local PostgreSQL plus the local Spring-to-Python path, not production network latency.
