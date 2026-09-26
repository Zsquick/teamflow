import { deleteAttachment } from '../api/file-api.js';
import { setButtonBusy } from '../components/page-elements.js';
import { confirmAction } from '../components/confirm-dialog.js';
import { showToast } from '../components/toast.js';
import { saveAttachment } from './file-transfer.js';

/** 两个附件页面共用的下载交互。 */
export async function downloadAttachmentWithFeedback(attachment, button, options = {}) {
  setButtonBusy(button, true, '下载中…');
  try {
    await saveAttachment(attachment, options);
    return true;
  } catch (error) {
    if (error?.name !== 'AbortError') showToast(error.message || '下载失败', 'error');
    return false;
  } finally {
    if (button.isConnected) setButtonBusy(button, false);
  }
}

/** 两个附件页面共用的删除确认、请求和反馈；成功时返回 true。 */
export async function deleteAttachmentWithFeedback(attachment, button, options = {}) {
  const confirmed = await confirmAction({
    title: '删除附件',
    message: `确定删除附件“${attachment.originalName}”吗？该操作无法自动撤销。`,
    confirmLabel: '确认删除',
    danger: true
  });
  if (!confirmed) return false;
  setButtonBusy(button, true, '删除中…');
  try {
    await deleteAttachment(attachment.id, options);
    showToast('附件已删除', 'success');
    return true;
  } catch (error) {
    if (error?.name !== 'AbortError') showToast(error.message || '删除附件失败', 'error');
    return false;
  } finally {
    if (button.isConnected) setButtonBusy(button, false);
  }
}
