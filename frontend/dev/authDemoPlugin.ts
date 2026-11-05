import type { IncomingMessage, ServerResponse } from 'node:http';
import { randomUUID } from 'node:crypto';
import type { Plugin } from 'vite';

const MAX_ATTEMPTS = 5;
const LOCK_DURATION_MS = 15 * 60 * 1000;
const DEMO_IDENTITY = 'learner@example.test';
const INITIAL_DEMO_PASSWORD = 'DemoPass123!';
const RESET_CODE = '123456';
const CODE_LIFETIME_MS = 10 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;

interface AttemptState {
  count: number;
  lockedUntil: number | null;
}

interface ResetChallenge {
  expiresAt: number;
  resendAt: number;
  failures: number;
}

interface ResetGrant {
  email: string;
  expiresAt: number;
}

function stringField(payload: unknown, field: string): string | null {
  if (typeof payload !== 'object' || payload === null || !(field in payload)) return null;
  const value = (payload as Record<string, unknown>)[field];
  return typeof value === 'string' ? value : null;
}

function successBody(data: object, message = 'Success') {
  return { success: true, message, data, timestamp: new Date().toISOString() };
}

function respond(response: ServerResponse, status: number, body: object, retryAfter?: number) {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  if (retryAfter !== undefined) response.setHeader('Retry-After', String(retryAfter));
  response.end(JSON.stringify(body));
}

function errorBody(code: string, message: string, path = '/api/v1/auth/login') {
  return {
    success: false,
    errorCode: code,
    message,
    path,
    timestamp: new Date().toISOString(),
  };
}

async function readJson(request: IncomingMessage): Promise<unknown> {
  let raw = '';
  for await (const chunk of request) {
    raw += chunk.toString();
    if (raw.length > 4096) throw new Error('Request body is too large');
  }
  return JSON.parse(raw);
}

export function authDemoPlugin(): Plugin {
  const attempts = new Map<string, AttemptState>();
  const challenges = new Map<string, ResetChallenge>();
  const grants = new Map<string, ResetGrant>();
  let demoPassword = INITIAL_DEMO_PASSWORD;

  return {
    name: 'algolearn-auth-demo',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        const route = request.url?.split('?')[0];
        const resetRoutes = new Set(['/api/v1/auth/forgot-password', '/api/v1/auth/verify-otp', '/api/v1/auth/resend-otp', '/api/v1/auth/reset-password']);
        if (request.method !== 'POST' || !route || !resetRoutes.has(route)) { next(); return; }

        const fail = (status: number, code: string, message: string, retryAfter?: number) =>
          respond(response, status, errorBody(code, message, route), retryAfter);
        let payload: unknown;
        try { payload = await readJson(request); }
        catch { fail(400, 'INVALID_REQUEST', 'Invalid request body.'); return; }

        const email = stringField(payload, 'email')?.trim().toLowerCase();
        if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
          fail(400, 'INVALID_EMAIL', 'Enter a valid email address.'); return;
        }
        const now = Date.now();

        if (route === '/api/v1/auth/forgot-password') {
          challenges.set(email, { expiresAt: now + CODE_LIFETIME_MS, resendAt: now + RESEND_WAIT_MS, failures: 0 });
          respond(response, 200, successBody({ message: 'Reset code sent.' }, 'Reset code sent.'));
          return;
        }

        if (route === '/api/v1/auth/resend-otp') {
          const challenge = challenges.get(email);
          if (challenge && challenge.resendAt > now) {
            fail(429, 'RESEND_TOO_SOON', 'Wait before requesting another code.', Math.ceil((challenge.resendAt - now) / 1000)); return;
          }
          challenges.set(email, { expiresAt: now + CODE_LIFETIME_MS, resendAt: now + RESEND_WAIT_MS, failures: 0 });
          respond(response, 200, successBody({ success: true, message: 'Reset code sent.' }, 'Reset code sent.'));
          return;
        }

        if (route === '/api/v1/auth/verify-otp') {
          const purpose = stringField(payload, 'purpose');
          const code = stringField(payload, 'code');
          if (purpose !== 'RESET_PASSWORD' || !code) { fail(400, 'INVALID_REQUEST', 'Enter a reset verification code.'); return; }
          const challenge = challenges.get(email);
          if (!challenge || challenge.expiresAt <= now || challenge.failures >= MAX_ATTEMPTS) {
            fail(400, 'OTP_EXPIRED', 'Request a new reset code.'); return;
          }
          if (code !== RESET_CODE) {
            challenge.failures += 1;
            fail(400, 'INVALID_OTP', 'That code is invalid.'); return;
          }
          challenges.delete(email);
          const resetToken = randomUUID();
          grants.set(resetToken, { email, expiresAt: now + CODE_LIFETIME_MS });
          respond(response, 200, successBody({ resetToken }, 'Code verified.'));
          return;
        }

        const resetToken = stringField(payload, 'resetToken');
        const newPassword = stringField(payload, 'newPassword');
        const confirmPassword = stringField(payload, 'confirmPassword');
        const grant = resetToken ? grants.get(resetToken) : undefined;
        if (!grant || grant.email !== email || grant.expiresAt <= now) {
          fail(400, 'INVALID_RESET_TOKEN', 'Request and verify a new reset code.'); return;
        }
        if (!newPassword || newPassword.length < 8 || newPassword !== confirmPassword) {
          fail(400, 'INVALID_PASSWORD', 'Use at least 8 matching characters.'); return;
        }
        grants.delete(resetToken!);
        if (email === DEMO_IDENTITY) { demoPassword = newPassword; attempts.delete(email); }
        respond(response, 200, successBody({ success: true, message: 'Password updated.' }, 'Password updated.'));
      });
      server.middlewares.use(async (request, response, next) => {
        if (request.method !== 'POST' || request.url?.split('?')[0] !== '/api/v1/auth/login') {
          next();
          return;
        }

        let payload: unknown;
        try {
          payload = await readJson(request);
        } catch {
          respond(response, 400, errorBody('INVALID_REQUEST', 'Enter a username or email and password.'));
          return;
        }

        if (typeof payload !== 'object' || payload === null || !('usernameOrEmail' in payload) || !('password' in payload)
          || typeof payload.usernameOrEmail !== 'string' || typeof payload.password !== 'string') {
          respond(response, 400, errorBody('INVALID_REQUEST', 'Enter a username or email and password.'));
          return;
        }

        const identity = payload.usernameOrEmail.trim().toLowerCase();
        const now = Date.now();
        const saved = attempts.get(identity);
        const state = saved?.lockedUntil && saved.lockedUntil <= now ? { count: 0, lockedUntil: null } : saved ?? { count: 0, lockedUntil: null };

        if (state.lockedUntil && state.lockedUntil > now) {
          respond(response, 423, errorBody('ACCOUNT_LOCKED', 'Too many unsuccessful attempts. This account is temporarily locked.'), Math.ceil((state.lockedUntil - now) / 1000));
          return;
        }

        if (identity === DEMO_IDENTITY && payload.password === demoPassword) {
          attempts.delete(identity);
          respond(response, 200, {
            success: true,
            message: 'Signed in successfully',
            data: {
              token: 'mock-student',
              user: {
                id: 'auth-demo-student',
                username: 'learner',
                email: DEMO_IDENTITY,
                role: 'STUDENT',
                fullName: 'Demo Learner',
                lifecycleStatus: 'ACTIVE',
              },
            },
            timestamp: new Date().toISOString(),
          });
          return;
        }

        const count = state.count + 1;
        if (count >= MAX_ATTEMPTS) {
          const lockedUntil = now + LOCK_DURATION_MS;
          attempts.set(identity, { count, lockedUntil });
          respond(response, 423, errorBody('ACCOUNT_LOCKED', 'Too many unsuccessful attempts. This account is temporarily locked.'), Math.ceil(LOCK_DURATION_MS / 1000));
          return;
        }

        attempts.set(identity, { count, lockedUntil: null });
        const remaining = MAX_ATTEMPTS - count;
        respond(response, 401, errorBody('INVALID_CREDENTIALS', `${remaining} ${remaining === 1 ? 'attempt' : 'attempts'} remaining before a 15-minute lock.`));
      });
    },
  };
}
