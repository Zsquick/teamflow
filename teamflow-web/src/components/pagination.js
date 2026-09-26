import { element } from '../utils/dom.js';

/** 根据 PageResult 创建上一页/下一页控件。 */
export function createPagination(pageResult, onPage) {
  const page = Number(pageResult?.page ?? 1);
  const size = Number(pageResult?.size ?? 20);
  const total = Number(pageResult?.total ?? 0);
  const totalPages = total === 0 ? 0 : Math.ceil(total / size);
  const previous = pageButton('上一页', page <= 1, () => onPage(page - 1));
  const next = pageButton('下一页', page >= totalPages, () => onPage(page + 1));
  return element('nav', {
    className: 'pagination',
    attributes: { 'aria-label': '分页' },
    children: [
      previous,
      element('span', {
        className: 'pagination__status',
        textContent: totalPages === 0 ? '暂无记录' : `第 ${page} / ${totalPages} 页，共 ${total} 条`
      }),
      next
    ]
  });
}

function pageButton(label, disabled, action) {
  const button = element('button', {
    className: 'button button--secondary',
    textContent: label,
    attributes: { type: 'button', disabled }
  });
  button.disabled = disabled;
  button.addEventListener('click', () => action());
  return button;
}
