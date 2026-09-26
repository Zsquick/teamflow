import { request, requestRaw } from './http.js';

const id = encodeURIComponent;

export function listAttachments(taskId, options = {}) {
  return request(`/api/tasks/${id(taskId)}/attachments`, options);
}

export async function uploadAttachment(taskId, file, onProgress, options = {}) {
  const formData = new FormData();
  formData.append('file', file);
  onProgress?.({ phase: 'uploading' });
  try {
    const attachment = await request(`/api/tasks/${id(taskId)}/attachments`, {
      ...options,
      method: 'POST',
      body: formData,
      timeoutMs: options.timeoutMs ?? 0
    });
    onProgress?.({ phase: 'complete' });
    return attachment;
  } catch (error) {
    onProgress?.({ phase: 'error', error });
    throw error;
  }
}

export async function downloadAttachment(attachmentId, options = {}) {
  const response = await requestRaw(`/api/attachments/${id(attachmentId)}/content`, {
    ...options,
    timeoutMs: options.timeoutMs ?? 0,
    headers: { ...Object.fromEntries(new Headers(options.headers)), Accept: '*/*' }
  });
  const blob = await response.blob();
  return {
    blob,
    fileName: parseDownloadName(response.headers.get('content-disposition')),
    contentType: response.headers.get('content-type') || blob.type || 'application/octet-stream'
  };
}

export function deleteAttachment(attachmentId, options = {}) {
  return request(`/api/attachments/${id(attachmentId)}`, { ...options, method: 'DELETE' });
}

function parseDownloadName(disposition) {
  if (!disposition) {
    return null;
  }
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    try {
      return decodeURIComponent(encoded.replace(/^"|"$/g, ''));
    } catch {
      // 继续尝试兼容 filename。
    }
  }
  const plain = disposition.match(/filename="([^"]*)"/i)?.[1]
    ?? disposition.match(/filename=([^;]+)/i)?.[1];
  return plain?.trim() || null;
}
