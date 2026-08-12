export interface AuthUser {
  id: number;
  username: string;
  fullName: string;
  roleName: string;
  active: boolean;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  fullName: string;
}
