import './styles.css';

const androidApkUrl = String(import.meta.env.VITE_ANDROID_APK_URL ?? '').trim();
const downloadLinks = document.querySelectorAll<HTMLAnchorElement>('[data-download]');
const downloadStatus = document.querySelector<HTMLElement>('#download-status');
const year = document.querySelector<HTMLElement>('#copyright-year');

if (year) {
  year.textContent = String(new Date().getFullYear());
}

for (const link of downloadLinks) {
  if (androidApkUrl) {
    link.href = androidApkUrl;
    link.rel = 'noreferrer';
    link.dataset.ready = 'true';
  } else {
    link.setAttribute('aria-disabled', 'true');
    link.addEventListener('click', (event) => {
      event.preventDefault();
      document.querySelector('#download')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      if (downloadStatus) {
        downloadStatus.textContent = '安卓版准备中，开放后提供官方 HTTPS 下载地址。';
      }
    });
  }
}
