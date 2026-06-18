import type { AuthPayload } from "./types";

const API_BASE = (import.meta.env.VITE_ADMIN_API_BASE || "").replace(/\/$/, "");
const AUTH_STORAGE_KEY = "nq_admin_auth";
const CSRF_COOKIE_NAME = "nq_admin_csrf";
type StoredAuthMetadata = Pick<AuthPayload, "admin_user" | "expires_at"> & { csrf_token?: unknown };

export class ApiError extends Error {
  status: number;
  code: string;
  payload: unknown;

  constructor(status: number, code: string, payload: unknown) {
    super(code);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.payload = payload;
  }
}
export function getStoredAuth(): AuthPayload | null {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as StoredAuthMetadata;
    if (!parsed.admin_user) return null;
    const metadata = {
      admin_user: parsed.admin_user,
      expires_at: Number(parsed.expires_at) || 0,
    };
    if ("csrf_token" in parsed) {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(metadata));
    }
    return { ...metadata, csrf_token: readCookie(CSRF_COOKIE_NAME) };
  } catch {
    return null;
  }
}

export function setStoredAuth(payload: AuthPayload | null): void {
  if (!payload) {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return;
  }
  const metadata: StoredAuthMetadata = {
    admin_user: payload.admin_user,
    expires_at: payload.expires_at,
  };
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(metadata));
}

export function csrfToken(): string {
  return readCookie(CSRF_COOKIE_NAME);
}

function readCookie(name: string): string {
  const prefix = `${name}=`;
  return document.cookie
    .split(";")
    .map((item) => item.trim())
    .find((item) => item.startsWith(prefix))
    ?.slice(prefix.length) || "";
}

export interface ApiFetchOptions extends RequestInit {
  json?: unknown;
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  const csrf = csrfToken();
  if (csrf) headers.set("X-Admin-CSRF", csrf);
  if (options.json !== undefined) headers.set("Content-Type", "application/json");

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    body: options.json !== undefined ? JSON.stringify(options.json) : options.body,
    credentials: "include",
    headers,
  });

  const payload = await parseJSON(response);
  if (!response.ok) {
    const code = errorCode(payload) || response.statusText || "request_failed";
    if (response.status === 401) {
      setStoredAuth(null);
      window.dispatchEvent(new CustomEvent("admin:unauthorized"));
    }
    throw new ApiError(response.status, code, payload);
  }
  return payload as T;
}

async function parseJSON(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

function errorCode(payload: unknown): string {
  if (!payload || typeof payload !== "object") return "";
  const value = (payload as { error?: unknown }).error;
  return typeof value === "string" ? value : "";
}

export function toQuery(params: Record<string, string | number | boolean | undefined>): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === "") return;
    query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `?${text}` : "";
}
