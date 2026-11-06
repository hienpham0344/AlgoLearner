import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Plugin } from 'vite';

const ownerPath = '/api/v1/users/me/profile';
const publicPath = (username: string) => `/api/v1/users/${username}/profile`;
const maxAvatarBytes = 5 * 1024 * 1024;

interface DemoProfile {
  fullName: string | null;
  username: string;
  email: string;
  avatarUrl: string | null;
  shortBio: string | null;
  role: 'STUDENT' | 'TEACHER';
  joinedAt: string;
  isPublic: boolean;
  publicPracticeStatistics: { solved: number };
  streak: { currentStreak: number; highestStreak: number };
}

function json(response: ServerResponse, status: number, data: unknown, message = 'Success') {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  response.end(JSON.stringify(status < 400
    ? { success: true, message, data, timestamp: new Date().toISOString() }
    : { success: false, errorCode: message, message: String(data), timestamp: new Date().toISOString() }));
}

async function body(request: IncomingMessage, limit: number): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let length = 0;
  for await (const part of request) {
    const chunk = Buffer.isBuffer(part) ? part : Buffer.from(part);
    length += chunk.length;
    if (length > limit) throw new Error('TOO_LARGE');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

export function profileDemoPlugin(): Plugin {
  const profiles: Record<'learner' | 'teacher', DemoProfile> = {
    learner: {
      fullName: 'Learner', username: 'learner', email: 'learner@example.test', avatarUrl: null,
      shortBio: 'Learning to build, one small step at a time.', role: 'STUDENT',
      joinedAt: '2026-01-01T00:00:00Z', isPublic: false,
      publicPracticeStatistics: { solved: 18 }, streak: { currentStreak: 7, highestStreak: 12 },
    },
    teacher: {
      fullName: 'Teacher', username: 'teacher', email: 'teacher@example.test', avatarUrl: null,
      shortBio: 'Helping students learn to code.', role: 'TEACHER',
      joinedAt: '2025-09-01T00:00:00Z', isPublic: false,
      publicPracticeStatistics: { solved: 4 }, streak: { currentStreak: 0, highestStreak: 2 },
    },
  };
  const avatars = new Map<string, { bytes: Buffer; type: string }>();

  return {
    name: 'algolearn-profile-demo',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        const route = request.url?.split('?')[0];
        const publicMatch = route?.match(/^\/api\/v1\/users\/(learner|teacher)\/profile(\/avatar)?$/);
        const ownerRoute = route ? [ownerPath, `${ownerPath}/privacy`, `${ownerPath}/avatar`].includes(route) : false;
        if (!route || (!ownerRoute && !publicMatch)) { next(); return; }

        const token = request.headers.authorization;
        const ownerUsername = token === 'Bearer mock-student' ? 'learner' : token === 'Bearer mock-teacher' ? 'teacher' : null;
        if (ownerRoute && !ownerUsername) { json(response, token ? 403 : 401, 'This profile is unavailable to this account.', token ? 'FORBIDDEN' : 'UNAUTHORIZED'); return; }
        const username = ownerRoute ? ownerUsername! : publicMatch![1] as 'learner' | 'teacher';
        const profile = profiles[username];
        const isOwner = ownerUsername === username;
        const avatarPath = `${publicPath(username)}/avatar`;

        if (route === ownerPath && request.method === 'GET') { json(response, 200, profile); return; }
        if (route === ownerPath && request.method === 'PATCH') {
          try {
            const value: unknown = JSON.parse((await body(request, 4096)).toString('utf8'));
            if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('INVALID');
            const fields = Object.keys(value);
            if (!fields.length || fields.some(key => !['fullName', 'shortBio'].includes(key))) throw new Error('INVALID');
            const input = value as Record<string, unknown>;
            if (fields.some(key => input[key] !== null && typeof input[key] !== 'string')) throw new Error('INVALID');
            const name = fields.includes('fullName') ? (input.fullName as string | null)?.trim() ?? '' : profile.fullName;
            const bio = fields.includes('shortBio') ? (input.shortBio as string | null)?.trim() ?? '' : profile.shortBio;
            if ((name && [...name].length > 150) || (bio && [...bio].length > 500)) throw new Error('INVALID');
            profile.fullName = name || null;
            profile.shortBio = bio || null;
            json(response, 200, profile, 'Profile saved.');
          } catch { json(response, 400, 'Check the editable profile fields.', 'VALIDATION_FAILED'); }
          return;
        }
        if (route === `${ownerPath}/privacy` && request.method === 'PATCH') {
          try {
            const value: unknown = JSON.parse((await body(request, 4096)).toString('utf8'));
            if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('INVALID');
            const input = value as Record<string, unknown>;
            if (Object.keys(input).length !== 1 || typeof input.isPublic !== 'boolean') throw new Error('INVALID');
            profile.isPublic = input.isPublic;
            json(response, 200, profile, 'Visibility saved.');
          } catch { json(response, 400, 'Choose a valid visibility setting.', 'VALIDATION_FAILED'); }
          return;
        }
        if (route === `${ownerPath}/avatar` && request.method === 'PUT') {
          try {
            const contentType = request.headers['content-type'];
            if (!contentType?.startsWith('multipart/form-data')) throw new Error('INVALID_TYPE');
            const raw = await body(request, maxAvatarBytes + 1024 * 1024);
            const form = await new Request('http://localhost/upload', { method: 'POST', headers: { 'Content-Type': contentType }, body: raw }).formData();
            const file = form.get('file');
            if (!(file instanceof Blob) || file.size === 0) throw new Error('INVALID_TYPE');
            if (file.size > maxAvatarBytes) throw new Error('TOO_LARGE');
            const bytes = Buffer.from(await file.arrayBuffer());
            const png = file.type === 'image/png' && bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
            const jpeg = file.type === 'image/jpeg' && bytes[0] === 255 && bytes[1] === 216 && bytes[bytes.length - 2] === 255 && bytes[bytes.length - 1] === 217;
            if (!png && !jpeg) throw new Error('INVALID_TYPE');
            avatars.set(username, { bytes, type: file.type });
            profile.avatarUrl = avatarPath;
            json(response, 200, profile, 'Avatar saved.');
          } catch (error) {
            const reason = error instanceof Error ? error.message : 'INVALID_TYPE';
            json(response, reason === 'TOO_LARGE' ? 413 : 415, reason === 'TOO_LARGE' ? 'Image is too large.' : 'Choose a valid PNG or JPEG image.', reason === 'TOO_LARGE' ? 'AVATAR_TOO_LARGE' : 'UNSUPPORTED_AVATAR_TYPE');
          }
          return;
        }
        if (route === publicPath(username) && request.method === 'GET') {
          if (!profile.isPublic) { json(response, 404, 'Profile unavailable.', 'PROFILE_NOT_FOUND'); return; }
          json(response, 200, { username: profile.username, avatarUrl: profile.avatarUrl, shortBio: profile.shortBio,
            solved: profile.publicPracticeStatistics.solved, streak: profile.streak });
          return;
        }
        if (route === avatarPath && request.method === 'GET') {
          const avatar = avatars.get(username);
          if (!avatar || (!profile.isPublic && !isOwner)) { json(response, 404, 'Profile unavailable.', 'PROFILE_NOT_FOUND'); return; }
          response.statusCode = 200;
          response.setHeader('Content-Type', avatar.type);
          response.setHeader('Cache-Control', 'no-store');
          response.end(avatar.bytes);
          return;
        }
        next();
      });
    },
  };
}
