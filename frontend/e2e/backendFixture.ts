import type { Page } from '@playwright/test';

/** HTTP fixture for browser tests only: an available backend, including error responses. */
export async function installBackendFixture(page: Page) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    const headers = { 'x-algolearner-source': 'backend' };
    const identity = (route.request().headers().authorization ?? '').replace('Bearer fixture-', '') || 'student';
    const account = (username: string) => ({ id: `test-${username}`, username, email: `${username}@example.test`,
      role: username.startsWith('teacher') ? 'TEACHER' : username === 'admin' ? 'ADMIN' : 'STUDENT', lifecycleStatus: 'ACTIVE' });
    if (path === '/api/v1/status') return route.fulfill({ headers, json: { data: { source: 'backend' } } });
    if (path === '/api/v1/auth/login') {
      const username = route.request().postDataJSON().usernameOrEmail.split('@')[0];
      return route.fulfill({ headers, json: { data: { token: `fixture-${username}`, user: account(username) } } });
    }
    if (path === '/api/v1/auth/me') return route.fulfill({ headers, json: { data: account(identity) } });
    if (path === '/api/v1/auth/logout') return route.fulfill({ headers, json: { data: {} } });
    if (path === '/api/v1/auth/refresh-token') return route.fulfill({ status: 401, headers, json: { message: 'No session' } });
    if (path === '/api/v1/users/me/profile') return route.fulfill({ headers, json: { data: {
      ...account(identity), fullName: identity, shortBio: '', avatarUrl: null, joinedAt: '2026-11-11T00:00:00Z',
      isPublic: false, publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 },
    } } });
    return route.fulfill({ status: 503, headers, json: { message: 'Test backend section unavailable' } });
  });
}
