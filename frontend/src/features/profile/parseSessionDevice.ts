export type DeviceCategory = 'desktop' | 'mobile' | 'tablet' | 'unknown';

export interface ParsedSessionDevice {
  readonly label: string;
  readonly browser: string | null;
  readonly os: string | null;
  readonly category: DeviceCategory;
  readonly isParsedUserAgent: boolean;
  readonly rawUserAgent: string | null;
}

const TECHNICAL_UA_PATTERN =
  /\b(?:Mozilla|AppleWebKit|Gecko|Chrome|Chromium|HeadlessChrome|CriOS|Firefox|FxiOS|Safari|Edg|EdgA|EdgiOS|OPR|Opera|Vivaldi|SamsungBrowser|PostmanRuntime|curl|undici|axios)\//i;

function detectBrowser(ua: string): string | null {
  if (/(?:EdgA|EdgiOS|Edg)\//i.test(ua)) return 'Edge';
  if (/(?:OPR|Opera)\//i.test(ua)) return 'Opera';
  if (/SamsungBrowser\//i.test(ua)) return 'Samsung Internet';
  if (/Vivaldi\//i.test(ua)) return 'Vivaldi';
  if (/Brave\//i.test(ua)) return 'Brave';
  if (/(?:Firefox|FxiOS)\//i.test(ua)) return 'Firefox';
  if (/(?:Chrome|Chromium|HeadlessChrome|CriOS)\//i.test(ua)) return 'Chrome';
  if (/Safari\//i.test(ua)) return 'Safari';
  if (/curl\//i.test(ua)) return 'curl';
  if (/PostmanRuntime\//i.test(ua)) return 'Postman';
  if (/(?:node-fetch|undici|axios)\//i.test(ua)) return 'API Client';
  return null;
}

function detectPlatform(ua: string): { os: string | null; category: DeviceCategory } {
  if (/iPad/i.test(ua)) return { os: 'iPadOS', category: 'tablet' };
  if (/iPhone|iPod/i.test(ua)) return { os: 'iOS', category: 'mobile' };
  if (/Android/i.test(ua)) {
    return { os: 'Android', category: /Mobile/i.test(ua) ? 'mobile' : 'tablet' };
  }
  if (/CrOS/i.test(ua)) return { os: 'ChromeOS', category: 'desktop' };
  if (/Windows NT|Win64|WOW64|Windows/i.test(ua)) return { os: 'Windows', category: 'desktop' };
  if (/Macintosh|Mac OS X/i.test(ua)) return { os: 'macOS', category: 'desktop' };
  if (/Linux|X11|Ubuntu|Fedora/i.test(ua)) return { os: 'Linux', category: 'desktop' };
  return { os: null, category: 'unknown' };
}

export function parseSessionDevice(userAgent: string | null | undefined): ParsedSessionDevice {
  const raw = (userAgent ?? '').trim();
  if (!raw) {
    return {
      label: 'Other device',
      browser: null,
      os: null,
      category: 'unknown',
      isParsedUserAgent: false,
      rawUserAgent: null,
    };
  }

  if (!TECHNICAL_UA_PATTERN.test(raw)) {
    return {
      label: raw,
      browser: null,
      os: null,
      category: 'unknown',
      isParsedUserAgent: false,
      rawUserAgent: raw,
    };
  }

  const browser = detectBrowser(raw);
  const { os, category } = detectPlatform(raw);

  if (browser && os) {
    return {
      label: `${browser} on ${os}`,
      browser,
      os,
      category,
      isParsedUserAgent: true,
      rawUserAgent: raw,
    };
  }

  if (browser) {
    return {
      label: browser,
      browser,
      os: null,
      category: category === 'unknown' ? 'desktop' : category,
      isParsedUserAgent: true,
      rawUserAgent: raw,
    };
  }

  if (os) {
    return {
      label: os,
      browser: null,
      os,
      category,
      isParsedUserAgent: true,
      rawUserAgent: raw,
    };
  }

  return {
    label: raw,
    browser: null,
    os: null,
    category: 'unknown',
    isParsedUserAgent: false,
    rawUserAgent: raw,
  };
}
