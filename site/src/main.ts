import './styles.css';

const androidApkUrl = String(import.meta.env.VITE_ANDROID_APK_URL ?? '').trim();
const downloadLinks = document.querySelectorAll<HTMLAnchorElement>('[data-download]');

const isValidApkUrl = (value: string): boolean => {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.pathname.toLowerCase().endsWith('.apk');
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
