import { element } from '../utils/dom.js';

/** 创建页面统一标题区。 */
export function createPageHeader({ eyebrow, title, description, actions = [] }) {
  const text = element('div', {
    className: 'page-header__text',
    children: [
      eyebrow ? element('p', { className: 'eyebrow', textContent: eyebrow }) : null,
      element('h1', { textContent: title }),
      description ? element('p', { className: 'page-header__description', textContent: description }) : null
    ]
  });
  return element('header', {
    className: 'page-header',
    children: [
      text,
      actions.length ? element('div', { className: 'page-header__actions', children: actions }) : null
    ]
  });
}

/** 创建包含状态和可选说明的紧凑页面标题。 */
export function createTitleBlock({ eyebrow, title, description, status, actions = [] }) {
  const titleRow = element('div', {
    className: 'page-title-row',
    children: [
      element('h1', { textContent: title, attributes: { title } }),
      status || null
    ]
  });
  return element('header', {
    className: 'page-header',
    children: [
      element('div', {
        className: 'page-header__text',
        children: [
          eyebrow ? element('p', { className: 'eyebrow', textContent: eyebrow }) : null,
          titleRow,
          description ? element('p', { className: 'page-header__description', textContent: description }) : null
        ]
      }),
      actions.length ? element('div', { className: 'page-header__actions', children: actions }) : null
    ]
  });
}

export function createStatusBadge(value, labels = {}) {
  const normalized = String(value || 'UNKNOWN');
  return element('span', {
    className: `status-badge status-badge--${normalized.toLowerCase().replaceAll('_', '-')}`,
    textContent: labels[normalized] ?? normalized
  });
}

/** 创建由应用路由接管的站内链接。 */
export function createInternalLink(href, label, navigate, className = '') {
  const link = element('a', {
    className,
    textContent: label,
    attributes: { href }
  });
  link.addEventListener('click', (event) => {
    if (
      event.defaultPrevented
      || event.button !== 0
      || event.metaKey
      || event.ctrlKey
      || event.shiftKey
      || event.altKey
    ) return;
    event.preventDefault();
    void navigate(href);
  });
  return link;
}

/** 创建带标题和可选提示的表单字段。 */
export function createField(label, control, hint = null) {
  const labelNode = element('label', {
    className: 'field',
    children: [element('span', { className: 'field__label', textContent: label }), control]
  });
  if (hint) labelNode.append(element('small', { className: 'field__hint', textContent: hint }));
  return labelNode;
}

/** 防止异步表单操作期间重复点击，并保留按钮原文。 */
export function setButtonBusy(button, busy, busyText = '处理中…') {
  if (!(button instanceof HTMLButtonElement)) {
    throw new TypeError('需要提供按钮元素');
  }
  if (busy) {
    if (!button.dataset.idleText) button.dataset.idleText = button.textContent ?? '';
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    button.textContent = busyText;
  } else {
    button.disabled = false;
    button.removeAttribute('aria-busy');
    button.textContent = button.dataset.idleText ?? button.textContent;
    delete button.dataset.idleText;
  }
}

/** 把空字符串转换成 JSON 中可清空可选字段的 null。 */
export function optionalText(value) {
  const text = value == null ? '' : String(value).trim();
  return text === '' ? null : text;
}
