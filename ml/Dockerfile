# ML Service Dockerfile - uses pre-trained model artifacts (included in repo)
# Build context: ./ml (set in render.yaml)
# Dockerfile location: ./ml/Dockerfile
#
# NOTE: Model artifacts (*.joblib, *.json) must be committed to the repo.
# Train locally and commit: git add ml/*.joblib ml/*.json

FROM python:3.12-slim

WORKDIR /app

# Install runtime dependencies only
COPY requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --disable-pip-version-check -r requirements.txt

# Copy pre-trained model artifacts (must exist in repo)
COPY balanced_logistic_final.joblib ./
COPY screening_threshold.json ./
COPY baseline_holdout_metrics.json ./
COPY gradient_boosting_final.joblib ./
COPY targeting_policy.json ./

# Copy application code
COPY prediction_api.py ./

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)" || exit 1

CMD ["uvicorn", "prediction_api:app", "--host", "0.0.0.0", "--port", "8000"]# Force rebuild 10/22/2026 23:13:15
# force rebuild 10/22/2026 23:24:50
