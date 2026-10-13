# ML Service Dockerfile - trains model during build (self-contained)
# Build context: repo root (.)
# Dockerfile location: ./ml/Dockerfile

FROM python:3.12-slim AS trainer

WORKDIR /app

# Install training dependencies
COPY ml/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --disable-pip-version-check -r requirements.txt

# Copy training scripts
COPY ml/train_baseline_logistic.py ml/finalize_churn_model.py ./

# Copy entire dataset directory (avoids filename space issues)
COPY dataset/ ./dataset/

# Train model and generate artifacts
RUN TRAIN_DATA_PATH=/app/dataset/Client\ Data\ Set\ for\ Student\ Project\ Workbook.xlsx python train_baseline_logistic.py && \
    TRAIN_DATA_PATH=/app/dataset/Client\ Data\ Set\ for\ Student\ Project\ Workbook.xlsx python finalize_churn_model.py

# ============================================================
# Runtime stage
# ============================================================
FROM python:3.12-slim

WORKDIR /app

# Install runtime dependencies only
COPY ml/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --disable-pip-version-check -r requirements.txt

# Copy trained artifacts from trainer stage (only files that actually exist)
COPY --from=trainer /app/balanced_logistic_final.joblib ./
COPY --from=trainer /app/screening_threshold.json ./
COPY --from=trainer /app/baseline_holdout_metrics.json ./
COPY --from=trainer /app/gradient_boosting_final.joblib ./
COPY --from=trainer /app/targeting_policy.json ./

# Copy application code
COPY ml/prediction_api.py ./

EXPOSE 8000

CMD ["uvicorn", "prediction_api:app", "--host", "0.0.0.0", "--port", "8000"]