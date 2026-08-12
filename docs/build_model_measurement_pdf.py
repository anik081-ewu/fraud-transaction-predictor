from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import (
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


OUTPUT_DIR = Path(__file__).resolve().parent
OUTPUT_PATH = OUTPUT_DIR / "Anomaly_Model_Measurement_Guide.pdf"

NAVY = colors.HexColor("#17324D")
BLUE = colors.HexColor("#2563EB")
LIGHT_BLUE = colors.HexColor("#EAF2FF")
PALE_BLUE = colors.HexColor("#F5F8FC")
LIGHT_GRAY = colors.HexColor("#F2F4F7")
MID_GRAY = colors.HexColor("#667085")
GREEN = colors.HexColor("#087A55")
PALE_GREEN = colors.HexColor("#E9F8F1")
AMBER = colors.HexColor("#8A5A00")
PALE_AMBER = colors.HexColor("#FFF5D6")
BLACK = colors.HexColor("#111827")
WHITE = colors.white


styles = getSampleStyleSheet()
styles.add(
    ParagraphStyle(
        name="GuideTitle",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=24,
        leading=27,
        textColor=NAVY,
        alignment=TA_LEFT,
        spaceAfter=10,
    )
)
styles.add(
    ParagraphStyle(
        name="GuideSubtitle",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=11.5,
        leading=16,
        textColor=MID_GRAY,
        spaceAfter=15,
    )
)
styles.add(
    ParagraphStyle(
        name="GuideH1",
        parent=styles["Heading1"],
        fontName="Helvetica-Bold",
        fontSize=15,
        leading=18,
        textColor=BLUE,
        spaceBefore=13,
        spaceAfter=7,
        keepWithNext=True,
    )
)
styles.add(
    ParagraphStyle(
        name="GuideH2",
        parent=styles["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=12,
        leading=15,
        textColor=NAVY,
        spaceBefore=9,
        spaceAfter=5,
        keepWithNext=True,
    )
)
styles.add(
    ParagraphStyle(
        name="GuideBody",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=9.5,
        leading=13,
        textColor=BLACK,
        spaceAfter=6,
    )
)
styles.add(
    ParagraphStyle(
        name="GuideSmall",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=7.9,
        leading=10.2,
        textColor=BLACK,
        spaceAfter=0,
    )
)
styles.add(
    ParagraphStyle(
        name="GuideTableHeader",
        parent=styles["BodyText"],
        fontName="Helvetica-Bold",
        fontSize=8,
        leading=9.5,
        textColor=WHITE,
        alignment=TA_LEFT,
    )
)
styles.add(
    ParagraphStyle(
        name="GuideCallout",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=9.2,
        leading=12.5,
        textColor=BLACK,
        spaceAfter=0,
    )
)


def p(text, style="GuideBody"):
    return Paragraph(text, styles[style])


def heading(text, level=1):
    return Paragraph(text, styles["GuideH1" if level == 1 else "GuideH2"])


def callout(label, text, fill=LIGHT_BLUE, label_color=BLUE):
    content = Paragraph(
        f'<font color="{label_color.hexval()}"><b>{label}:</b></font> {text}',
        styles["GuideCallout"],
    )
    table = Table([[content]], colWidths=[6.5 * inch])
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), fill),
                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#D5DCE5")),
                ("LEFTPADDING", (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
            ]
        )
    )
    return KeepTogether([table, Spacer(1, 7)])


def definition(term, text):
    return p(f"<b><font color='#17324D'>{term}.</font></b> {text}")


def data_table(headers, rows, widths, header_fill=NAVY, font_size=7.9):
    header_cells = [p(header, "GuideTableHeader") for header in headers]
    body_style = ParagraphStyle(
        name=f"TableBody{font_size}",
        parent=styles["GuideSmall"],
        fontSize=font_size,
        leading=font_size + 2.2,
    )
    data = [header_cells]
    for row in rows:
        data.append([Paragraph(str(value), body_style) for value in row])
    table = Table(data, colWidths=[width * inch for width in widths], repeatRows=1, hAlign="CENTER")
    commands = [
        ("BACKGROUND", (0, 0), (-1, 0), header_fill),
        ("GRID", (0, 0), (-1, -1), 0.45, colors.HexColor("#D7DEE8")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]
    for row_index in range(1, len(data)):
        if row_index % 2 == 0:
            commands.append(("BACKGROUND", (0, row_index), (-1, row_index), PALE_BLUE))
    table.setStyle(TableStyle(commands))
    return [table, Spacer(1, 8)]


def page_furniture(canvas, document):
    canvas.saveState()
    width, height = letter
    canvas.setStrokeColor(colors.HexColor("#D9E1EA"))
    canvas.setLineWidth(0.5)
    canvas.line(0.75 * inch, height - 0.52 * inch, width - 0.75 * inch, height - 0.52 * inch)
    canvas.setFont("Helvetica-Bold", 7.5)
    canvas.setFillColor(MID_GRAY)
    canvas.drawString(0.75 * inch, height - 0.40 * inch, "FRAUD TRANSACTION DETECTOR | TECHNICAL GUIDE")
    canvas.setFont("Helvetica", 7.5)
    canvas.drawRightString(
        width - 0.75 * inch,
        0.42 * inch,
        f"Anomaly model measurement methodology | Page {document.page}",
    )
    canvas.restoreState()


def build_pdf():
    document = SimpleDocTemplate(
        str(OUTPUT_PATH),
        pagesize=letter,
        rightMargin=1.0 * inch,
        leftMargin=1.0 * inch,
        topMargin=0.76 * inch,
        bottomMargin=0.68 * inch,
        title="Anomaly Model Measurement Guide",
        author="Fraud Transaction Detector Project",
        subject="Model comparison methodology",
    )
    story = []

    story.append(Spacer(1, 0.18 * inch))
    story.append(p("<b><font color='#2563EB'>ANOMALY MODEL COMPARISON</font></b>", "GuideSmall"))
    story.append(Paragraph("How the Models and Report Metrics Are Measured", styles["GuideTitle"]))
    story.append(
        Paragraph(
            "A professor-ready explanation of native anomaly scores, proxy quality, resampling stability, "
            "data-growth behavior, fit score, and assessment labels",
            styles["GuideSubtitle"],
        )
    )
    story.append(
        callout(
            "Central claim",
            "The dataset has no confirmed fraud labels. The system therefore reports label-free model-selection "
            "indicators, not real fraud accuracy, precision, recall, or F1.",
            fill=PALE_AMBER,
            label_color=AMBER,
        )
    )

    story.append(heading("1. The Three Measurement Layers"))
    story.append(
        definition(
            "Layer 1 - Native model score",
            "Each algorithm produces a model-specific score. Raw magnitudes are meaningful within one trained "
            "model but are not directly comparable across algorithms.",
        )
    )
    story.append(
        definition(
            "Layer 2 - Common proxy evaluation",
            "All models face the same chronological validation rows and generated anomaly challenges. Their results "
            "are converted to common 0-100 indicators.",
        )
    )
    story.append(
        definition(
            "Layer 3 - Data-growth comparison",
            "Evaluation is repeated on the oldest 10%, 25%, 50%, and 100% of transactions to show whether measured "
            "quality and stability improve as data grows.",
        )
    )

    story.append(heading("2. Native Measurement Unit of Each Model"))
    story.append(
        p(
            "Features are standardized before fitting. Except for PCA reconstruction error, native decision values "
            "are dimensionless, model-specific scores. For all five models, the production binary output is "
            "1 = inlier and -1 = anomaly."
        )
    )
    story.extend(
        data_table(
            ["Model", "What it measures", "Native output", "Interpretation"],
            [
                [
                    "Isolation Forest",
                    "Number of random splits needed to isolate a transaction.",
                    "Dimensionless path-based decision score.",
                    "Below 0 anomaly; above 0 inlier. Short paths are unusual.",
                ],
                [
                    "LOF",
                    "Local density relative to nearest neighbors.",
                    "Shifted local-density decision score.",
                    "Below 0 anomaly. Raw LOF is near 1 for typical local density.",
                ],
                [
                    "One-Class SVM",
                    "Signed position relative to a learned normal-data boundary.",
                    "Kernel-space decision value.",
                    "Below 0 anomaly. Magnitude is not probability.",
                ],
                [
                    "Elliptic Envelope",
                    "Robust covariance distance from an elliptical center.",
                    "Shifted robust-distance score.",
                    "Below 0 anomaly. Assumes approximately elliptical structure.",
                ],
                [
                    "PCA Reconstruction",
                    "Mean information loss after compression and reconstruction.",
                    "Mean squared error in standardized-feature units.",
                    "Error above learned percentile threshold is anomaly.",
                ],
            ],
            [1.05, 2.0, 1.55, 1.9],
            font_size=7.25,
        )
    )
    story.append(
        callout(
            "Do not compare raw scores",
            "An SVM value of -2 is not automatically more anomalous than an Isolation Forest value of -0.2. "
            "Compare each native score with its own boundary and distribution.",
        )
    )

    story.append(heading("3. Training and Evaluation Flow"))
    story.extend(
        data_table(
            ["Stage", "Current implementation"],
            [
                ["Chronological partitions", "Oldest 10%, 25%, 50%, and 100% by transaction_date."],
                [
                    "Holdout",
                    "Newest 20% inside each partition, bounded to 50-2,000 rows, is reserved for validation.",
                ],
                [
                    "Tuning limit",
                    "Candidate optimization uses at most 5,000 pre-validation rows.",
                ],
                [
                    "Synthetic challenge",
                    "Copy each validation row; perturb 20% of features by 3-6 standardized units.",
                ],
                [
                    "Candidate selection",
                    "Fit multiple hyperparameter combinations and select the highest proxy score.",
                ],
                [
                    "Stability test",
                    "Refit three times on bootstrap samples sized at 85% of training-row count.",
                ],
            ],
            [1.55, 4.95],
        )
    )

    story.append(heading("4. Proxy Quality Metrics"))
    story.append(
        definition(
            "Proxy Average Precision, 0 to 1",
            "Measures precision-recall ranking of generated anomaly challenges against original validation rows. "
            "Higher is better, but it is not real fraud average precision.",
        )
    )
    story.append(
        definition(
            "Proxy ROC AUC, 0 to 1",
            "Probability that a generated challenge receives a more anomalous score than an original validation row. "
            "0.5 is chance ranking and 1.0 is perfect proxy separation.",
        )
    )
    story.append(
        definition(
            "Validation anomaly rate, 0 to 1",
            "Share of original validation rows predicted as anomalies. It resembles a proxy false-positive rate, "
            "but original data can contain unknown genuine anomalies.",
        )
    )
    story.append(
        definition(
            "Rate-control score, 0 to 100",
            "Rewards an observed validation anomaly rate near the configured 5% target.",
        )
    )
    story.append(
        callout(
            "Rate-control equation",
            "100 x max(0, 1 - |observed anomaly rate - target anomaly rate| / max(target rate, 0.01)).",
            fill=PALE_GREEN,
            label_color=GREEN,
        )
    )
    story.append(
        callout(
            "Proxy score equation",
            "ProxyScore = 100 x (0.50 x AveragePrecision + 0.35 x ROC_AUC + 0.15 x RateControlFraction).",
            fill=PALE_GREEN,
            label_color=GREEN,
        )
    )

    story.append(heading("5. Resampling Stability"))
    story.append(
        p(
            "Three versions of the selected model are trained on independent bootstrap samples. Each sample draws "
            "with replacement and has 85% of the original training-row count. All three models classify the same "
            "chronological validation rows."
        )
    )
    story.append(
        callout(
            "Stability equation",
            "StabilityScore = 100 x mean prediction agreement for model pairs (1,2), (1,3), and (2,3). "
            "A score of 80 means 80% average binary-decision agreement.",
        )
    )
    story.append(
        callout(
            "Important limit",
            "Stability measures repeatability, not correctness. A consistently wrong model can still be stable.",
            fill=PALE_AMBER,
            label_color=AMBER,
        )
    )

    story.append(heading("6. Training Report Columns"))
    story.extend(
        data_table(
            ["UI column", "Unit and formula", "Interpretation"],
            [
                [
                    "Fit score",
                    "0-100. 0.65 x AvgQuality + 0.35 x AvgStability.",
                    "Current overall ranking score.",
                ],
                [
                    "Avg. quality",
                    "0-100. Mean qualityScore across four partitions.",
                    "Average composite candidate quality as data grows.",
                ],
                [
                    "Avg. stability",
                    "Percent. Mean bootstrap agreement across partitions.",
                    "Repeatability under resampled training data.",
                ],
                [
                    "Avg. anomaly rate",
                    "Percent. Mean anomaly_count / partition_rows.",
                    "Expected alert volume; not accuracy.",
                ],
                [
                    "10% to 100%",
                    "Quality_100% minus Quality_10%, in points.",
                    "Direction of quality change as data grows.",
                ],
                [
                    "Assessment",
                    "Rule-based label from stability and quality change.",
                    "Strong, moderate, needs review, or insufficient.",
                ],
            ],
            [1.15, 2.75, 2.6],
            font_size=7.5,
        )
    )

    story.append(heading("7. Exact Meaning of Avg. Quality"))
    story.append(
        callout(
            "Current partition quality equation",
            "QualityScore = 0.65 x ProxyScore + 0.25 x StabilityScore + 0.10 x RateControlScore.",
            fill=PALE_GREEN,
            label_color=GREEN,
        )
    )
    story.append(
        p(
            "The UI then applies 65% AvgQuality and 35% AvgStability. Stability is therefore included once inside "
            "qualityScore and again directly in fit score. The UI phrase 'proxy quality 65% and resampling stability "
            "35%' is simplified; technically the 65% component is composite quality."
        )
    )
    story.append(
        callout(
            "Expanded fit equation",
            "FitScore = 0.4225 x AvgProxyScore + 0.5125 x AvgStability + 0.065 x AvgRateControl.",
            fill=PALE_AMBER,
            label_color=AMBER,
        )
    )
    story.append(
        callout(
            "Recommended wording",
            "The fit score is a label-free composite dominated by resampling stability, with additional "
            "synthetic-challenge separation and anomaly-rate calibration.",
        )
    )

    story.append(heading("8. Assessment Rules"))
    story.extend(
        data_table(
            ["Assessment", "Rule", "Meaning"],
            [
                ["Strong and stable", "Stability >= 80 and change >= -5.", "Highly repeatable; no material deterioration."],
                ["Moderately stable", "Stability >= 65 and change >= -10.", "Reasonably repeatable; tolerable decline."],
                ["Needs review", "Below the moderate thresholds.", "Too variable or quality declines sharply."],
                ["Insufficient metrics", "Stability unavailable.", "No defensible assessment can be produced."],
            ],
            [1.5, 2.25, 2.75],
        )
    )

    story.append(PageBreak())
    story.append(heading("9. Worked Example"))
    story.append(
        p(
            "Assume quality scores of 76, 79, 82, and 84; stability of 80%, 82%, 84%, and 86%; and anomaly rates "
            "of 5%, 5.5%, 4.8%, and 5.2%."
        )
    )
    story.extend(
        data_table(
            ["Calculation", "Result"],
            [
                ["AvgQuality = (76 + 79 + 82 + 84) / 4", "80.25 points"],
                ["AvgStability = (80 + 82 + 84 + 86) / 4", "83.00%"],
                ["AvgAnomalyRate = (5 + 5.5 + 4.8 + 5.2) / 4", "5.125%"],
                ["10% to 100% = 84 - 76", "+8.00 points"],
                ["FitScore = 0.65 x 80.25 + 0.35 x 83.00", "81.21 / 100"],
                ["Assessment", "Strong and stable"],
            ],
            [4.5, 2.0],
            header_fill=BLUE,
        )
    )
    story.append(
        p(
            "The model ranks strongly because measured quality rises with more data, decisions remain consistent "
            "under resampling, and anomaly volume is close to target. This still does not prove real fraud detection."
        )
    )

    story.append(heading("10. Five Models in Viva Language"))
    story.extend(
        data_table(
            ["Model", "One-sentence explanation", "Strength", "Risk"],
            [
                ["Isolation Forest", "Random trees isolate unusual transactions quickly.", "Scalable global anomalies.", "Boundary depends on contamination."],
                ["LOF", "Anomalies have lower density than their neighbors.", "Detects local anomalies.", "Sensitive to neighborhood and dimension."],
                ["One-Class SVM", "Anomalies lie outside a nonlinear normal-data boundary.", "Flexible boundary.", "Sensitive to scaling and tuning."],
                ["Elliptic Envelope", "Anomalies are far from a robust elliptical center.", "Interpretable covariance distance.", "Poor for multimodal shapes."],
                ["PCA Reconstruction", "Anomalies reconstruct poorly from dominant components.", "Captures unusual correlations.", "Linear model misses nonlinear patterns."],
            ],
            [1.15, 2.65, 1.3, 1.4],
            font_size=7.2,
        )
    )

    story.append(heading("11. Safe Claims and Future Validation"))
    story.extend(
        data_table(
            ["Safe claims now", "Requires reviewer-confirmed labels"],
            [
                ["A model is more stable under bootstrap resampling.", "The model has higher real fraud precision."],
                ["A model separates generated challenges more strongly.", "The model catches more actual fraud."],
                ["Anomaly rate is near the operating target.", "Anomaly rate is a true false-positive rate."],
                ["Quality improves or declines as data grows.", "The model has validated accuracy or F1."],
                ["Voting reached the configured case threshold.", "A generated case confirms fraud."],
            ],
            [3.25, 3.25],
            header_fill=BLUE,
        )
    )
    story.append(
        callout(
            "Future validation",
            "After reviewers label cases, report precision, recall, F1, false-positive rate, confusion matrix, "
            "precision-recall curves, calibration, subgroup checks, and time-based backtesting.",
            fill=PALE_GREEN,
            label_color=GREEN,
        )
    )

    story.append(heading("12. Short Viva Script"))
    story.append(definition("Why unsupervised models", "The dataset has no confirmed fraud labels."))
    story.append(
        definition(
            "How algorithms are compared",
            "Native scores are not directly compared. Shared holdout and generated challenges produce common indicators.",
        )
    )
    story.append(
        definition(
            "Why four data sizes",
            "They reveal whether a model improves and remains stable as historical data increases.",
        )
    )
    story.append(
        definition(
            "What best fit means",
            "Highest current label-free composite score, not highest verified fraud accuracy.",
        )
    )
    story.append(
        definition(
            "Why voting",
            "Different models capture different structures; voting reduces dependence on one algorithm.",
        )
    )

    story.append(heading("13. References and Implementation Basis"))
    references = [
        "Project: fraud-ml-service/app/training.py - candidate evaluation, synthetic anomalies, stability, quality, and anomaly rate.",
        "Project: fraud-transaction-ui/src/app/pages/datasets-page.component.ts - averages, fit score, trend, and assessment.",
        "Scikit-learn User Guide: https://scikit-learn.org/stable/modules/outlier_detection.html",
        "Scikit-learn IsolationForest API: https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.IsolationForest.html",
        "Scikit-learn LocalOutlierFactor API: https://scikit-learn.org/stable/modules/generated/sklearn.neighbors.LocalOutlierFactor.html",
        "Scikit-learn OneClassSVM API: https://scikit-learn.org/stable/modules/generated/sklearn.svm.OneClassSVM.html",
        "Mihelich et al. (2024), Interplay of ROC and Precision-Recall AUCs, PMLR 235.",
    ]
    for index, reference in enumerate(references, start=1):
        story.append(p(f"<b>{index}.</b> {reference}", "GuideSmall"))

    story.append(
        callout(
            "Final takeaway",
            "The report is a defensible engineering comparison under missing labels. The strongest scientific term "
            "is 'proxy quality and robustness assessment.' Real fraud effectiveness requires reviewer-confirmed outcomes.",
        )
    )

    document.build(story, onFirstPage=page_furniture, onLaterPages=page_furniture)
    return OUTPUT_PATH


if __name__ == "__main__":
    print(build_pdf())
