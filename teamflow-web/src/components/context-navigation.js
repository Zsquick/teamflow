import { element } from '../utils/dom.js';
import { createInternalLink } from './page-elements.js';

/** 创建可返回上级的面包屑，最后一级只表示当前位置。 */
export function createBreadcrumb(items, navigate) {
  const list = element('ol', { className: 'breadcrumb__list' });
  items.filter(Boolean).forEach((item, index, visibleItems) => {
    const current = index === visibleItems.length - 1;
    const label = item.label || '未命名';
    const content = current || !item.href
      ? element('span', {
          className: 'breadcrumb__current',
          textContent: label,
          attributes: { 'aria-current': current ? 'page' : null, title: label }
        })
      : createInternalLink(item.href, label, navigate, 'breadcrumb__link');
    if (content instanceof HTMLElement) content.title = label;
    list.append(element('li', { className: 'breadcrumb__item', children: [content] }));
  });
  return element('nav', {
    className: 'breadcrumb',
    attributes: { 'aria-label': '面包屑' },
    children: [list]
  });
}

/** 创建团队、项目或任务内的稳定上下文页签。 */
export function createContextTabs(items, activeKey, navigate, label = '上下文导航') {
  const list = element('div', { className: 'context-tabs__list' });
  for (const item of items) {
    const link = createInternalLink(item.href, item.label, navigate, 'context-tabs__link');
    if (item.key === activeKey) link.setAttribute('aria-current', 'page');
    if (item.count != null) {
      link.append(element('span', { className: 'context-tabs__count', textContent: String(item.count) }));
    }
    list.append(link);
  }
  return element('nav', {
    className: 'context-tabs',
    attributes: { 'aria-label': label },
    children: [list]
  });
}

export function teamTabs(teamId, activeKey) {
  const id = encodeURIComponent(teamId);
  return [
    { key: 'overview', label: '概览', href: `/teams/${id}` },
    { key: 'projects', label: '项目', href: `/teams/${id}?tab=projects` },
    { key: 'members', label: '成员', href: `/teams/${id}?tab=members` }
  ].map((item) => ({ ...item, active: item.key === activeKey }));
}

export function projectTabs(projectId) {
  const id = encodeURIComponent(projectId);
  return [
    { key: 'board', label: '任务看板', href: `/projects/${id}/board` },
    { key: 'dashboard', label: '数据仪表盘', href: `/projects/${id}/dashboard` },
    { key: 'import', label: 'CSV 导入', href: `/projects/${id}/import` }
  ];
}

export function taskTabs(taskId) {
  const id = encodeURIComponent(taskId);
  return [
    { key: 'details', label: '任务详情', href: `/tasks/${id}` },
    { key: 'comments', label: '评论', href: `/tasks/${id}?tab=comments` },
    { key: 'attachments', label: '附件', href: `/tasks/${id}/files` }
  ];
}
