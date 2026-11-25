import test from 'node:test';
import assert from 'node:assert/strict';
import { parseSessionDevice } from '../src/features/profile/parseSessionDevice.ts';

test('parseSessionDevice parses real Linux Chrome user agent into clean label', () => {
  const ua =
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36';
  const parsed = parseSessionDevice(ua);

  assert.equal(parsed.label, 'Chrome on Linux');
  assert.equal(parsed.browser, 'Chrome');
  assert.equal(parsed.os, 'Linux');
  assert.equal(parsed.category, 'desktop');
  assert.equal(parsed.isParsedUserAgent, true);
  assert.equal(parsed.rawUserAgent, ua);
});

test('parseSessionDevice distinguishes Edge, Firefox, Safari, mobile, and tablet user agents', () => {
  const edgeWin = parseSessionDevice(
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0',
  );
  assert.equal(edgeWin.label, 'Edge on Windows');
  assert.equal(edgeWin.category, 'desktop');

  const safariMac = parseSessionDevice(
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15',
  );
  assert.equal(safariMac.label, 'Safari on macOS');
  assert.equal(safariMac.category, 'desktop');

  const iphone = parseSessionDevice(
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1',
  );
  assert.equal(iphone.label, 'Safari on iOS');
  assert.equal(iphone.category, 'mobile');

  const androidPhone = parseSessionDevice(
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36',
  );
  assert.equal(androidPhone.label, 'Chrome on Android');
  assert.equal(androidPhone.category, 'mobile');

  const ipad = parseSessionDevice(
    'Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
  );
  assert.equal(ipad.label, 'Safari on iPadOS');
  assert.equal(ipad.category, 'tablet');

  const firefoxLinux = parseSessionDevice(
    'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0',
  );
  assert.equal(firefoxLinux.label, 'Firefox on Linux');
  assert.equal(firefoxLinux.category, 'desktop');
});

test('parseSessionDevice preserves custom human-readable labels and handles empty input', () => {
  const fixture = parseSessionDevice('Other browser');
  assert.equal(fixture.label, 'Other browser');
  assert.equal(fixture.isParsedUserAgent, false);
  assert.equal(fixture.category, 'unknown');
  assert.equal(fixture.rawUserAgent, 'Other browser');

  const empty = parseSessionDevice(null);
  assert.equal(empty.label, 'Other device');
  assert.equal(empty.isParsedUserAgent, false);
  assert.equal(empty.rawUserAgent, null);
});
