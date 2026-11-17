import test from 'node:test';
import assert from 'node:assert/strict';
import { validateAvatar } from '../src/features/profile/validateAvatar.ts';

test('avatar validation accepts PNG/JPEG within the backend limit', () => {
  assert.equal(validateAvatar({ type: 'image/png', size: 1024 }), null);
  assert.equal(validateAvatar({ type: 'image/jpeg', size: 5 * 1024 * 1024 }), null);
});

test('avatar validation rejects unsupported, empty and oversized files before upload', () => {
  assert.match(validateAvatar({ type: 'image/gif', size: 1024 }), /PNG or JPEG/);
  assert.match(validateAvatar({ type: 'image/png', size: 0 }), /non-empty/);
  assert.match(validateAvatar({ type: 'image/jpeg', size: 5 * 1024 * 1024 + 1 }), /5 MB/);
});
