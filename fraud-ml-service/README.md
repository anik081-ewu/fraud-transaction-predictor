# Fraud ML Service (FastAPI)

This service trains anomaly-detection models from historical transactions and exposes a prediction API for Spring Boot.

## 1) Setup

```bash
cd fraud-ml-service
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

## 2) Train models (from Excel)

Prepare an Excel file with columns like:

- `transaction_id`, `account_id`, `transaction_amount`, `transaction_type`, `transaction_date`
- `location`, `channel`, `login_attempts`, `account_balance`
- optional: `customer_age`, `customer_occupation`

Run:

```bash
python training/train_model.py --input-file "PATH\\TO\\transactions.xlsx"
```

Or from CSV:

```bash
python training/train_model.py --input-file "PATH\\TO\\transactions.csv"
```

Outputs:

- `models/iso_model.pkl`
- `models/lof_model.pkl`
- `models/svm_model.pkl`
- `models/scaler.pkl`
- `models/feature_columns.pkl`

## 3) Run FastAPI

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## 4) Test the prediction endpoint

Endpoint:

- `POST http://localhost:8000/api/v1/fraud/predict`

Example request body:

```json
{
  "transaction": {
    "transactionId": "TX100001",
    "accountId": "AC00093",
    "transactionAmount": 827.14,
    "transactionType": "Debit",
    "transactionDate": "2026-05-05T15:30:00",
    "location": "El Paso",
    "channel": "Branch",
    "loginAttempts": 4,
    "accountBalance": 5000.00
  },
  "customer": {
    "customerAge": 45,
    "customerOccupation": "Business"
  },
  "accountProfile": {
    "previousTransactionDate": "2026-05-04T10:10:00",
    "previousLocation": "Atlanta",
    "userAvgAmount": 250.50,
    "userMaxAmount": 700.00,
    "userAmountStd": 120.20,
    "userTxnCount": 35,
    "rolling7dAvgAmount": 162.30,
    "rolling30dAvgAmount": 162.30
  }
}
```

PowerShell test:

```powershell
$body = Get-Content -Raw .\\sample_request.json
Invoke-RestMethod -Method Post -Uri http://localhost:8000/api/v1/fraud/predict -ContentType 'application/json' -Body $body
```

Note: The mention `openai-docs` refers to the Codex skill file at `C:\\Users\\Administrator\\.codex\\skills\\.system\\openai-docs\\SKILL.md` and is not required for running this ML service.

## 5) Train models via API (from Spring Boot DB data)

Spring Boot owns MS SQL Server access. It should fetch historical transactions from the database and send them to this
FastAPI endpoint for training/updating the anomaly models.

Endpoint:

- `POST http://localhost:8000/api/v1/models/train`

Example request body:

```json
{
  "source": "SPRING_BOOT_DB",
  "requestedBy": "spring-boot",
  "hyperparams": {
    "ml.iso.n_estimators": 200,
    "ml.iso.contamination": "auto",
    "ml.lof.n_neighbors": 35,
    "ml.lof.contamination": "auto",
    "ml.svm.kernel": "rbf",
    "ml.svm.gamma": "scale",
    "ml.svm.nu": 0.05,
    "ml.random_state": 42
  },
  "transactions": [
    {
      "transactionId": "TX001",
      "accountId": "AC00001",
      "transactionAmount": 2500.0,
      "transactionType": "Debit",
      "transactionDate": "2026-05-01T10:30:00",
      "location": "Dhaka",
      "channel": "Online",
      "customerAge": 35,
      "customerOccupation": "Business",
      "loginAttempts": 1,
      "accountBalance": 50000.0
    }
  ]
}
```

After a successful training call, the service reloads the model artifacts in-memory, so subsequent calls to:

- `POST /api/v1/fraud/predict`

will use the latest trained models without restarting FastAPI.

## 6) Compute score percentiles (for gating thresholds)

This endpoint helps choose sensible gating thresholds by showing the distribution of model decision scores.

Endpoint:

- `POST http://localhost:8000/api/v1/models/score-percentiles`

Request body:

```json
{
  "source": "SPRING_BOOT_DB",
  "requestedBy": "spring-boot",
  "transactions": [
    {
      "transactionId": "TX001",
      "accountId": "AC00001",
      "transactionAmount": 2500.0,
      "transactionType": "Debit",
      "transactionDate": "2026-05-01T10:30:00",
      "location": "Dhaka",
      "channel": "Online",
      "customerAge": 35,
      "customerOccupation": "Business",
      "loginAttempts": 1,
      "accountBalance": 50000.0
    }
  ]
}
```

Response includes percentiles for:

- `lofDecision` (LOF `decision_function`)
- `svmDecision` (One-Class SVM `decision_function`)

Use lower-tail percentiles (e.g., 1st/5th) to pick gating thresholds.

## 7) Debug: confirm loaded hyperparams/artifacts

After retraining, these endpoints help confirm the server is using the latest artifacts and gating thresholds:

- `GET http://localhost:8000/api/v1/models/hyperparams`
- `GET http://localhost:8000/api/v1/models/artifacts-info`
# Persisted-feature prediction

`POST /api/v2/fraud/predict` scores a versioned feature map calculated and persisted by Spring Boot. The endpoint aligns supplied values to the model artifact's stored feature columns and does not recalculate behavioural features.

The legacy `POST /api/v1/fraud/predict` endpoint remains available only during the Phase 12 migration window. It is marked deprecated, returns `Deprecation` and `Sunset` headers, and is not called by production Spring code.

Production scoring uses `POST /api/v2/fraud/predict` with persisted Spring feature vectors. Only a promoted Half-Space Trees champion or the Isolation Forest batch fallback can influence production decisions. LOF, kernel One-Class SVM, Elliptic Envelope, and PCA Reconstruction are offline-comparison models.

Legacy migration controls:

```powershell
$env:LEGACY_API_ENABLED='true'
$env:LEGACY_API_SUNSET_AT='2026-12-31T23:59:59Z'
```
