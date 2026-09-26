import { countUnread, listNotifications, markNotificationRead } from '../api/notification-api.js';
import { createBreadcrumb } from '../components/context-navigation.js';
import { createEmptyState, createLoadingState } from '../components/loading.js';
import { createTitleBlock, setButtonBusy } from '../components/page-elements.js';
import { createPagination } from '../components/pagination.js';
import { showToast } from '../components/toast.js';
import { createNotificationStream } from '../sse/notification-stream.js';
import { publishUnreadCount } from '../state/navigation-state.js';
import { clearElement, element } from '../utils/dom.js';
import { formatDateTime } from '../utils/format.js';

const typeLabels = {
  TASK_ASSIGNED: '任务指派',
  TASK_DUE_SOON: '即将到期',
  COMMENT_ADDED: '新增评论',
  IMPORT_COMPLETED: '导入完成',
  IMPORT_FAILED: '导入失败',
  TEAM_INVITATION_RECEIVED: '团队邀请',
  TEAM_INVITATION_ACCEPTED: '邀请已接受',
  TEAM_INVITATION_REJECTED: '邀请已拒绝',
  TEAM_INVITATION_REVOKED: '邀请已撤销'
};

/** 通知中心页。 */
export async function renderNotificationsPage({ root, navigate, signal }) {
  let [pageResult, unreadCount] = await Promise.all([
    listNotifications(1, 20, { signal }),
    countUnread({ signal })
  ]);
  const seenIds = new Set(pageResult.items.map((item) => item.id));
  const pendingReadIds = new Set();
  let changingPage = false;
  let stateRevision = 0;

  const stream = createNotificationStream({
    onEvent: ({ type, data }) => {
      if (type !== 'notification' || !data?.id || seenIds.has(data.id)) return;
      seenIds.add(data.id);
      if (!data.read) unreadCount += 1;
      pageResult = { ...pageResult, total: pageResult.total + 1 };
      if (pageResult.page === 1) {
        pageResult = {
          ...pageResult,
          items: [data, ...pageResult.items].slice(0, pageResult.size)
        };
        render();
      } else {
        showToast('收到一条新通知，返回第一页即可查看', 'info');
        render();
      }
    },
    onError: (error) => {
      if (!signal.aborted) console.warn('通知流暂时不可用，将自动重连', error);
    },
    onReconnect: () => reconcileNotifications()
  });

  const render = () => {
    const list = element('div', { className: 'notification-list' });
    if (pageResult.items.length === 0) list.append(createEmptyState('暂无通知，新的任务、团队邀请、评论或导入结果会出现在这里。'));
    for (const notification of pageResult.items) {
      const actions = [];
      if (!notification.read) {
        const read = element('button', {
          className: 'button button--secondary button--small',
          textContent: '标记已读',
          attributes: { type: 'button' }
        });
        read.addEventListener('click', () => void markRead(notification, read));
        actions.push(read);
      }
      if (notification.type?.startsWith('TEAM_INVITATION_')) {
        const target = notification.type === 'TEAM_INVITATION_RECEIVED'
          ? '/invitations'
          : '/teams';
        const open = element('button', {
          className: 'button button--secondary button--small',
          textContent: notification.type === 'TEAM_INVITATION_RECEIVED' ? '查看邀请' : '查看团队',
          attributes: { type: 'button' }
        });
        open.addEventListener('click', () => void navigate(target));
        actions.push(open);
      }
      list.append(element('article', {
        className: `notification-item${notification.read ? '' : ' notification-item--unread'}`,
        children: [
          element('div', {
            className: 'notification-item__body',
            children: [
              element('div', {
                className: 'notification-item__heading',
                children: [
                  element('span', { className: 'badge', textContent: typeLabels[notification.type] ?? notification.type }),
                  !notification.read ? element('span', { className: 'unread-dot', textContent: '未读' }) : null
                ]
              }),
              element('h2', { textContent: notification.title }),
              element('p', { textContent: notification.content }),
              element('time', { className: 'muted', textContent: formatDateTime(notification.createdAt) })
            ]
          }),
          actions.length ? element('div', { className: 'button-row', children: actions }) : null
        ]
      }));
    }

    const unreadOnPage = pageResult.items.filter((item) => !item.read);
    const markPageRead = element('button', {
      className: 'button button--primary',
      textContent: '将本页全部标记为已读',
      attributes: { type: 'button', disabled: unreadOnPage.length === 0 }
    });
    markPageRead.disabled = unreadOnPage.length === 0;
    markPageRead.addEventListener('click', () => void markCurrentPageRead(markPageRead));

    clearElement(root).append(element('section', {
      className: 'page notifications-page',
      children: [
        createBreadcrumb([{ label: '通知' }], navigate),
        createTitleBlock({
          eyebrow: '全局消息',
          title: '通知',
          description: '历史消息分页加载，新通知会实时到达并标记为未读。',
          status: element('span', { className: 'counter-pill', textContent: `${unreadCount} 条未读` }),
          actions: [markPageRead]
        }),
        changingPage
          ? createLoadingState('正在加载通知…')
          : element('div', { className: 'notification-scroll', children: [list] }),
        changingPage ? null : createPagination(pageResult, (page) => void goToPage(page))
      ]
    }));
    publishUnreadCount(unreadCount);
  };

  const goToPage = async (page) => {
    if (changingPage || page < 1) return;
    changingPage = true;
    render();
    try {
      pageResult = await listNotifications(page, pageResult.size, { signal });
      pageResult.items.forEach((item) => seenIds.add(item.id));
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '加载通知失败', 'error');
    } finally {
      changingPage = false;
      if (!signal.aborted) render();
    }
  };

  const reconcileNotifications = async () => {
    if (signal.aborted) return;
    const revision = stateRevision;
    const [latestFirstPage, latestUnreadCount] = await Promise.all([
      listNotifications(1, pageResult.size, { signal }),
      countUnread({ signal })
    ]);
    if (signal.aborted || revision !== stateRevision) return;
    latestFirstPage.items.forEach((item) => seenIds.add(item.id));
    const reconciledFirstPage = {
      ...latestFirstPage,
      items: latestFirstPage.items.map((item) => pendingReadIds.has(item.id)
        ? { ...item, read: true }
        : item)
    };
    if (pendingReadIds.size === 0) unreadCount = latestUnreadCount;
    pageResult = pageResult.page === 1
      ? reconciledFirstPage
      : { ...pageResult, total: latestFirstPage.total };
    if (!signal.aborted) render();
  };

  const markRead = async (notification, button) => {
    if (notification.read || pendingReadIds.has(notification.id)) return;
    pendingReadIds.add(notification.id);
    stateRevision += 1;
    setButtonBusy(button, true, '提交中…');
    pageResult = {
      ...pageResult,
      items: pageResult.items.map((item) => item.id === notification.id ? { ...item, read: true } : item)
    };
    unreadCount = Math.max(0, unreadCount - 1);
    render();
    try {
      await markNotificationRead(notification.id, { signal });
      pendingReadIds.delete(notification.id);
      stateRevision += 1;
    } catch (error) {
      pendingReadIds.delete(notification.id);
      stateRevision += 1;
      pageResult = {
        ...pageResult,
        items: pageResult.items.map((item) => item.id === notification.id ? { ...item, read: false } : item)
      };
      unreadCount += 1;
      if (error?.name !== 'AbortError') showToast(error.message || '标记已读失败', 'error');
      render();
    }
  };

  const markCurrentPageRead = async (button) => {
    const unreadItems = pageResult.items.filter((item) => !item.read && !pendingReadIds.has(item.id));
    if (unreadItems.length === 0) return;
    setButtonBusy(button, true, '提交中…');
    unreadItems.forEach((item) => pendingReadIds.add(item.id));
    stateRevision += 1;
    const results = await Promise.allSettled(
      unreadItems.map((item) => markNotificationRead(item.id, { signal }))
    );
    const succeededIds = new Set();
    let failedCount = 0;
    results.forEach((result, index) => {
      const id = unreadItems[index].id;
      pendingReadIds.delete(id);
      if (result.status === 'fulfilled') succeededIds.add(id);
      else if (result.reason?.name !== 'AbortError') failedCount += 1;
    });
    stateRevision += 1;
    pageResult = {
      ...pageResult,
      items: pageResult.items.map((item) => succeededIds.has(item.id) ? { ...item, read: true } : item)
    };
    unreadCount = Math.max(0, unreadCount - succeededIds.size);
    if (failedCount > 0) showToast(`${failedCount} 条通知未能标记为已读`, 'error');
    else showToast('本页未读通知已全部处理', 'success');
    if (!signal.aborted) render();
  };

  render();
  void stream.connect();
  return () => stream.close();
}
