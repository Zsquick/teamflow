import { element } from '../utils/dom.js';
import { formatDateTime, formatFileSize } from '../utils/format.js';

/** 渲染附件元数据并把下载、删除行为交给页面。 */
export function createAttachmentList(attachments, handlers = {}) {
  const list = element('ul', { className: 'attachment-list' });
  for (const attachment of attachments) {
    const download = element('button', {
      className: 'button button--secondary button--small',
      textContent: '下载',
      attributes: { type: 'button' }
    });
    download.addEventListener('click', () => handlers.onDownload?.(attachment, download));

    const actions = [download];
    if (handlers.canDelete?.(attachment)) {
      const remove = element('button', {
        className: 'button button--danger button--small',
        textContent: '删除',
        attributes: { type: 'button' }
      });
      remove.addEventListener('click', () => handlers.onDelete?.(attachment, remove));
      actions.push(remove);
    }

    list.append(element('li', {
      className: 'attachment-item',
      children: [
        element('div', {
          className: 'attachment-item__body',
          children: [
            element('strong', {
              className: 'attachment-item__name',
              textContent: attachment.originalName || '未命名附件',
              attributes: { title: attachment.originalName || '未命名附件' }
            }),
            element('span', {
              className: 'muted',
              textContent: `${formatFileSize(attachment.size)} · 上传者 ${attachment.uploaderName || attachment.uploaderId || '--'} · ${formatDateTime(attachment.createdAt)}`
            }),
            element('code', {
              className: 'attachment-item__digest',
              textContent: shortDigest(attachment.sha256),
              attributes: { title: attachment.sha256 || 'SHA-256 暂缺' }
            })
          ]
        }),
        element('div', { className: 'button-row', children: actions })
      ]
    }));
  }
  return list;
}

function shortDigest(value) {
  const digest = String(value || '');
  if (!digest) return 'SHA-256：--';
  return `SHA-256：${digest.slice(0, 12)}…${digest.slice(-8)}`;
}
