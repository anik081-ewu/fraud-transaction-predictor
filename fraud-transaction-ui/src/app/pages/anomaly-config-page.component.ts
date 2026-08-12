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
      incrementalSchedule: 'DAILY',
      batchSchedule: 'WEEKLY',
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
    const nextValue = this.clamp(value);
    const current = this.layerWeights();
    const otherKeys = (Object.keys(current) as LayerKey[]).filter((entry) => entry !== key);
    const remaining = 1 - nextValue;
    const totalOthers = otherKeys.reduce((sum, entry) => sum + current[entry], 0);
    const updated = { ...current, [key]: nextValue };

    if (otherKeys.length === 0) {
      this.assignLayerWeights(updated);
      return;
    }

    if (totalOthers <= 0) {
      const even = remaining / otherKeys.length;
      otherKeys.forEach((entry) => { updated[entry] = even; });
    } else {
      otherKeys.forEach((entry) => { updated[entry] = remaining * (current[entry] / totalOthers); });
    }

    this.assignLayerWeights(this.normalizedWeights(updated));
  }

  setCbSubWeight(key: CbSubKey, value: number): void {
    const nextValue = this.clamp(value);
    const current = this.cbSubWeights();
    const otherKeys = (Object.keys(current) as CbSubKey[]).filter((k) => k !== key);
    const remaining = 1 - nextValue;
    const totalOthers = otherKeys.reduce((sum, k) => sum + current[k], 0);
    const updated = { ...current, [key]: nextValue };
    if (totalOthers <= 0) {
      const even = remaining / otherKeys.length;
      otherKeys.forEach((k) => { updated[k] = even; });
    } else {
      otherKeys.forEach((k) => { updated[k] = remaining * (current[k] / totalOthers); });
    }
    const normalized = this.normalizedWeights(updated);
    this.assignCbSubWeights(normalized);
  }

  cbSubTotal(): number {
    return this.cbAmountWeight + this.cbNoveltyWeight + this.cbFrequencyWeight + this.cbTimeGapWeight + this.cbUnusualHourWeight;
  }

  validCbSubWeights(): boolean {
    return Math.abs(this.cbSubTotal() - 1) < 0.000001;
  }

  setPbSubWeight(key: PbSubKey, value: number): void {
    const nextValue = this.clamp(value);
    const current = this.pbSubWeights();
    const otherKeys = (Object.keys(current) as PbSubKey[]).filter((k) => k !== key);
    const remaining = 1 - nextValue;
    const totalOthers = otherKeys.reduce((sum, k) => sum + current[k], 0);
    const updated = { ...current, [key]: nextValue };
    if (totalOthers <= 0) {
      const even = remaining / otherKeys.length;
      otherKeys.forEach((k) => { updated[k] = even; });
    } else {
      otherKeys.forEach((k) => { updated[k] = remaining * (current[k] / totalOthers); });
    }
    const normalized = this.normalizedWeights(updated);
    this.assignPbSubWeights(normalized);
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
      this.redistributeEnabledModels();
      return;
    }

    model.enabled = true;
    const enabled = this.enabledModels();
    const newWeight = 1 / enabled.length;
    enabled.forEach((entry) => { entry.weight = newWeight; });
    this.normalizeModelWeights();
  }

  setModelWeight(modelKey: string, value: number): void {
    const target = this.models.find((entry) => entry.modelKey === modelKey && entry.enabled);
    if (!target) return;
    const enabled = this.enabledModels();
    if (enabled.length === 1) {
      target.weight = 1;
      return;
    }

    const nextValue = this.clamp(value);
    const others = enabled.filter((entry) => entry.modelKey !== modelKey);
    const remaining = 1 - nextValue;
    const otherTotal = others.reduce((sum, entry) => sum + entry.weight, 0);
    target.weight = nextValue;
    if (otherTotal <= 0) {
      const even = remaining / others.length;
      others.forEach((entry) => { entry.weight = even; });
    } else {
      others.forEach((entry) => { entry.weight = remaining * (entry.weight / otherTotal); });
    }
    this.normalizeModelWeights();
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

  familyDescription(family: string): string {
    return family === 'BATCH'
      ? 'Uses a larger historical snapshot and is trained manually from Training Operations.'
      : 'Adapts to evolving behaviour and is trained manually from Training Operations.';
  }

  private assign(policy: RiskPolicyConfig): void {
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
    this.normalizeModelWeights();
  }

  private enabledModels(): EditableModel[] {
    return this.models.filter((model) => model.enabled);
  }

  private redistributeEnabledModels(): void {
    const enabled = this.enabledModels();
    if (enabled.length === 0) return;
    const total = enabled.reduce((sum, model) => sum + model.weight, 0);
    if (total <= 0) {
      const even = 1 / enabled.length;
      enabled.forEach((model) => { model.weight = even; });
      return;
    }
    enabled.forEach((model) => { model.weight /= total; });
    this.normalizeModelWeights();
  }

  private normalizeModelWeights(): void {
    const enabled = this.enabledModels();
    if (enabled.length === 0) return;
    const normalized = this.normalizedWeights(
      Object.fromEntries(enabled.map((model) => [model.modelKey, model.weight]))
    );
    enabled.forEach((model) => { model.weight = normalized[model.modelKey] ?? model.weight; });
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
    const rounded = { ...weights };
    let running = 0;
    keys.forEach((key, index) => {
      if (index === keys.length - 1) {
        rounded[key] = this.clamp(1 - running);
      } else {
        rounded[key] = this.round4(this.clamp(weights[key]));
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
