import { refresh } from '../api/auth-api.js';
import { getCurrentUser } from '../api/user-api.js';
import { isInvalidSessionError } from '../auth/session-errors.js';

const REFRESH_TOKEN_KEY = 'teamflow.refresh-token';

/** 访问令牌只放内存，刷新令牌使用当前标签页的 sessionStorage 保存。 */
class AuthStore {
  #accessToken = null;
  #refreshToken = null;
  #currentUser = null;
  #listeners = new Set();

  get accessToken() {
    return this.#accessToken;
  }

  get refreshToken() {
    return this.#refreshToken;
  }

  get currentUser() {
    return this.#currentUser;
  }

  get isAuthenticated() {
    return Boolean(this.#accessToken && this.#currentUser);
  }

  async restore() {
    let storedRefreshToken = null;
    try {
      storedRefreshToken = sessionStorage.getItem(REFRESH_TOKEN_KEY);
    } catch {
      storedRefreshToken = null;
    }
    if (!storedRefreshToken) {
      this.clear();
      return null;
    }

    this.#refreshToken = storedRefreshToken;
    try {
      const tokens = await refresh(storedRefreshToken);
      this.setSession(tokens, null, { notify: false });
      const user = await getCurrentUser();
      this.setSession(tokens, user);
      return user;
    } catch (error) {
      if (isInvalidSessionError(error)) {
        this.clear();
        return null;
      }
      // 网络错误和服务端暂时故障不代表刷新令牌失效，保留会话以便用户重试。
      throw error;
    }
  }

  setSession(tokenResponse, user = this.#currentUser, { notify = true } = {}) {
    if (!tokenResponse?.accessToken || !tokenResponse?.refreshToken) {
      throw new TypeError('令牌响应不完整');
    }
    this.#accessToken = tokenResponse.accessToken;
    this.#refreshToken = tokenResponse.refreshToken;
    this.#currentUser = user ?? null;
    try {
      sessionStorage.setItem(REFRESH_TOKEN_KEY, this.#refreshToken);
    } catch {
      // 隐私模式可能禁用 Web Storage；当前标签页内的会话仍可继续使用。
    }
    if (notify) {
      this.#emit();
    }
  }

  clear() {
    const hadState = Boolean(this.#accessToken || this.#refreshToken || this.#currentUser);
    this.#accessToken = null;
    this.#refreshToken = null;
    this.#currentUser = null;
    try {
      sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    } catch {
      // 同上，不让存储不可用妨碍清理内存态。
    }
    if (hadState) {
      this.#emit();
    }
  }

  subscribe(listener) {
    if (typeof listener !== 'function') {
      throw new TypeError('监听器必须是函数');
    }
    this.#listeners.add(listener);
    let subscribed = true;
    return () => {
      if (subscribed) {
        subscribed = false;
        this.#listeners.delete(listener);
      }
    };
  }

  #emit() {
    const snapshot = {
      authenticated: this.isAuthenticated,
      currentUser: this.#currentUser
    };
    for (const listener of [...this.#listeners]) {
      try {
        listener(snapshot);
      } catch (error) {
        console.error('登录状态监听器执行失败', error);
      }
    }
  }
}

export const authStore = new AuthStore();
