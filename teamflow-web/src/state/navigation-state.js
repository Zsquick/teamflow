const UNREAD_COUNT_EVENT = 'teamflow:unread-count';

/** 让通知页与全局导航共享真实未读数，不复制通知数据。 */
export function publishUnreadCount(count) {
  const normalized = Math.max(0, Number(count) || 0);
  window.dispatchEvent(new CustomEvent(UNREAD_COUNT_EVENT, {
    detail: { count: normalized }
  }));
}

export function subscribeUnreadCount(listener) {
  const handler = (event) => listener(event.detail?.count ?? 0);
  window.addEventListener(UNREAD_COUNT_EVENT, handler);
  return () => window.removeEventListener(UNREAD_COUNT_EVENT, handler);
}
