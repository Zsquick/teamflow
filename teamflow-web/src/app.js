import { authStore } from './state/auth-store.js';
import { logout } from './api/auth-api.js';
import { countUnread } from './api/notification-api.js';
import { createNavigation } from './components/navigation.js';
import { createErrorState, createLoadingState } from './components/loading.js';
import { createIcon } from './components/icons.js';
import { openQuickJump } from './components/quick-jump.js';
import { showToast } from './components/toast.js';
import { subscribeUnreadCount } from './state/navigation-state.js';
import { clearElement, element } from './utils/dom.js';

const routes = [
  route(/^\/login\/?$/, false, () => import('./pages/login-page.js'), 'renderLoginPage'),
  route(/^\/teams\/?$/, true, () => import('./pages/team-page.js'), 'renderTeamPage'),
  route(/^\/teams\/(?<teamId>[^/]+)\/?$/, true, () => import('./pages/team-page.js'), 'renderTeamPage'),
  route(/^\/projects\/(?<projectId>[^/]+)\/board\/?$/, true, () => import('./pages/project-board-page.js'), 'renderProjectBoardPage'),
  route(/^\/projects\/(?<projectId>[^/]+)\/dashboard\/?$/, true, () => import('./pages/dashboard-page.js'), 'renderDashboardPage'),
  route(/^\/projects\/(?<projectId>[^/]+)\/import\/?$/, true, () => import('./pages/import-page.js'), 'renderImportPage'),
  route(/^\/tasks\/(?<taskId>[^/]+)\/files\/?$/, true, () => import('./pages/files-page.js'), 'renderFilesPage'),
  route(/^\/tasks\/(?<taskId>[^/]+)\/?$/, true, () => import('./pages/task-detail-page.js'), 'renderTaskDetailPage'),
  route(/^\/notifications\/?$/, true, () => import('./pages/notifications-page.js'), 'renderNotificationsPage'),
  route(/^\/invitations\/?$/, true, () => import('./pages/invitations-page.js'), 'renderInvitationsPage')
];

function route(pattern, requiresAuth, load, exportName) {
  return { pattern, requiresAuth, load, exportName };
}

/** 创建原生 JavaScript 单页应用。 */
export function createApp(rootElement) {
  if (!rootElement) {
    throw new Error('缺少 #app 根节点');
  }

  let started = false;
  let renderId = 0;
  let pageDestroy = null;
  let pageAbortController = null;
  let unsubscribeAuth = null;
  let unsubscribeUnread = null;
  let unreadCount = 0;
  let unreadRequestId = 0;
  let closeQuickJump = null;

  const sidebarHost = element('div', { className: 'app-shell__sidebar' });
  const sidebarBackdrop = element('button', {
    className: 'sidebar-backdrop',
    attributes: { type: 'button', 'aria-label': '关闭导航菜单' }
  });
  const menuButton = element('button', {
    className: 'topbar__menu',
    attributes: { type: 'button', 'aria-label': '打开导航菜单', 'aria-expanded': 'false' },
    children: [createIcon('menu')]
  });
  const topbarTitle = element('strong', {
    className: 'topbar__title',
    textContent: 'TeamFlow'
  });
  const quickJumpButton = element('button', {
    className: 'quick-jump-trigger',
    attributes: { type: 'button', 'aria-label': '打开快速跳转' },
    children: [
      createIcon('search'),
      element('span', { textContent: '快速跳转' }),
      element('kbd', { textContent: 'Ctrl K' })
    ]
  });
  const topbar = element('header', {
    className: 'topbar',
    children: [
      element('div', { className: 'topbar__leading', children: [menuButton, topbarTitle] }),
      quickJumpButton
    ]
  });
  const main = element('main', {
    className: 'app-main',
    attributes: { id: 'main-content', tabindex: '-1' }
  });
  const workspace = element('div', {
    className: 'app-shell__workspace',
    children: [topbar, main]
  });
  const shell = element('div', {
    className: 'app-shell',
    children: [sidebarHost, sidebarBackdrop, workspace]
  });

  const renderNavigation = () => {
    const authenticated = authStore.isAuthenticated;
    shell.classList.toggle('app-shell--guest', !authenticated);
    topbar.hidden = !authenticated;
    if (!authenticated) {
      sidebarHost.replaceChildren();
      closeSidebar();
      return;
    }
    sidebarHost.replaceChildren(createNavigation(
      authStore.currentUser,
      (path) => {
        closeSidebar();
        void navigate(path);
      },
      handleLogout,
      { unreadCount }
    ));
  };

  const setTopbarTitle = (value) => {
    const title = String(value || 'TeamFlow').trim();
    topbarTitle.textContent = title;
    document.title = title === 'TeamFlow' ? title : `${title} - TeamFlow`;
  };

  function setSidebarOpen(open) {
    const next = Boolean(open && authStore.isAuthenticated);
    shell.classList.toggle('app-shell--nav-open', next);
    menuButton.setAttribute('aria-expanded', String(next));
    document.body.classList.toggle('nav-drawer-open', next);
  }

  function closeSidebar() {
    setSidebarOpen(false);
  }

  const openQuickJumpPanel = () => {
    if (!authStore.isAuthenticated || closeQuickJump) return;
    closeQuickJump = openQuickJump({
      navigate,
      onClose: () => { closeQuickJump = null; }
    });
  };

  const refreshUnreadCount = async () => {
    const requestId = ++unreadRequestId;
    if (!authStore.isAuthenticated) {
      unreadCount = 0;
      renderNavigation();
      return;
    }
    try {
      const nextCount = await countUnread();
      if (requestId !== unreadRequestId || !authStore.isAuthenticated) return;
      unreadCount = Math.max(0, Number(nextCount) || 0);
      renderNavigation();
    } catch (error) {
      if (error?.name !== 'AbortError') console.warn('未读通知数加载失败', error);
    }
  };

  const cleanupPage = async () => {
    pageAbortController?.abort();
    pageAbortController = null;
    const destroy = pageDestroy;
    pageDestroy = null;
    if (typeof destroy === 'function') {
      try {
        await destroy();
      } catch (error) {
        console.error('页面清理失败', error);
      }
    }
  };

  async function renderCurrentUrl() {
    const currentRenderId = ++renderId;
    await cleanupPage();
    if (currentRenderId !== renderId) return;

    const pathname = normalizePath(window.location.pathname);
    if (pathname === '/') {
      await redirect(authStore.isAuthenticated ? '/teams' : '/login', true);
      return;
    }

    const match = matchRoute(pathname);
    if (!match) {
      renderNavigation();
      renderNotFound(main, (path) => void navigate(path));
      return;
    }
    if (match.route.requiresAuth && !authStore.isAuthenticated) {
      const next = `${pathname}${window.location.search}`;
      await redirect(`/login?next=${encodeURIComponent(next)}`, true);
      return;
    }
    if (!match.route.requiresAuth && authStore.isAuthenticated) {
      await redirect('/teams', true);
      return;
    }

    renderNavigation();
    clearElement(main).append(createLoadingState('正在打开页面…'));
    pageAbortController = new AbortController();
    const signal = pageAbortController.signal;

    try {
      const module = await match.route.load();
      if (currentRenderId !== renderId || signal.aborted) return;
      const renderPage = module[match.route.exportName];
      if (typeof renderPage !== 'function') {
        throw new Error('页面模块缺少渲染函数');
      }
      const destroy = await renderPage({
        root: main,
        params: match.params,
        searchParams: new URLSearchParams(window.location.search),
        authStore,
        navigate,
        signal
      });
      if (currentRenderId !== renderId || signal.aborted) {
        if (typeof destroy === 'function') await destroy();
        return;
      }
      pageDestroy = typeof destroy === 'function' ? destroy : null;
      setTopbarTitle(main.querySelector('h1')?.textContent ?? 'TeamFlow');
      main.focus({ preventScroll: true });
    } catch (error) {
      if (currentRenderId !== renderId || signal.aborted || error?.name === 'AbortError') return;
      clearElement(main).append(createErrorState(error, () => void renderCurrentUrl()));
    }
  }

  async function navigate(path, { replace = false } = {}) {
    closeQuickJump?.();
    closeQuickJump = null;
    closeSidebar();
    const target = new URL(path, window.location.origin);
    if (target.origin !== window.location.origin) {
      throw new TypeError('只允许跳转到站内页面');
    }
    const next = `${normalizePath(target.pathname)}${target.search}${target.hash}`;
    const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    if (replace) {
      history.replaceState(null, '', next);
    } else if (next !== current) {
      history.pushState(null, '', next);
    }
    await renderCurrentUrl();
  }

  async function redirect(path, replace = true) {
    await navigate(path, { replace });
  }

  async function handleLogout() {
    const refreshToken = authStore.refreshToken;
    try {
      if (refreshToken) await logout(refreshToken);
    } catch (error) {
      showToast('服务器会话未能注销，本地登录状态已清除', 'info');
    } finally {
      authStore.clear();
      await navigate('/login', { replace: true });
    }
  }

  const onPopState = () => void renderCurrentUrl();
  const onGlobalKeyDown = (event) => {
    if (event.key === 'Escape' && shell.classList.contains('app-shell--nav-open')) {
      event.preventDefault();
      closeSidebar();
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLocaleLowerCase('en-US') === 'k') {
      if (!authStore.isAuthenticated) return;
      event.preventDefault();
      openQuickJumpPanel();
    }
  };

  menuButton.addEventListener('click', () => {
    setSidebarOpen(!shell.classList.contains('app-shell--nav-open'));
  });
  sidebarBackdrop.addEventListener('click', closeSidebar);
  quickJumpButton.addEventListener('click', openQuickJumpPanel);

  return {
    async start() {
      if (started) return;
      started = true;
      clearElement(rootElement).append(shell);
      window.addEventListener('popstate', onPopState);
      document.addEventListener('keydown', onGlobalKeyDown);
      let restoreError = null;
      try {
        await authStore.restore();
      } catch (error) {
        restoreError = error;
        console.warn('登录状态暂时无法恢复，已保留刷新令牌', error);
      }
      let wasAuthenticated = authStore.isAuthenticated;
      unsubscribeAuth = authStore.subscribe(({ authenticated }) => {
        renderNavigation();
        void refreshUnreadCount();
        if (wasAuthenticated && !authenticated && matchRoute(window.location.pathname)?.route.requiresAuth) {
          void navigate('/login', { replace: true });
        }
        wasAuthenticated = authenticated;
      });
      unsubscribeUnread = subscribeUnreadCount((count) => {
        unreadCount = count;
        renderNavigation();
      });
      renderNavigation();
      void refreshUnreadCount();
      await renderCurrentUrl();
      if (restoreError) {
        showToast(
          restoreError.message || '服务暂时不可用，已保留登录信息，请稍后重试',
          'error'
        );
      }
    },
    navigate,
    async destroy() {
      if (!started) return;
      started = false;
      renderId += 1;
      window.removeEventListener('popstate', onPopState);
      document.removeEventListener('keydown', onGlobalKeyDown);
      unsubscribeAuth?.();
      unsubscribeAuth = null;
      unsubscribeUnread?.();
      unsubscribeUnread = null;
      closeQuickJump?.();
      closeQuickJump = null;
      closeSidebar();
      await cleanupPage();
      clearElement(rootElement);
    },
    authStore
  };
}

function normalizePath(pathname) {
  const normalized = pathname.replace(/\/{2,}/g, '/');
  return normalized.length > 1 ? normalized.replace(/\/$/, '') : normalized;
}

function matchRoute(pathname) {
  for (const item of routes) {
    const result = item.pattern.exec(pathname);
    if (result) {
      const params = {};
      for (const [key, value] of Object.entries(result.groups ?? {})) {
        try {
          params[key] = decodeURIComponent(value);
        } catch {
          params[key] = value;
        }
      }
      return { route: item, params };
    }
  }
  return null;
}

function renderNotFound(root, onNavigate) {
  const link = element('a', { textContent: '返回团队页', attributes: { href: '/teams' } });
  link.addEventListener('click', (event) => {
    event.preventDefault();
    onNavigate('/teams');
  });
  clearElement(root).append(element('section', {
    className: 'not-found',
    children: [
      element('p', { className: 'eyebrow', textContent: '404' }),
      element('h1', { textContent: '没有找到这个页面' }),
      element('p', { textContent: '链接可能已失效，或地址输入有误。' }),
      link
    ]
  }));
}
