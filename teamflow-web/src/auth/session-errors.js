const INVALID_SESSION_CODES = new Set([
  'COMMON_0003',
  'AUTH_0008',
  'AUTH_0009'
]);

/** 只有服务端明确拒绝当前会话时，才删除本地保存的刷新令牌。 */
export function isInvalidSessionError(error) {
  return error?.status === 401
    || INVALID_SESSION_CODES.has(error?.code);
}
