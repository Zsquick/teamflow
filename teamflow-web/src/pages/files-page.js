import { listAttachments } from '../api/file-api.js';
import { getProject } from '../api/project-api.js';
import { getTask } from '../api/task-api.js';
import { listTeamMembers, listTeams } from '../api/team-api.js';
import { createAttachmentList } from '../components/attachment-list.js';
import { createBreadcrumb, createContextTabs, taskTabs } from '../components/context-navigation.js';
import { createEmptyState } from '../components/loading.js';
import { openModal } from '../components/modal.js';
import { createField, createStatusBadge, createTitleBlock, setButtonBusy } from '../components/page-elements.js';
import { showToast } from '../components/toast.js';
import { deleteAttachmentWithFeedback, downloadAttachmentWithFeedback } from '../files/attachment-actions.js';
import { uploadTaskAttachment } from '../files/file-transfer.js';
import { clearElement, element } from '../utils/dom.js';

const statusLabels = { TODO: '待办', IN_PROGRESS: '进行中', DONE: '已完成' };

/** 任务上下文中的独立附件页签。 */
export async function renderFilesPage({ root, params, authStore, navigate, signal }) {
  const [task, initialAttachments] = await Promise.all([
    getTask(params.taskId, { signal }),
    listAttachments(params.taskId, { signal })
  ]);
  const [project, teams] = await Promise.all([
    getProject(task.projectId, { signal }),
    listTeams({ signal })
  ]);
  const members = await listTeamMembers(project.teamId, { signal });
  const team = teams.find((item) => item.id === project.teamId);
  let attachments = initialAttachments;
  let activeModalClose = null;
  const editable = project.status === 'ACTIVE';
  const currentMember = members.find((member) => member.userId === authStore.currentUser.id);
  const canManageAttachments = ['OWNER', 'ADMIN'].includes(currentMember?.role);

  const render = () => {
    const upload = element('button', {
      className: 'button button--primary',
      textContent: '上传附件',
      attributes: { type: 'button', disabled: !editable }
    });
    upload.disabled = !editable;
    upload.addEventListener('click', openUploadModal);

    const list = attachments.length
      ? createAttachmentList(attachments, {
          canDelete: (attachment) => editable
            && (attachment.uploaderId === authStore.currentUser.id || canManageAttachments),
          onDownload: (attachment, button) => void downloadAttachmentWithFeedback(attachment, button, { signal }),
          onDelete: (attachment, button) => void removeAttachment(attachment, button)
        })
      : createEmptyState(editable
          ? '这个任务还没有附件，使用右上角上传。'
          : '这个任务没有可查看的附件。');

    clearElement(root).append(element('section', {
      className: 'page files-page',
      children: [
        createBreadcrumb([
          { label: '团队', href: '/teams' },
          { label: team?.name || project.teamId, href: `/teams/${encodeURIComponent(project.teamId)}` },
          { label: project.name, href: `/projects/${encodeURIComponent(project.id)}/board` },
          { label: task.id, href: `/tasks/${encodeURIComponent(task.id)}` },
          { label: '附件' }
        ], navigate),
        createTitleBlock({
          eyebrow: `${project.projectKey} · ${task.id}`,
          title: task.title,
          description: '上传、下载并管理这个任务的文件。',
          status: createStatusBadge(task.status, statusLabels),
          actions: [upload]
        }),
        createContextTabs(taskTabs(task.id), 'attachments', navigate, '任务导航'),
        !editable ? element('div', { className: 'notice', textContent: '所属项目已归档，附件仅可查看和下载。' }) : null,
        element('article', {
          className: 'card attachment-panel',
          children: [
            element('div', {
              className: 'section-heading',
              children: [
                element('div', { children: [element('h2', { textContent: '附件列表' }), element('span', { className: 'muted', textContent: `${attachments.length} 个` })] }),
                element('span', { className: 'muted', textContent: '列表在区域内滚动' })
              ]
            }),
            element('div', { className: 'attachment-scroll', children: [list] })
          ]
        })
      ]
    }));
  };

  function openUploadModal() {
    if (!editable) return;
    const file = element('input', {
      attributes: { type: 'file', name: 'file', required: true, autofocus: true }
    });
    const submit = element('button', {
      className: 'button button--primary',
      textContent: '开始上传',
      attributes: { type: 'submit' }
    });
    const form = element('form', {
      className: 'form-stack modal-form',
      children: [
        element('div', { className: 'form-error', attributes: { role: 'alert', hidden: true } }),
        createField('选择文件', file, '文件不能为空，最大 20 MB。'),
        element('p', { className: 'muted', textContent: '文件会以 FormData 上传，浏览器自动生成 multipart boundary。' }),
        element('div', { className: 'button-row button-row--end', children: [submit] })
      ]
    });
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      setButtonBusy(submit, true, '上传中…');
      try {
        const created = await uploadTaskAttachment(task.id, file.files?.[0], null, { signal });
        attachments = [...attachments, created];
        activeModalClose?.();
        showToast('附件上传成功', 'success');
        render();
      } catch (error) {
        if (error?.name !== 'AbortError') {
          const errorNode = form.querySelector('.form-error');
          errorNode.hidden = false;
          errorNode.textContent = error.message || '上传失败';
          showToast(error.message || '上传失败', 'error');
        }
      } finally {
        if (submit.isConnected) setButtonBusy(submit, false);
      }
    });
    activeModalClose = openModal({ title: '上传任务附件', content: form, onClose: () => { activeModalClose = null; } });
  }

  const removeAttachment = async (attachment, button) => {
    if (await deleteAttachmentWithFeedback(attachment, button, { signal })) {
      attachments = attachments.filter((item) => item.id !== attachment.id);
      render();
    }
  };

  render();
  return () => activeModalClose?.();
}
