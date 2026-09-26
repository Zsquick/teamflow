import { element } from '../utils/dom.js';

/** 创建加载、空数据和错误状态组件。 */
export function createLoadingState(text = '加载中…') {
  return element('div', {
    className: 'state state--loading',
    attributes: { role: 'status', 'aria-busy': 'true' },
    children: [
      element('span', { className: 'spinner', attributes: { 'aria-hidden': 'true' } }),
      element('span', { textContent: text })
    ]
  });
}

export function createEmptyState(text = '暂无数据') {
  return element('div', {
    className: 'state state--empty',
    attributes: { role: 'status' },
    textContent: text
  });
}

export function createErrorState(error, retry) {
  const message = error instanceof Error && error.message
    ? error.message
    : '加载失败，请稍后重试';
  const children = [
    element('strong', { textContent: '操作未完成' }),
    element('p', { textContent: message })
  ];
  if (typeof retry === 'function') {
    const button = element('button', {
      className: 'button button--secondary',
      textContent: '重试',
      attributes: { type: 'button' }
    });
    button.addEventListener('click', retry);
    children.push(button);
  }
  return element('div', {
    className: 'state state--error',
    attributes: { role: 'alert' },
    children
  });
}
