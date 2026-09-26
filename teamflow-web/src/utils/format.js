const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'short'
});

export function formatDateTime(value) {
  if (value == null || value === '') {
    return '--';
  }
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? '--' : dateTimeFormatter.format(date);
}

export function formatFileSize(bytes) {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) {
    return '--';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  let amount = value;
  let unitIndex = 0;
  while (amount >= 1024 && unitIndex < units.length - 1) {
    amount /= 1024;
    unitIndex += 1;
  }
  return unitIndex === 0
    ? `${Math.round(amount)} ${units[unitIndex]}`
    : `${amount.toFixed(1)} ${units[unitIndex]}`;
}

export function escapeCsvCell(value) {
  let text = value == null ? '' : String(value);
  if (/^[=+\-@]/.test(text)) {
    text = `'${text}`;
  }
  if (/[",\r\n]/.test(text)) {
    return `"${text.replaceAll('"', '""')}"`;
  }
  return text;
}

/** 将 datetime-local 的本地时间值转换为后端约定的 UTC 文本。 */
export function toUtcDateTimeText(value) {
  if (value == null || String(value).trim() === '') return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new TypeError('日期时间格式不正确');
  }
  return date.toISOString();
}

/** 将 UTC 文本转换为 datetime-local 控件可显示的本地时间。 */
export function toDateTimeLocalValue(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}
