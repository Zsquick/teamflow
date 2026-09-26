import { element } from '../utils/dom.js';

let modalSequence = 0;

/** 打开可访问的模态框，并返回关闭函数。 */
export function openModal({ title, content, onClose }) {
  if (!(content instanceof Node)) {
    throw new TypeError('模态框内容必须是 DOM 节点');
  }
  const previousFocus = document.activeElement;
  const titleId = `teamflow-modal-title-${++modalSequence}`;
  const closeButton = element('button', {
    className: 'modal__close',
    textContent: '×',
    attributes: { type: 'button', 'aria-label': '关闭对话框' }
  });
  const dialog = element('section', {
    className: 'modal',
    attributes: {
      role: 'dialog',
      'aria-modal': 'true',
      'aria-labelledby': titleId,
      tabindex: '-1'
    },
    children: [
      element('header', {
        className: 'modal__header',
        children: [
          element('h2', { textContent: title, attributes: { id: titleId } }),
          closeButton
        ]
      }),
      element('div', { className: 'modal__content', children: [content] })
    ]
  });
  const overlay = element('div', {
    className: 'modal-overlay',
    children: [dialog]
  });
  let root = document.querySelector('#modal-root');
  if (!root) {
    root = element('div', { attributes: { id: 'modal-root' } });
    document.body.append(root);
  }
  root.append(overlay);

  let closed = false;
  const focusableSelector = [
    'a[href]', 'button:not([disabled])', 'input:not([disabled])',
    'select:not([disabled])', 'textarea:not([disabled])', '[tabindex]:not([tabindex="-1"])'
  ].join(',');

  const close = () => {
    if (closed) return;
    closed = true;
    document.removeEventListener('keydown', onKeyDown);
    overlay.removeEventListener('mousedown', onOverlayMouseDown);
    closeButton.removeEventListener('click', close);
    overlay.remove();
    if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
      previousFocus.focus();
    }
    onClose?.();
  };
  const onKeyDown = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = [...dialog.querySelectorAll(focusableSelector)];
    if (focusable.length === 0) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable.at(-1);
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };
  const onOverlayMouseDown = (event) => {
    if (event.target === overlay) close();
  };

  document.addEventListener('keydown', onKeyDown);
  overlay.addEventListener('mousedown', onOverlayMouseDown);
  closeButton.addEventListener('click', close);
  queueMicrotask(() => {
    const initialFocus = dialog.querySelector('[autofocus]')
      ?? dialog.querySelector(focusableSelector)
      ?? dialog;
    initialFocus.focus();
  });
  return close;
}
