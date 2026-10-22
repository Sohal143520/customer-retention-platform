# Render Free Tier Deployment Guide

## Quick Fixes for 429/502/503 Errors

### 1. Environment Variables (Set in Render Dashboard)
For each service, go to **Environment** tab and add:

**Backend (`customer-retention-platform`):**
```
JWT_SECRET=<generate with: openssl rand -base64 32>
SPRING_PROFILES_ACTIVE=production
PREDICTION_API_URL=https://customer-retention-ml.onrender.com
```

**Frontend (`customer-retention-frontend`):**
```
BACKEND_URL=https://customer-retention-platform.onrender.com
VITE_API_BASE_URL=https://customer-retention-platform.onrender.com
```

**ML Service (`customer-retention-ml`):**
```
PYTHONUNBUFFERED=1
PORT=8000
```

### 2. Pre-commit Model Artifacts (Required!)
The ML Dockerfile no longer trains during build. You MUST commit model artifacts:

```bash
# Train locally first
cd ml
python train_baseline_logistic.py
python finalize_churn_model.py

# Commit artifacts (use Git LFS for large files)
git lfs track "ml/*.joblib"
git add ml/*.joblib ml/*.json
git commit -m "chore(ml): add trained model artifacts"
git push
```

Or use the GitHub Actions workflow (`.github/workflows/train-model.yml`) to auto-train on push.

### 3. Database Setup
1. Create PostgreSQL service in Render (type: `pserv`)
2. Note the `DATABASE_URL` / connection details
3. Link it to backend service via `fromDatabase` in render.yaml

### 4. First Admin User Setup
After deployment:
```bash
# Register user via API
curl -X POST https://customer-retention-platform.onrender.com/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"your-strong-password"}'

# Promote to ADMIN via Render's PostgreSQL shell or psql
# Connect to DB and run:
# UPDATE users SET role = 'ADMIN' WHERE username = 'admin';
```

### 5. Troubleshooting Common Issues

| Error | Cause | Fix |
|-------|-------|-----|
| **429 Too Many Requests** | Render free tier rate limit | Wait 30-60s; retry logic added in frontend |
| **502 Bad Gateway** | Backend spinning down (cold start) | Wait 30-60s; increased health check timeouts |
| **503 Service Unavailable** | Service starting up | Wait 30-60s; retry logic handles this |
| **504 Gateway Timeout** | Request took >30s | Increase proxy timeouts in nginx.conf |
| **CORS Error** | Origin not in allowed list | Add exact Render URL to SecurityConfig |
| **JWT_SECRET error** | Secret not set or < 32 chars | Set in Render Dashboard Environment |

### 6. Render Free Tier Limitations
- Services spin down after **15 minutes** of inactivity
- Cold start: **30-60 seconds** for first request
- **512 MB RAM** limit (JVM tuned to 75% = ~384MB)
- **100 GB/month** bandwidth
- No persistent disks (database is separate managed service)
- Build timeout: **30 minutes**

### 7. Health Check Endpoints
- Backend: `https://customer-retention-platform.onrender.com/actuator/health`
- ML: `https://customer-retention-ml.onrender.com/health`
- Frontend: `https://customer-retention-frontend.onrender.com/`

### 8. Monitoring Logs
In Render Dashboard → service → **Logs** tab, look for:
- Backend: `Started CustomerRetentionPlatformApplication`
- ML: `Uvicorn running on http://0.0.0.0:8000`
- Frontend: `nginx: ready`

### 9. Upgrade Path (When Ready)
| Plan | RAM | Always On | Price |
|------|-----|-----------|-------|
| Starter | 512 MB | No | Free |
| Standard | 1 GB | Yes | $7/mo |
| Pro | 2 GB | Yes | $25/mo |

For production, upgrade at least the backend and database to Standard.