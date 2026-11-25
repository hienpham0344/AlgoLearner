import { request as httpRequest, type IncomingMessage } from 'node:http';
import { request as httpsRequest } from 'node:https';
import type { Connect, Plugin } from 'vite';

const offlineRequests = new WeakSet<IncomingMessage>();
export const isOfflineRequest = (request: IncomingMessage) => offlineRequests.has(request);

/** Never replay a request once a connection was established: it may already have written data. */
export function createBackendMiddleware(target: string): Connect.NextHandleFunction {
  const origin = new URL(target);
  if (!['http:', 'https:'].includes(origin.protocol)) throw new Error('Backend target must use HTTP(S).');
  return (request, response, next) => {
    if (!request.url?.startsWith('/api/')) { next(); return; }
    let connected = false;
    let settled = false;
    const headers = { ...request.headers, host: origin.host };
    if (headers.authorization?.startsWith('Bearer mock-')) {
      delete headers.authorization;
      delete headers.cookie;
    }
    const send = origin.protocol === 'https:' ? httpsRequest : httpRequest;
    const upstream = send(new URL(request.url, origin), { method: request.method, headers, agent: false }, incoming => {
      settled = true;
      response.writeHead(incoming.statusCode ?? 502, { ...incoming.headers, 'x-algolearner-source': 'backend' });
      incoming.pipe(response);
      incoming.on('error', () => response.destroy());
    });
    upstream.once('socket', socket => {
      const forward = () => { connected = true; request.pipe(upstream); };
      if (socket.connecting) socket.once(origin.protocol === 'https:' ? 'secureConnect' : 'connect', forward);
      else forward();
    });
    const deadlineMs = request.url?.split('?')[0] === '/api/v1/auth/register' ? 30_000 : 15_000;
    const deadline = setTimeout(() => upstream.destroy(new Error('Backend timed out')), deadlineMs);
    upstream.on('close', () => clearTimeout(deadline));
    request.on('aborted', () => upstream.destroy());
    response.on('close', () => { if (!response.writableFinished) upstream.destroy(); });
    upstream.on('error', (error: NodeJS.ErrnoException) => {
      clearTimeout(deadline);
      if (settled || response.destroyed) return;
      settled = true;
      if (!connected && error.code === 'ECONNREFUSED') {
        offlineRequests.add(request);
        response.setHeader('X-Algolearner-Source', 'mock');
        response.setHeader('Cache-Control', 'no-store');
        next();
      } else {
        response.writeHead(502, { 'Content-Type': 'application/json', 'X-Algolearner-Source': 'backend' });
        response.end(JSON.stringify({ success: false, errorCode: 'BACKEND_UNREACHABLE', message: 'Cannot complete the backend request. Try again.', timestamp: new Date().toISOString() }));
      }
    });
  };
}

export function backendFirstPlugin(target: string): Plugin {
  return { name: 'backend-first', apply: 'serve', configureServer(server) {
    server.middlewares.use(createBackendMiddleware(target));
    server.middlewares.use((request, response, next) => {
      if (!isOfflineRequest(request)) { next(); return; }
      const route = request.url?.split('?')[0];
      let data: object | undefined;
      if (route === '/api/v1/status') data = { source: 'mock' };
      if (route === '/api/v1/auth/logout' && request.method === 'POST') data = {};
      if (route === '/api/v1/auth/me' && request.method === 'GET') {
        const role = request.headers.authorization?.match(/^Bearer mock-(student|teacher|admin)$/)?.[1];
        if (role) data = { id: `offline-${role}`, username: role === 'student' ? 'learner' : role,
          email: `${role === 'student' ? 'learner' : role}@example.test`, role: role.toUpperCase(), lifecycleStatus: 'ACTIVE' };
      }
      if (data) {
        response.setHeader('Content-Type', 'application/json');
        response.end(JSON.stringify({ success: true, data, timestamp: new Date().toISOString() }));
      } else next();
    });
  } };
}

export function offlineNotFoundPlugin(): Plugin {
  return { name: 'offline-api-not-found', apply: 'serve', configureServer(server) {
    server.middlewares.use((request, response, next) => {
      if (!isOfflineRequest(request)) { next(); return; }
      response.writeHead(request.url?.includes('/auth/') ? 401 : 503, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ success: false, errorCode: 'OFFLINE_MOCK', message: 'Backend is offline. This action needs the backend.', timestamp: new Date().toISOString() }));
    });
  } };
}
