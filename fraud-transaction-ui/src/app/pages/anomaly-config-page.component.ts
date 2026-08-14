import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AlertService } from '../core/alert.service';
import { ComparisonApiService } from '../core/comparison-api.service';
import { AmlRuleThresholds, CustomerBehaviourSubWeights, PeerBehaviourSubWeights, RiskPolicyConfig, RiskPolicyModelConfig } from '../core/models';

type LayerKey = 'customerBehaviourWeight' | 'peerBehaviourWeight' | 'mlEnsembleWeight' | 'rulesWeight';
type CbSubKey = 'cbAmountWeight' | 'cbNoveltyWeight' | 'cbFrequencyWeight' | 'cbTimeGapWeight' | 'cbUnusualHourWeight';
type PbSubKey = 'pbAmountWeight' | 'pbFrequencyWeight' | 'pbExpectedTurnoverWeight';

interface EditableModel extends RiskPolicyModelConfig {}

@Component({
  selector: 'app-anomaly-config-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './anomaly-config-page.component.html',
  styleUrls: ['./page.css', './anomaly-config-page.component.css']
})
export class AnomalyConfigPageComponent implements OnInit {
  private readonly api = inject(ComparisonApiService);
  private readonly alerts = inject(AlertService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly activePolicy = signal<RiskPolicyConfig | null>(null);
  readonly cbModalOpen = signal(false);
  readonly pbModalOpen = signal(false);
  readonly rulesModalOpen = signal(false);
  readonly mlModalOpen = signal(false);

  customerBehaviourWeight = 0.20;
  peerBehaviourWeight = 0.15;
  mlEnsembleWeight = 0.40;
  rulesWeight = 0.25;
  cbAmountWeight = 0.55;
  cbNoveltyWeight = 0.20;
  cbFrequencyWeight = 0.12;
  cbTimeGapWeight = 0.08;
  cbUnusualHourWeight = 0.05;
  pbAmountWeight = 0.60;
  pbFrequencyWeight = 0.25;
  pbExpectedTurnoverWeight = 0.15;
  rulesReportingThreshold = 10000;
  rulesStructuringCount24h = 3;
  rulesRapidTxCount10m = 5;
  rulesHighTxCount1h = 10;
  rulesMultiBeneficiaryCount1h = 4;
  rulesRepeatedAmountCount24h = 4;
  rulesHighCustomerAmountRatio = 4.0;
  rulesExtremeCustomerAmountRatio = 8.0;
  rulesHighBalanceRatio = 0.80;
  rulesHighExpectedTurnoverRatio = 0.50;
  lowRiskThreshold = 0.40;
  mediumRiskThreshold = 0.65;
  highRiskThreshold = 0.80;
  models: EditableModel[] = [];
  learningMode: 'UNSUPERVISED' | 'SUPERVISED' = 'UNSUPERVISED';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getRiskPolicy().subscribe({
      next: (policy) => {
        this.activePolicy.set(policy);
        this.assign(policy);
      },
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Risk policy unavailable');
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  save(): void {
    if (!this.validTopLevelWeights()) {
      this.alerts.error('Top-level layer weights must total exactly 100%.', 'Invalid layer weights');
      return;
    }
    if (!this.validCbSubWeights()) {
      this.alerts.error('Customer behaviour sub-weights must total exactly 100%.', 'Invalid sub-weights');
      return;
    }
    if (!this.validPbSubWeights()) {
      this.alerts.error('Peer behaviour sub-weights must total exactly 100%.', 'Invalid sub-weights');
      return;
    }
    if (!this.validRulesThresholds()) {
      this.alerts.error('One or more AML rule thresholds are invalid.', 'Invalid rule thresholds');
      return;
    }
    if (!this.validModelWeights()) {
      this.alerts.error('Enabled ML model weights must total exactly 100%.', 'Invalid model weights');
      return;
    }
    if (!this.validThresholds()) {
      this.alerts.error('Thresholds must follow LOW < MEDIUM < HIGH.', 'Invalid risk thresholds');
      return;
    }
    this.saving.set(true);
    this.api.updateRiskPolicy({
      customerBehaviourWeight: this.customerBehaviourWeight,
      peerBehaviourWeight: this.peerBehaviourWeight,
      mlEnsembleWeight: this.mlEnsembleWeight,
      rulesWeight: this.rulesWeight,
      customerBehaviourSubWeights: {
        amount: this.cbAmountWeight,
        novelty: this.cbNoveltyWeight,
        frequency: this.cbFrequencyWeight,
        timeGap: this.cbTimeGapWeight,
        unusualHour: this.cbUnusualHourWeight,
      },
      peerBehaviourSubWeights: {
        amount: this.pbAmountWeight,
        frequency: this.pbFrequencyWeight,
        expectedTurnover: this.pbExpectedTurnoverWeight,
      },
      amlRuleThresholds: {
        reportingThreshold: this.rulesReportingThreshold,
        structuringCount24h: this.rulesStructuringCount24h,
        rapidTxCount10m: this.rulesRapidTxCount10m,
        highTxCount1h: this.rulesHighTxCount1h,
        multiBeneficiaryCount1h: this.rulesMultiBeneficiaryCount1h,
        repeatedAmountCount24h: this.rulesRepeatedAmountCount24h,
        highCustomerAmountRatio: this.rulesHighCustomerAmountRatio,
        extremeCustomerAmountRatio: this.rulesExtremeCustomerAmountRatio,
        highBalanceRatio: this.rulesHighBalanceRatio,
        highExpectedTurnoverRatio: this.rulesHighExpectedTurnoverRatio,
      },
      models: this.models.map((model) => ({
        modelKey: model.modelKey,
        enabled: model.enabled,
        weight: model.enabled ? model.weight : 0,
      })),
      lowRiskThreshold: this.lowRiskThreshold,
      mediumRiskThreshold: this.mediumRiskThreshold,
      highRiskThreshold: this.highRiskThreshold,
    }).subscribe({
      next: (policy) => {
        this.activePolicy.set(policy);
        this.alerts.success(
          'Configuration saved',
          'Flexible production strategy is active for shadow scoring and governance review.',
          () => window.location.reload()
        );
      },
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Risk policy not saved');
        this.saving.set(false);
      },
      complete: () => this.saving.set(false),
    });
  }

  setLayerWeight(key: LayerKey, value: number): void {
    this.assignLayerWeights({ ...this.layerWeights(), [key]: this.clamp(value) });
  }

  normalizeLayerWeight(key: LayerKey): void {
    const remaining = this.remainingLayerWeight(key);
    if (remaining < 0 || remaining > 1) {
      this.alerts.error(
        'The other rule weights already exceed 100%. Reduce one of them before using the remaining budget.',
        'Cannot normalize this component'
      );
      return;
    }
    this.setLayerWeight(key, remaining);
  }

  setCbSubWeight(key: CbSubKey, value: number): void {
    this.assignCbSubWeights({ ...this.cbSubWeights(), [key]: this.clamp(value) });
  }


  normalizeCbSubWeights(): void {
    this.assignCbSubWeights(this.normalizedWeights(this.cbSubWeights()));
  }

  cbSubTotal(): number {
    return this.cbAmountWeight + this.cbNoveltyWeight + this.cbFrequencyWeight + this.cbTimeGapWeight + this.cbUnusualHourWeight;
  }

  validCbSubWeights(): boolean {
    return Math.abs(this.cbSubTotal() - 1) < 0.000001;
  }

  setPbSubWeight(key: PbSubKey, value: number): void {
    this.assignPbSubWeights({ ...this.pbSubWeights(), [key]: this.clamp(value) });
  }

  normalizePbSubWeights(): void {
    this.assignPbSubWeights(this.normalizedWeights(this.pbSubWeights()));
  }

  pbSubTotal(): number {
    return this.pbAmountWeight + this.pbFrequencyWeight + this.pbExpectedTurnoverWeight;
  }

  validPbSubWeights(): boolean {
    return Math.abs(this.pbSubTotal() - 1) < 0.000001;
  }

  validRulesThresholds(): boolean {
    return this.rulesReportingThreshold > 0
      && this.rulesStructuringCount24h >= 2
      && this.rulesRapidTxCount10m >= 2
      && this.rulesHighTxCount1h >= this.rulesRapidTxCount10m
      && this.rulesMultiBeneficiaryCount1h >= 2
      && this.rulesRepeatedAmountCount24h >= 2
      && this.rulesHighCustomerAmountRatio > 1
      && this.rulesExtremeCustomerAmountRatio > this.rulesHighCustomerAmountRatio
      && this.rulesHighBalanceRatio > 0 && this.rulesHighBalanceRatio <= 1
      && this.rulesHighExpectedTurnoverRatio > 0 && this.rulesHighExpectedTurnoverRatio <= 1;
  }

  toggleModel(modelKey: string): void {
    const model = this.models.find((entry) => entry.modelKey === modelKey);
    if (!model) return;
    if (model.enabled && this.enabledModels().length === 1) {
      this.alerts.error('Keep at least one production ML model enabled.', 'Model selection required');
      return;
    }

    if (model.enabled) {
      model.enabled = false;
      model.weight = 0;
      return;
    }

    model.enabled = true;
    model.weight = this.round4(Math.max(0, 1 - this.modelWeightTotal()));
  }

  setModelWeight(modelKey: string, value: number): void {
    const target = this.models.find((entry) => entry.modelKey === modelKey && entry.enabled);
    if (!target) return;
    target.weight = this.clamp(value);
  }

  normalizeModelWeight(modelKey: string): void {
    const target = this.models.find((model) => model.modelKey === modelKey && model.enabled);
    if (!target) return;
    const remaining = this.remainingModelWeight(modelKey);
    if (remaining < 0 || remaining > 1) {
      this.alerts.error(
        'The other enabled model weights already exceed 100%. Reduce one before using the remaining budget.',
        'Cannot normalize this model'
      );
      return;
    }
    target.weight = this.round4(remaining);
  }

  topLevelTotal(): number {
    return this.customerBehaviourWeight + this.peerBehaviourWeight + this.mlEnsembleWeight + this.rulesWeight;
  }

  enabledModelCount(): number {
    return this.enabledModels().length;
  }

  modelWeightTotal(): number {
    return this.enabledModels().reduce((sum, model) => sum + model.weight, 0);
  }

  remainingLayerWeight(key: LayerKey): number {
    const weights = this.layerWeights();
    return this.round4(1 - Object.entries(weights)
      .filter(([entryKey]) => entryKey !== key)
      .reduce((sum, [, weight]) => sum + weight, 0));
  }

  remainingModelWeight(modelKey: string): number {
    return this.round4(1 - this.enabledModels()
      .filter((model) => model.modelKey !== modelKey)
      .reduce((sum, model) => sum + model.weight, 0));
  }

  normalizeLabel(value: number): string {
    if (value < 0) return 'Other weights exceed 100%';
    return `Use remaining ${this.percent(value)}`;
  }

  validTopLevelWeights(): boolean {
    return Math.abs(this.topLevelTotal() - 1) < 0.000001;
  }

  validModelWeights(): boolean {
    return this.enabledModelCount() > 0 && Math.abs(this.modelWeightTotal() - 1) < 0.000001;
  }

  validThresholds(): boolean {
    return this.lowRiskThreshold >= 0
      && this.lowRiskThreshold < this.mediumRiskThreshold
      && this.mediumRiskThreshold < this.highRiskThreshold
      && this.highRiskThreshold <= 1;
  }

  percent(value: number): string {
    return `${Math.round(value * 100)}%`;
  }

  effectiveWeight(model: EditableModel): number {
    return model.enabled ? model.weight * this.mlEnsembleWeight : 0;
  }

  private assign(policy: RiskPolicyConfig): void {
    this.learningMode = policy.learningMode;
    this.customerBehaviourWeight = policy.customerBehaviourWeight;
    this.peerBehaviourWeight = policy.peerBehaviourWeight;
    this.mlEnsembleWeight = policy.mlEnsembleWeight;
    this.rulesWeight = policy.rulesWeight;
    const sub = policy.customerBehaviourSubWeights;
    if (sub) {
      this.cbAmountWeight = sub.amount;
      this.cbNoveltyWeight = sub.novelty;
      this.cbFrequencyWeight = sub.frequency;
      this.cbTimeGapWeight = sub.timeGap;
      this.cbUnusualHourWeight = sub.unusualHour;
    }
    const pbSub = policy.peerBehaviourSubWeights;
    if (pbSub) {
      this.pbAmountWeight = pbSub.amount;
      this.pbFrequencyWeight = pbSub.frequency;
      this.pbExpectedTurnoverWeight = pbSub.expectedTurnover;
    }
    const rules = policy.amlRuleThresholds;
    if (rules) {
      this.rulesReportingThreshold = rules.reportingThreshold;
      this.rulesStructuringCount24h = rules.structuringCount24h;
      this.rulesRapidTxCount10m = rules.rapidTxCount10m;
      this.rulesHighTxCount1h = rules.highTxCount1h;
      this.rulesMultiBeneficiaryCount1h = rules.multiBeneficiaryCount1h;
      this.rulesRepeatedAmountCount24h = rules.repeatedAmountCount24h;
      this.rulesHighCustomerAmountRatio = rules.highCustomerAmountRatio;
      this.rulesExtremeCustomerAmountRatio = rules.extremeCustomerAmountRatio;
      this.rulesHighBalanceRatio = rules.highBalanceRatio;
      this.rulesHighExpectedTurnoverRatio = rules.highExpectedTurnoverRatio;
    }
    this.lowRiskThreshold = policy.lowRiskThreshold;
    this.mediumRiskThreshold = policy.mediumRiskThreshold;
    this.highRiskThreshold = policy.highRiskThreshold;
    this.models = policy.models.map((model) => ({ ...model }));
    if (!this.models.some((model) => model.enabled)) {
      this.models[0].enabled = true;
      this.models[0].weight = 1;
    }
  }

  private enabledModels(): EditableModel[] {
    return this.models.filter((model) => model.enabled);
  }

  private layerWeights(): Record<LayerKey, number> {
    return {
      customerBehaviourWeight: this.customerBehaviourWeight,
      peerBehaviourWeight: this.peerBehaviourWeight,
      mlEnsembleWeight: this.mlEnsembleWeight,
      rulesWeight: this.rulesWeight,
    };
  }

  private assignLayerWeights(weights: Record<LayerKey, number>): void {
    this.customerBehaviourWeight = weights.customerBehaviourWeight;
    this.peerBehaviourWeight = weights.peerBehaviourWeight;
    this.mlEnsembleWeight = weights.mlEnsembleWeight;
    this.rulesWeight = weights.rulesWeight;
  }

  private cbSubWeights(): Record<CbSubKey, number> {
    return {
      cbAmountWeight: this.cbAmountWeight,
      cbNoveltyWeight: this.cbNoveltyWeight,
      cbFrequencyWeight: this.cbFrequencyWeight,
      cbTimeGapWeight: this.cbTimeGapWeight,
      cbUnusualHourWeight: this.cbUnusualHourWeight,
    };
  }

  private assignCbSubWeights(weights: Record<CbSubKey, number>): void {
    this.cbAmountWeight = weights.cbAmountWeight;
    this.cbNoveltyWeight = weights.cbNoveltyWeight;
    this.cbFrequencyWeight = weights.cbFrequencyWeight;
    this.cbTimeGapWeight = weights.cbTimeGapWeight;
    this.cbUnusualHourWeight = weights.cbUnusualHourWeight;
  }

  private pbSubWeights(): Record<PbSubKey, number> {
    return {
      pbAmountWeight: this.pbAmountWeight,
      pbFrequencyWeight: this.pbFrequencyWeight,
      pbExpectedTurnoverWeight: this.pbExpectedTurnoverWeight,
    };
  }

  private assignPbSubWeights(weights: Record<PbSubKey, number>): void {
    this.pbAmountWeight = weights.pbAmountWeight;
    this.pbFrequencyWeight = weights.pbFrequencyWeight;
    this.pbExpectedTurnoverWeight = weights.pbExpectedTurnoverWeight;
  }

  private normalizedWeights<T extends string>(weights: Record<T, number>): Record<T, number> {
    const keys = Object.keys(weights) as T[];
    if (keys.length === 0) return weights;
    const total = keys.reduce((sum, key) => sum + this.clamp(weights[key]), 0);
    if (total <= 0) {
      const even = 1 / keys.length;
      return Object.fromEntries(keys.map((key) => [key, even])) as Record<T, number>;
    }
    const rounded = { ...weights };
    let running = 0;
    keys.forEach((key, index) => {
      if (index === keys.length - 1) {
        rounded[key] = this.clamp(1 - running);
      } else {
        rounded[key] = this.round4(this.clamp(weights[key]) / total);
        running += rounded[key];
      }
    });
    return rounded;
  }

  private round4(value: number): number {
    return Math.round(value * 10000) / 10000;
  }

  private clamp(value: number): number {
    if (!Number.isFinite(value)) return 0;
    return Math.max(0, Math.min(1, value));
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Unable to update the risk policy.';
  }
}
