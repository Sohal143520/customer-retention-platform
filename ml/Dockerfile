FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --disable-pip-version-check -r requirements.txt
COPY prediction_api.py balanced_logistic_final.joblib screening_threshold.json ./
EXPOSE 8000
CMD ["uvicorn", "prediction_api:app", "--host", "0.0.0.0", "--port", "8000"]
