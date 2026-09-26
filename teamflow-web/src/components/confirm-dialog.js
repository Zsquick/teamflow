import { element } from '../utils/dom.js';
import { openModal } from './modal.js';

/** 打开可访问的二次确认框，用 Promise 返回用户选择。 */
export function confirmAction({
  title = '确认操作',
  message,
  confirmLabel = '确认',
  danger = false
}) {
  return new Promise((resolve) => {
    let settled = false;
    let closeModal = null;
    const settle = (value) => {
      if (settled) return;
      settled = true;
      resolve(value);
    };
    const cancel = element('button', {
      className: 'button button--ghost',
      textContent: '取消',
      attributes: { type: 'button' }
    });
    const confirm = element('button', {
      className: danger ? 'button button--danger' : 'button button--primary',
      textContent: confirmLabel,
      attributes: { type: 'button', autofocus: true }
    });
    cancel.addEventListener('click', () => {
      settle(false);
      closeModal?.();
    });
    confirm.addEventListener('click', () => {
      settle(true);
      closeModal?.();
    });
    const content = element('div', {
      className: 'confirm-dialog',
      children: [
        element('p', { textContent: message || '该操作无法自动撤销。' }),
        element('div', { className: 'button-row button-row--end', children: [cancel, confirm] })
      ]
    });
    closeModal = openModal({
      title,
      content,
      onClose: () => settle(false)
    });
  });
}
