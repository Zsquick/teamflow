import { downloadAttachment, uploadAttachment } from '../api/file-api.js';

const MAX_ATTACHMENT_SIZE = 20 * 1024 * 1024;

/** 上传附件；底层使用 FormData。 */
export async function uploadTaskAttachment(taskId, file, onProgress, options = {}) {
  if (!(file instanceof File)) {
    throw new TypeError('请选择要上传的文件');
  }
  if (file.size === 0) {
    throw new Error('不能上传空文件');
  }
  if (file.size > MAX_ATTACHMENT_SIZE) {
    throw new Error('附件不能超过 20 MB');
  }
  return uploadAttachment(taskId, file, onProgress, options);
}

/** 使用 Blob 和临时 a 元素触发浏览器保存。 */
export async function saveAttachment(attachment, options = {}) {
  if (!attachment?.id) {
    throw new TypeError('附件信息不完整');
  }
  const downloaded = await downloadAttachment(attachment.id, options);
  const url = URL.createObjectURL(downloaded.blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = safeFileName(downloaded.fileName || attachment.originalName || 'attachment');
  anchor.hidden = true;
  document.body.append(anchor);
  try {
    anchor.click();
  } finally {
    window.setTimeout(() => {
      anchor.remove();
      URL.revokeObjectURL(url);
    }, 1000);
  }
}

function safeFileName(value) {
  const normalized = String(value).replaceAll('\\', '/');
  const name = normalized.slice(normalized.lastIndexOf('/') + 1).replace(/[\u0000-\u001f\u007f]/g, '').trim();
  return name || 'attachment';
}
