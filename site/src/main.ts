import './styles.css';

const androidApkUrl = String(import.meta.env.VITE_ANDROID_APK_URL ?? '').trim();
const downloadLinks = document.querySelectorAll<HTMLAnchorElement>('[data-download]');

const decodeUrlText = (value: string): string => {
  let current = value;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const decoded = decodeURIComponent(current);
      if (decoded === current) {
        break;
      }
      current = decoded;
    } catch {
      break;
    }
  }
  return current;
};

const isValidApkUrl = (value: string): boolean => {
  try {
    const url = new URL(value);
    const signedQueryKeys = new Set([
      'expires',
      'signature',
      'ossaccesskeyid',
      'security-token',
      'x-oss-expires',
      'x-oss-signature',
      'x-oss-credential',
      'x-oss-security-token',
    ]);
    for (const key of url.searchParams.keys()) {
      if (signedQueryKeys.has(key.toLowerCase())) {
        return false;
      }
    }
    if (url.username || url.password || url.search || url.hash) {
      return false;
    }
    if (url.port && url.port !== '443') {
      return false;
    }
    const rawPath = url.pathname.toLowerCase();
    const decodedPath = decodeUrlText(url.pathname).toLowerCase();
    const normalized = `${value}\n${decodeUrlText(value)}\n${rawPath}\n${decodedPath}`.toLowerCase();
    if (
      rawPath.includes('..') ||
      decodedPath.includes('..') ||
      normalized.includes('test-apks') ||
      normalized.includes('debug') ||
      normalized.includes('internal') ||
      normalized.includes('staging')
    ) {
      return false;
    }
    return url.protocol === 'https:' &&
      url.hostname.toLowerCase() === 'download.nongjiqiancha.cn' &&
      rawPath.startsWith('/android/releases/') &&
      decodedPath.startsWith('/android/releases/') &&
      rawPath.endsWith('.apk') &&
      decodedPath.endsWith('.apk');
  } catch {
    return false;
  }
};

for (const link of downloadLinks) {
  if (isValidApkUrl(androidApkUrl)) {
    link.href = androidApkUrl;
    link.rel = 'noreferrer';
    link.dataset.ready = 'true';
  } else {
    link.removeAttribute('href');
    link.setAttribute('aria-disabled', 'true');
    link.tabIndex = -1;
    link.addEventListener('click', (event) => {
      event.preventDefault();
    });
  }
}
