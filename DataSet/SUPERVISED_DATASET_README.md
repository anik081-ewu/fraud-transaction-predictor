# Supervised Test Dataset

## Files

- `supervised_bank_transactions_25000.csv`: application-ready synthetic bank transactions.
- `supervised_bank_transactions_25000.summary.json`: generation statistics and SHA-256 checksum.
- `supervised-source/creditcard_openml_42397.csv`: downloaded labelled ULB/Worldline source dataset.
- `supervised-source/creditcard_ulb_stratified_10000.csv`: compact source reference containing all 492 fraud records and 9,508 sampled legitimate records.
- `supervised-source/OPENML_42397_README.md`: source metadata and requested citations.

## Application label

`FraudLabel` uses the supervised-system contract:

- `1`: confirmed suspicious/fraud;
- `0`: confirmed legitimate;
- blank: unresolved and excluded from supervised training.

The generated file contains 25,000 rows, 600 accounts, and 1,250 fraud-labelled rows (5%). The higher fraud rate than the original source is intentional so every 10%, 25%, 50%, and 100% chronological partition contains enough positive examples for manual testing.

## How it was generated

The deterministic generator at `scripts/generate_supervised_bank_dataset.py` uses seed `20260812`. It preserves the source `Class`, `Amount`, `Time`, and anonymized PCA risk patterns as generation inputs, then synthesizes fields understood by this application: accounts, transaction dates, transaction types, channels, locations, age, occupation, login attempts, balances, and previous transaction dates.

Fraud records are not made artificially obvious: 15% use subtle normal-looking behavior. Three percent of legitimate records receive noisy risk-like behavior. This overlap is useful for testing precision/recall trade-offs.

## Limitations

This is synthetic integration and demonstration data. It is suitable for application testing and model-pipeline demonstrations, but it must not be presented as evidence of real-world bank fraud accuracy. The original source covers anonymized European credit-card transactions over two days; the generated dates and customer attributes are synthetic.

Source: OpenML dataset 42397, originally collected through collaboration between Worldline and the Machine Learning Group of Université Libre de Bruxelles.
