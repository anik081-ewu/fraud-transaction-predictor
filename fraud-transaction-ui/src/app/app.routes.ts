import { Routes } from '@angular/router';

import { administratorGuard, authGuard } from './core/auth.guard';
import { LoginPageComponent } from './pages/login-page.component';
import { ModelTuningPageComponent } from './pages/model-tuning-page.component';
import { RegisterPageComponent } from './pages/register-page.component';
import { AnomalyConfigPageComponent } from './pages/anomaly-config-page.component';
import { CasesPageComponent } from './pages/cases-page.component';
import { ManualCasePageComponent } from './pages/manual-case-page.component';
import { TransactionCheckPageComponent } from './pages/transaction-check-page.component';
import { ColdStartConfigPageComponent } from './pages/cold-start-config-page.component';
import { DatasetsPageComponent } from './pages/datasets-page.component';
import { UploadPageComponent } from './pages/upload-page.component';
import { TrainingOperationsPageComponent } from './pages/training-operations-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'datasets' },
  { path: 'login', component: LoginPageComponent },
  { path: 'register', component: RegisterPageComponent },
  { path: 'uploads', component: UploadPageComponent, canActivate: [authGuard] },
  { path: 'datasets', component: DatasetsPageComponent, canActivate: [authGuard] },
{ path: 'training-operations', component: TrainingOperationsPageComponent, canActivate: [administratorGuard] },
  { path: 'config', component: AnomalyConfigPageComponent, canActivate: [administratorGuard] },
  { path: 'cold-start', component: ColdStartConfigPageComponent, canActivate: [administratorGuard] },
  { path: 'model-tuning', component: ModelTuningPageComponent, canActivate: [administratorGuard] },
  { path: 'transaction-check', component: TransactionCheckPageComponent, canActivate: [authGuard] },
  { path: 'cases/manual', component: ManualCasePageComponent, canActivate: [authGuard] },
  { path: 'cases', component: CasesPageComponent, canActivate: [authGuard] },
];
