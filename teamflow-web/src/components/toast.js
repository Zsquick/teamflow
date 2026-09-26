import { element } from '../utils/dom.js';

/** 显示短暂反馈消息。 */
export function showToast(message, type = 'info', duration = null) {
  const safeType = ['success', 'error', 'info'].includes(type) ? type : 'info';
  const displayDuration = duration == null
    ? (safeType === 'error' ? 10000 : 4500)
    : duration;
  let root = document.querySelector('#toast-root');
  if (!root) {
    root = element('div', { attributes: { id: 'toast-root' } });
    document.body.append(root);
  }

  const toast = element('div', {
    className: `toast toast--${safeType}`,
    attributes: { role: safeType === 'error' ? 'alert' : 'status' },
    children: [
      element('span', { className: 'toast__message', textContent: String(message) })
    ]
  });
  const closeButton = element('button', {
    className: 'toast__close',
    textContent: '×',
    attributes: { type: 'button', 'aria-label': '关闭提示' }
  });
  toast.append(closeButton);
  root.append(toast);

  let removed = false;
  let timer = null;
  const remove = () => {
    if (removed) return;
    removed = true;
    if (timer != null) window.clearTimeout(timer);
    toast.remove();
  };
  closeButton.addEventListener('click', remove, { once: true });
  if (Number.isFinite(displayDuration) && displayDuration > 0) {
    timer = window.setTimeout(remove, displayDuration);
  }
  return remove;
}
