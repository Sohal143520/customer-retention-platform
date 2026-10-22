# ML Service Dockerfile - uses pre-trained model artifacts (included in repo)
# Build context: repo root (.)
# Dockerfile location: ./ml/Dockerfile
#
# NOTE: Model artifacts (*.joblib, *.json) must be committed to the repo.
# Train locally and commit: git add ml/*.joblib ml/*.json

FROM python:3.12-slim

WORKDIR /app

# Install runtime dependencies only
COPY ml/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --disable-pip-version-check -r requirements.txt

# Copy pre-trained model artifacts (must exist in repo)
COPY ml/balanced_logistic_final.joblib ./
COPY ml/screening_threshold.json ./
COPY ml/baseline_holdout_metrics.json ./
COPY ml/gradient_boosting_final.joblib ./
COPY ml/targeting_policy.json ./

# Copy application code
COPY ml/prediction_api.py ./

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)" || exit 1

CMD ["uvicorn", "prediction_api:app", "--host", "0.0.0.0", "--port", "8000"]