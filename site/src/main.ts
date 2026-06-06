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
        downloadStatus.textContent = '安卓下载地址待开放。备案、公安备案和真机回归完成后，这里会放官方 HTTPS 下载链接。';
      }
    });
  }
}
