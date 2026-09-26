import { request } from './http.js';

/** 验证浏览器到 Spring MVC 的基础 HTTP 链路。 */
export function ping(options = {}) {
  return request('/api/v1/system/ping', { ...options, skipAuthRefresh: true });
}
