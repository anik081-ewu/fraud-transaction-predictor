import fs from "node:fs/promises";
import path from "node:path";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const ROOT = "D:/personal/Final Year Project/fraud-transaction-predictor";
const OUT = path.join(ROOT, "docs/deliverables/Intelligent_Fraud_Transaction_Detection_and_AML_Case_Management_Defense_Presentation.pptx");
const QA = path.join(ROOT, "tmp/project-defense-deck/rendered");
const IMG = {
  unsupervised: path.join(ROOT, "docs/assets/ui-report/08-unsupervised-models.png"),
  fusion: path.join(ROOT, "docs/assets/current-results/supervised-current-measured.png"),
  policy: path.join(ROOT, "docs/assets/current-results/risk-policy-current-measured.png"),
  training: path.join(ROOT, "docs/assets/ui-report/07-training-operations.png"),
  cases: path.join(ROOT, "docs/assets/ui-report/06-case-management.png"),
};

const C = {
  canvas: "#FFFFFF", ink: "#081A3A", muted: "#5E6F89", panel: "#EDF1F6",
  rule: "#B8C2D0", accent: "#3D8DFF", accent2: "#6DCBF4", pale: "#EAF3FF",
  green: "#16A36A", greenPale: "#E8F8F1", red: "#D92D20", redPale: "#FDECEA",
  amber: "#F59E0B", amberPale: "#FFF5DA", navy: "#102A63"
};

async function bytes(file) {
  const b = await fs.readFile(file);
  return b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
}
async function writeBlob(file, blob) {
  await fs.writeFile(file, new Uint8Array(await blob.arrayBuffer()));
}
function text(slide, value, x, y, w, h, size=20, color=C.ink, bold=false, align="left") {
  const shape = slide.shapes.add({
    geometry: "textbox", position: { left:x, top:y, width:w, height:h },
    fill: "none", line: { style:"solid", fill:"none", width:0 }
  });
  shape.text = value;
  shape.text.style = { fontFamily:"Arial", fontSize:size, color, bold, alignment:align };
  return shape;
}
function box(slide, x, y, w, h, fill=C.panel, line="none", radius=false) {
  return slide.shapes.add({
    geometry: radius ? "roundRect" : "rect", position:{left:x,top:y,width:w,height:h},
    fill, line:{style:"solid",fill:line,width:line==="none"?0:1},
    ...(radius ? { borderRadius:"rounded-xl" } : {})
  });
}
function rule(slide, x, y, w, color=C.rule, width=1) {
  return slide.shapes.add({ geometry:"rect", position:{left:x,top:y,width:w,height:width}, fill:color, line:{style:"solid",fill:color,width:0} });
}
function header(slide, title, kicker, number) {
  slide.background.fill = C.canvas;
  text(slide, kicker.toUpperCase(), 64, 34, 620, 24, 14, C.accent, true);
  text(slide, title, 64, 66, 1080, 54, 38, C.ink, true);
  text(slide, String(number).padStart(2,"0"), 1160, 42, 56, 24, 14, C.muted, true, "right");
  rule(slide, 64, 126, 1152, C.rule, 1);
}
function footer(slide, label="INTELLIGENT FRAUD TRANSACTION DETECTION") {
  text(slide, label, 64, 687, 700, 18, 10, C.muted, true);
}
function notes(slide, talk, sources=[]) {
  const sourceBlock = sources.length ? `\n\n[Sources]\n${sources.map(s=>`- ${s}`).join("\n")}\n[/Sources]` : "";
  slide.speakerNotes.textFrame.setText(talk + sourceBlock);
  slide.speakerNotes.setVisible(true);
}
async function image(slide, file, x, y, w, h, alt, fit="contain") {
  slide.images.add({ blob:await bytes(file), contentType:"image/png", alt, fit, position:{left:x,top:y,width:w,height:h} });
}
function metric(slide, label, value, x, y, w, accent=C.accent, note="") {
  rule(slide, x, y, w, accent, 5);
  text(slide, label.toUpperCase(), x, y+16, w, 22, 13, C.muted, true);
  text(slide, value, x, y+43, w, 56, 34, C.ink, true);
  if (note) text(slide, note, x, y+100, w, 36, 14, C.muted, false);
}
function bulletList(slide, items, x, y, w, size=20, gap=55) {
  items.forEach((item,i)=>{
    box(slide,x,y+i*gap+8,8,8,C.accent);
    text(slide,item,x+24,y+i*gap,w-24,gap-4,size,C.ink,false);
  });
}
function cmCell(slide, x,y,w,h,label,value,fill,color) {
  box(slide,x,y,w,h,fill,"none",true);
  text(slide,label,x+10,y+12,w-20,22,13,C.muted,true,"center");
  text(slide,value,x+10,y+40,w-20,35,26,color,true,"center");
}

async function main() {
  await fs.mkdir(QA,{recursive:true});
  await fs.mkdir(path.dirname(OUT),{recursive:true});
  const deck = Presentation.create({slideSize:{width:1280,height:720}});

  // 1 — Title
  {
    const s=deck.slides.add(); s.background.fill=C.canvas;
    box(s,0,0,18,720,C.accent);
    text(s,"ACADEMIC PROJECT DEFENSE",72,70,500,28,15,C.accent,true);
    text(s,"Intelligent Fraud Transaction Detection\nand AML Case Management",72,145,950,150,54,C.ink,true);
    text(s,"A dual-mode, research-guided platform for anomaly discovery, supervised fraud classification, explainable risk scoring, and governed investigation.",72,330,930,90,24,C.muted,false);
    rule(s,72,470,1136,C.rule,1);
    text(s,"Md. Mydul Islam Anik  •  MIT Project  •  August 2026",72,500,900,28,17,C.ink,true);
    text(s,"Institute of Information Technology (IIT), University of Dhaka",72,538,900,24,16,C.muted,false);
    text(s,"MEASURED EVIDENCE • REPRODUCIBLE DATA • EXPLICIT LIMITATIONS",72,635,1000,22,13,C.accent,true);
    notes(s,"Open by framing this as a complete investigation platform, not only a classifier. The defense will separate measured evidence from demo-only values.");
  }

  // 2 — Problem
  {
    const s=deck.slides.add(); header(s,"Fraud monitoring fails when one constraint is solved in isolation","The problem",2);
    text(s,"01",64,166,90,60,44,C.accent,true); text(s,"Labels are often absent",160,170,420,34,25,C.ink,true);
    text(s,"A new client may have transactions but no reviewed fraud outcomes.",160,210,440,52,18,C.muted);
    text(s,"02",64,310,90,60,44,C.accent,true); text(s,"Fraud is rare and time-dependent",160,314,450,34,25,C.ink,true);
    text(s,"Random splits and plain accuracy can hide leakage and missed fraud.",160,354,440,52,18,C.muted);
    text(s,"03",660,166,90,60,44,C.accent,true); text(s,"Real-time decisions must stay fast",756,170,440,34,25,C.ink,true);
    text(s,"Millions of rows belong in offline training—not inside one transaction request.",756,210,450,52,18,C.muted);
    text(s,"04",660,310,90,60,44,C.accent,true); text(s,"A score must be explainable",756,314,430,34,25,C.ink,true);
    text(s,"AML staff need reasons, policy versions, cases, and auditable outcomes.",756,354,440,52,18,C.muted);
    box(s,64,500,1152,112,C.navy);
    text(s,"Design question",88,520,210,24,14,"#FFFFFF",true);
    text(s,"How can one system learn before labels exist, improve after labels arrive, scale to bank volumes, and still explain every decision?",88,552,1080,42,25,"#FFFFFF",true);
    footer(s); notes(s,"Use this slide to establish the four problems the architecture must solve together.",[
      "https://fraud-detection-handbook.github.io/fraud-detection-handbook/Chapter_4_PerformanceMetrics/Summary.html",
      "https://proceedings.mlr.press/v151/bach-nguyen22a.html"
    ]);
  }

  // 3 — Research map
  {
    const s=deck.slides.add(); header(s,"Published methods shaped the design—not the claimed results","Research-guided approach",3);
    const rows=[
      ["Fraud Detection Handbook","Chronological evaluation, rare-event metrics, reproducible simulator"],
      ["Isolation Forest + autoencoder literature","Complementary unsupervised anomaly views"],
      ["Random Forest, Extra Trees, XGBoost","Robust nonlinear supervised baselines"],
      ["Excess-Mass and anomaly-evaluation research","Label-free comparison without calling it fraud accuracy"],
      ["Future-information research","Point-in-time features and leakage-safe holdouts"]
    ];
    rows.forEach((r,i)=>{
      const y=166+i*88;
      text(s,String(i+1).padStart(2,"0"),64,y+8,48,26,15,C.accent,true);
      text(s,r[0],132,y,330,32,22,C.ink,true);
      text(s,r[1],500,y,670,48,18,C.muted,false);
      rule(s,132,y+66,1038,C.rule,1);
    });
    text(s,"Research determines the protocol. Project measurements determine the conclusion.",132,626,980,28,22,C.accent,true);
    footer(s); notes(s,"Make the distinction explicit: the project follows journal and handbook methods, but does not present published authors' numbers as its own.",[
      "https://github.com/Fraud-Detection-Handbook/fraud-detection-handbook",
      "https://doi.org/10.1109/ICDM.2008.17",
      "https://doi.org/10.1007/s10994-006-6226-1",
      "https://doi.org/10.1145/2939672.2939785",
      "https://proceedings.mlr.press/v38/goix15.html",
      "https://arxiv.org/abs/1607.01152",
      "https://proceedings.mlr.press/v151/bach-nguyen22a.html"
    ]);
  }

  // 4 — Dataset
  {
    const s=deck.slides.add(); header(s,"The main supervised benchmark preserves chronology and rarity","Dataset provenance",4);
    metric(s,"Transactions","264,500",64,164,250,C.accent,"Adapted simulator rows");
    metric(s,"Fraud rows","2,340",350,164,250,C.red,"0.8847% prevalence");
    metric(s,"Customers","749",636,164,250,C.green,"Complete histories");
    metric(s,"Calendar span","183 days",922,164,250,C.amber,"Apr–Sep 2018");
    box(s,64,340,540,220,C.pale);
    text(s,"Source",88,366,180,24,15,C.accent,true);
    text(s,"Fraud Detection Handbook\nsimulated-data-transformed",88,402,450,66,27,C.ink,true);
    text(s,"Terminal identity is mapped to Location. Fraud labels and scenario identifiers are never model inputs.",88,490,455,54,17,C.muted);
    box(s,636,340,536,220,C.panel);
    text(s,"Leakage-safe split",660,366,240,24,15,C.accent,true);
    text(s,"70% train  →  10% calibrate  →  20% untouched test",660,408,455,64,24,C.ink,true);
    text(s,"Thresholds are chosen on calibration only; the newest period reports final performance.",660,494,455,48,17,C.muted);
    text(s,"Evidence boundary: reproducible simulated evidence—not a claim about a live bank population.",64,606,1100,30,20,C.red,true);
    footer(s); notes(s,"Explain why rarity and chronological splitting matter. The fraud scenario identifier remains diagnostic metadata only.",[
      "https://fraud-detection-handbook.github.io/fraud-detection-handbook/Chapter_3_GettingStarted/SimulatedDataset.html",
      "https://fraud-detection-handbook.github.io/fraud-detection-handbook/Chapter_3_GettingStarted/BaselineModeling.html"
    ]);
  }

  // 5 — Dual mode
  {
    const s=deck.slides.add(); header(s,"One setting changes the learning path; the operational workflow remains","Dual-mode architecture",5);
    box(s,64,162,310,420,C.pale); text(s,"UNSUPERVISED",90,190,250,28,18,C.accent,true);
    text(s,"When labels are absent",90,232,250,36,27,C.ink,true);
    bulletList(s,["Isolation Forest","Autoencoder","Behavioral Cluster Outlier","EM-AUC, score shape, stability"],90,300,250,18,55);
    box(s,454,162,310,420,C.greenPale); text(s,"SUPERVISED",480,190,250,28,18,C.green,true);
    text(s,"When reviewed labels exist",480,232,250,62,27,C.ink,true);
    bulletList(s,["XGBoost","Class-Balanced Random Forest","Extra Trees","PR-AUC, precision, recall, F1"],480,312,250,18,55);
    box(s,844,162,372,420,C.panel); text(s,"SHARED OPERATIONS",870,190,300,28,18,C.muted,true);
    text(s,"The rest of the system does not reset",870,232,300,62,27,C.ink,true);
    bulletList(s,["Customer behaviour","Peer behaviour","AML rules","Risk policy and cases","STR workflow and audit"],870,312,300,18,48);
    text(s,"This avoids forcing a client without labels to pretend supervised accuracy exists.",64,620,1100,28,21,C.accent,true);
    footer(s); notes(s,"The Settings page chooses the mode. Uploaded labelled rows support supervised learning; null labels are excluded. Unsupervised evidence remains available for clients without reviewed outcomes.");
  }

  // 6 — Four layers
  {
    const s=deck.slides.add(); header(s,"The final 0–100 score combines four independently explainable layers","Layered risk decision",6);
    const cols=[
      ["Customer behaviour","Personal amount, velocity, novelty, confidence",C.accent],
      ["Peer behaviour","Occupation-age peers with fallback hierarchy",C.green],
      ["ML ensemble","Selected supervised or unsupervised detectors",C.navy],
      ["AML rules","Deterministic structuring, velocity, sanctions, turnover",C.amber]
    ];
    cols.forEach((r,i)=>{
      const x=64+i*288;
      rule(s,x,174,246,r[2],6);
      text(s,r[0],x,202,246,36,23,C.ink,true);
      text(s,r[1],x,252,246,92,17,C.muted);
      text(s,"0–1 normalized",x,364,246,24,15,r[2],true);
    });
    rule(s,64,430,1152,C.rule,2);
    text(s,"Final score",64,458,220,30,20,C.muted,true);
    text(s,"Σ (component score × configured weight) × 100",286,450,720,48,31,C.ink,true);
    text(s,"= 100%",1040,450,176,48,31,C.accent,true,"right");
    box(s,64,544,1152,78,C.navy);
    text(s,"MEDIUM creates a case. HIGH creates a case and prepares draft STR output. Model votes stay visible as diagnostics; policy weights determine the decision.",88,566,1104,40,19,"#FFFFFF",true);
    footer(s); notes(s,"Emphasize that model votes alone do not equal the final score. Each component is normalized, weighted, versioned, and explainable.");
  }

  // 7 — Unsupervised
  {
    const s=deck.slides.add(); header(s,"Behavioral Cluster Outlier gives the strongest current label-free separation","Unsupervised evidence",7);
    s.charts.add("bar",{
      position:{left:64,top:172,width:680,height:400},
      categories:["Isolation Forest","Autoencoder","Behavioral Cluster Outlier"],
      series:[{name:"EM-AUC",values:[67.2,85.6,89.2],fill:C.accent}],
      hasLegend:false, dataLabels:{showValue:true,position:"outEnd"},
      xAxis:{minimumScale:0,maximumScale:100,majorUnit:20},
      yAxis:{majorGridlines:{style:"solid",fill:"#D8E0EA",width:1}}
    });
    metric(s,"Best EM-AUC","89.2 / 100",790,184,380,C.green,"Behavioral Cluster Outlier");
    metric(s,"Lowest anomaly rate","0.82%",790,334,380,C.accent,"412 of 50,000 rows");
    metric(s,"Fastest training","1.31 s",790,484,380,C.amber,"Isolation Forest");
    text(s,"EM-AUC evaluates anomaly-score structure; it is not fraud precision or recall.",64,620,1100,28,21,C.red,true);
    footer(s); notes(s,"These are current measured values on the 50,000-row unlabeled snapshot. Explain why EM-AUC and anomaly rate are appropriate before confirmed labels exist.",[
      "https://proceedings.mlr.press/v38/goix15.html",
      "https://arxiv.org/abs/1607.01152",
      "https://doi.org/10.1109/ICDM.2008.17",
      "https://doi.org/10.1145/2689746.2689747"
    ]);
  }

  // 8 — Growth
  {
    const s=deck.slides.add(); header(s,"Every model is retested as the oldest history grows","Growth comparison protocol",8);
    const xs=[120,390,660,930], labels=["10%","25%","50%","100%"];
    rule(s,154,285,810,C.rule,5);
    labels.forEach((label,i)=>{
      box(s,xs[i],250,72,72,i===3?C.accent:C.pale,i===3?C.accent:C.rule,true);
      text(s,label,xs[i],269,72,28,22,i===3?"#FFFFFF":C.ink,true,"center");
      text(s,["Early signal","Emerging pattern","Stability check","Full history"][i],xs[i]-34,344,140,30,16,C.muted,true,"center");
    });
    text(s,"Oldest transactions first",64,166,420,34,27,C.ink,true);
    text(s,"The timestamp order never changes; only the amount of available history grows.",64,206,700,36,18,C.muted);
    text(s,"What the matrix reveals",64,430,350,32,24,C.ink,true);
    bulletList(s,["Does quality improve, decline, or oscillate?","Does anomaly/classification volume stabilize?","What is the training and scoring cost?","Which model remains credible at production scale?"],64,478,1080,19,44);
    footer(s); notes(s,"The comparison uses oldest-first 10%, 25%, 50%, and 100% partitions with the same chronological holdout logic. Results are cached by immutable snapshot so reopening a page does not rerun training.",[
      "https://fraud-detection-handbook.github.io/fraud-detection-handbook/Chapter_3_GettingStarted/BaselineModeling.html",
      "https://proceedings.mlr.press/v151/bach-nguyen22a.html"
    ]);
  }

  // 9 — Supervised protocol
  {
    const s=deck.slides.add(); header(s,"Supervised models are calibrated for rare-event decisions—not raw accuracy","Supervised protocol",9);
    const models=[["XGBoost","Gradient boosting for nonlinear interactions"],["Class-Balanced Random Forest","Robust bagged benchmark under imbalance"],["Extra Trees","Randomized splits for diversity and efficiency"]];
    models.forEach((m,i)=>{
      const y=170+i*108;
      text(s,String(i+1).padStart(2,"0"),64,y,60,42,28,C.accent,true);
      text(s,m[0],140,y,410,36,24,C.ink,true);
      text(s,m[1],560,y,600,36,18,C.muted);
      rule(s,140,y+66,1020,C.rule,1);
    });
    box(s,64,510,1152,112,C.panel);
    text(s,"Operating-point rule",88,530,240,22,15,C.accent,true);
    text(s,"Choose the threshold on calibration data to maximize recall while maintaining at least 70% calibration precision. Report once on the untouched newest 20%.",88,562,1080,44,21,C.ink,true);
    footer(s); notes(s,"The threshold is not manipulated to force 90% precision. It is chosen once on calibration data under an explicit business constraint, then evaluated on untouched data.",[
      "https://doi.org/10.1145/2939672.2939785",
      "https://doi.org/10.1023/A:1010933404324",
      "https://doi.org/10.1007/s10994-006-6226-1",
      "https://fraud-detection-handbook.github.io/fraud-detection-handbook/Chapter_4_PerformanceMetrics/TopKBased.html"
    ]);
  }

  // 10 — Main result
  {
    const s=deck.slides.add(); header(s,"Extra Trees exceeds 70% precision and recall on the untouched period","Strongest reproducible result",10);
    metric(s,"PR-AUC","73.3%",64,166,230,C.accent,"Ranking quality");
    metric(s,"Precision","74.8%",324,166,230,C.green,"323 / 432 alerts");
    metric(s,"Recall","71.8%",584,166,230,C.green,"323 / 450 frauds");
    metric(s,"F1","73.2%",844,166,230,C.accent,"Balanced operating point");
    text(s,"Untouched newest 52,900 transactions",64,336,500,28,22,C.ink,true);
    text(s,"Actual legitimate",64,408,160,24,16,C.muted,true);
    text(s,"Actual fraud",64,518,160,24,16,C.muted,true);
    cmCell(s,230,374,210,92,"True negative","52,341",C.greenPale,C.green);
    cmCell(s,454,374,210,92,"False positive","109",C.redPale,C.red);
    cmCell(s,230,484,210,92,"False negative","127",C.redPale,C.red);
    cmCell(s,454,484,210,92,"True positive","323",C.greenPale,C.green);
    box(s,730,374,442,202,C.pale);
    text(s,"Why this matters",758,398,300,28,18,C.accent,true);
    text(s,"The result remains credible under 0.8847% fraud prevalence, chronological separation, and a threshold chosen before the final test period.",758,440,365,98,23,C.ink,true);
    footer(s); notes(s,"This is the strongest reproducible benchmark in the project. It demonstrates a useful precision-recall operating point without claiming deployment readiness.",[
      "https://github.com/Fraud-Detection-Handbook/fraud-detection-handbook",
      "https://doi.org/10.1007/s10994-006-6226-1"
    ]);
  }

  // 11 — Fusion
  {
    const s=deck.slides.add(); header(s,"Fusion is useful only when measured—it does not automatically beat one model","Fusion evidence",11);
    await image(s,IMG.fusion,64,164,730,392,"Measured standalone and fusion comparison", "contain");
    box(s,834,164,338,392,C.panel);
    text(s,"Measured conclusion",858,190,290,26,16,C.accent,true);
    text(s,"Weighted probability ensemble",858,234,280,56,25,C.ink,true);
    text(s,"58.7% precision\n65.9% recall\n62.0% F1",858,310,280,110,30,C.navy,true);
    text(s,"The fusion result is close to—not clearly better than—the strongest standalone classifier on this 25,000-row integration snapshot.",858,438,270,86,18,C.muted);
    text(s,"Temporal stacking remains a research comparison but is removed from production-model selection.",64,602,1100,32,20,C.red,true);
    footer(s); notes(s,"Use this slide to demonstrate scientific honesty. Ensembles can improve robustness, but correlated errors and weak calibration can prevent a gain. Production therefore uses weighted registered base classifiers.");
  }

  // 12 — Policy replay
  {
    const s=deck.slides.add(); header(s,"The current four-layer policy needs calibration before deployment","Full policy replay",12);
    await image(s,IMG.policy,64,160,710,414,"Measured four-layer risk policy replay", "contain");
    metric(s,"Case precision","54.6%",824,174,340,C.red,"99 false positives");
    metric(s,"Case recall","50.2%",824,310,340,C.red,"118 frauds missed");
    metric(s,"Balanced accuracy","74.1%",824,446,340,C.amber,"Measured on same held-out rows");
    box(s,64,596,1100,48,C.redPale);
    text(s,"Customer, peer, and rules improve explanation and AML control—but current weights do not yet improve classification over ML-only.",84,607,1060,26,19,C.red,true);
    footer(s); notes(s,"The frozen policy used 25% customer behaviour, 10% peer behaviour, 60% ML, and 5% AML rules. The limitation is reported rather than hidden. Next work is calibration on a separate validation period and optimizing case/STR thresholds by investigator capacity.");
  }

  // 13 — Scalability
  {
    const s=deck.slides.add(); header(s,"Offline learning and online scoring are separated to bound latency and memory","Scalability design",13);
    const stages=[
      ["1","Commit transaction","Persist once; keep the decision path short"],
      ["2","Read point-in-time features","Bounded history and loaded profile state"],
      ["3","Score loaded artifacts","No retraining inside the request"],
      ["4","Commit decision","Create case/STR work after scoring"]
    ];
    stages.forEach((r,i)=>{
      const x=64+i*288;
      text(s,r[0],x,174,52,50,38,C.accent,true);
      text(s,r[1],x,230,246,58,23,C.ink,true);
      text(s,r[2],x,302,246,82,17,C.muted);
    });
    rule(s,64,414,1152,C.rule,1);
    text(s,"Offline training",64,450,240,28,22,C.ink,true);
    bulletList(s,["Keyset export to immutable Parquet parts","Compact bounded features avoid the earlier 19.8 GiB one-hot allocation","Checksummed snapshots and versioned candidate artifacts","Kafka is the next transport layer—not the governance store"],64,496,1100,18,40);
    footer(s); notes(s,"The 264,500-row pipeline uses compact bounded features and immutable snapshots. Kafka can distribute committed transaction events and isolate back pressure, but SQL Server remains the audit and governance system of record.");
  }

  // 14 — Workflow
  {
    const s=deck.slides.add(); header(s,"The prototype closes the loop from data to investigation","End-to-end workflow",14);
    await image(s,IMG.training,64,160,540,270,"Training Operations interface", "contain");
    await image(s,IMG.cases,676,160,540,270,"Case Management interface", "contain");
    const flow=["Upload & validate","Freeze snapshot","Train & compare","Configure policy","Score transaction","Review case / STR"];
    flow.forEach((label,i)=>{
      const x=64+i*192;
      box(s,x,500,160,64,i===5?C.navy:C.pale,i===5?C.navy:C.rule,true);
      text(s,label,x+8,518,144,30,16,i===5?"#FFFFFF":C.ink,true,"center");
      if(i<5) text(s,"→",x+164,514,28,34,26,C.accent,true,"center");
    });
    text(s,"JWT roles protect administration; model registry and policy versions preserve auditability; pagination keeps investigations usable at scale.",64,610,1120,36,19,C.muted,true);
    footer(s); notes(s,"Walk through the administrator and analyst experience. Training creates candidates but does not silently activate them. MEDIUM creates a case; HIGH prepares draft STR output.");
  }

  // 15 — Conclusion
  {
    const s=deck.slides.add(); header(s,"The project proves a credible architecture—and exposes the next research target","Conclusion",15);
    text(s,"PROVEN",64,170,250,28,18,C.green,true);
    bulletList(s,["Dual-mode learning before and after labels exist","Oldest-first growth comparison with cached evidence","74.8% precision and 71.8% recall on the strongest reproducible benchmark","Explainable four-layer scoring, cases, and draft STR workflow","Bounded online scoring with governed offline training"],64,214,520,19,58);
    text(s,"NOT YET PROVEN",680,170,300,28,18,C.red,true);
    bulletList(s,["Live-bank generalization","Production false-positive cost","Fairness across customer segments","That the current layered policy beats ML-only","Regulatory acceptance of generated STR XML"],680,214,510,19,58);
    box(s,64,548,1152,80,C.navy);
    text(s,"Next research milestone",88,566,240,22,14,"#FFFFFF",true);
    text(s,"Calibrate customer, peer, ML, and rule weights on a separate validation period—then prove improvement on an untouched future window.",334,560,830,48,22,"#FFFFFF",true);
    footer(s); notes(s,"Close by resolving the opening question: the architecture works, the strongest supervised model crosses 70% precision and recall, and the remaining weakness is specifically the calibration of the full layered policy—not an unexplained failure.");
  }

  for (const [i,s] of deck.slides.items.entries()) {
    const stem=`slide-${String(i+1).padStart(2,"0")}`;
    await writeBlob(path.join(QA,`${stem}.png`),await deck.export({slide:s,format:"png",scale:1}));
    const layout=await s.export({format:"layout"});
    await fs.writeFile(path.join(QA,`${stem}.layout.json`),await layout.text());
  }
  await writeBlob(path.join(QA,"deck-montage.webp"),await deck.export({format:"webp",montage:true,scale:1}));
  const pptx=await PresentationFile.exportPptx(deck);
  await pptx.save(OUT);
  console.log(OUT);
}

main().catch(e=>{console.error(e);process.exitCode=1;});
