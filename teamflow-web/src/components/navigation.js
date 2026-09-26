import { element } from '../utils/dom.js';
import { createIcon } from './icons.js';

/** 创建主导航。返回 DOM 元素，不拼接不可信 HTML。 */
export function createNavigation(currentUser, onNavigate, onLogout, { unreadCount = 0 } = {}) {
  const nav = element('aside', { className: 'sidebar' });
  const brand = element('a', {
    className: 'brand',
    attributes: { href: currentUser ? '/teams' : '/login', 'aria-label': 'TeamFlow 首页' },
    children: [
      element('span', { className: 'brand__mark', textContent: 'TF', attributes: { 'aria-hidden': 'true' } }),
      element('span', { className: 'brand__name', textContent: 'TeamFlow' })
    ]
  });
  bindNavigation(brand, onNavigate);
  nav.append(brand);

  const links = currentUser
    ? [
        { href: '/teams', label: '团队', icon: 'teams' },
        { href: '/invitations', label: '团队邀请', icon: 'invitations' },
        { href: '/notifications', label: '通知', icon: 'notifications', count: unreadCount }
      ]
    : [{ href: '/login', label: '登录 / 注册', icon: 'teams' }];
  const list = element('nav', {
    className: 'sidebar__links',
    attributes: { 'aria-label': '主导航' }
  });
  if (currentUser) list.append(element('p', { className: 'sidebar__section-title', textContent: '工作区' }));
  for (const item of links) {
    const link = element('a', {
      className: 'sidebar__link',
      attributes: { href: item.href },
      children: [
        createIcon(item.icon, 'sidebar__icon'),
        element('span', { className: 'sidebar__label', textContent: item.label }),
        item.count > 0 ? element('span', {
          className: 'sidebar__count',
          textContent: item.count > 99 ? '99+' : String(item.count),
          attributes: { 'aria-label': `${item.count} 条未读通知` }
        }) : null
      ]
    });
    const currentPath = window.location.pathname;
    const workspaceActive = item.href === '/teams'
      && /^\/(teams|projects|tasks)(\/|$)/.test(currentPath);
    if (workspaceActive || currentPath === item.href || item.href !== '/login' && currentPath.startsWith(`${item.href}/`)) {
      link.setAttribute('aria-current', 'page');
    }
    bindNavigation(link, onNavigate);
    list.append(link);
  }
  nav.append(list);

  if (currentUser) {
    const displayName = currentUser.displayName || currentUser.username;
    const userBlock = element('div', {
      className: 'sidebar__user',
      children: [
        element('span', {
          className: 'sidebar__avatar',
          textContent: initials(displayName),
          attributes: { 'aria-hidden': 'true' }
        }),
        element('span', {
          className: 'sidebar__user-copy',
          children: [
            element('strong', { textContent: displayName, attributes: { title: displayName } }),
            element('small', { textContent: currentUser.email || currentUser.username })
          ]
        })
      ]
    });
    const logoutButton = element('button', {
      className: 'sidebar__logout',
      attributes: { type: 'button', 'aria-label': '退出登录' },
      children: [createIcon('logout'), element('span', { textContent: '退出登录' })]
    });
    logoutButton.addEventListener('click', () => void onLogout?.());
    userBlock.append(logoutButton);
    nav.append(userBlock);
  }
  return nav;
}

function initials(value) {
  const text = String(value || 'U').trim();
  return [...text].slice(0, 2).join('').toLocaleUpperCase('zh-CN');
}

function bindNavigation(link, onNavigate) {
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
    onNavigate?.(link.getAttribute('href'));
  });
}
