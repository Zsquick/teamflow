import { createComment, deleteComment, listComments } from '../api/comment-api.js';
import { getProject } from '../api/project-api.js';
import { listTeamMembers, listTeams } from '../api/team-api.js';
import { deleteTask, getTask, updateTask } from '../api/task-api.js';
import { confirmAction } from '../components/confirm-dialog.js';
import { createBreadcrumb, createContextTabs, taskTabs } from '../components/context-navigation.js';
import { createEmptyState } from '../components/loading.js';
import { openModal } from '../components/modal.js';
import {
  createField,
  createStatusBadge,
  createTitleBlock,
  optionalText,
  setButtonBusy
} from '../components/page-elements.js';
import { showToast } from '../components/toast.js';
import { clearElement, element, formToObject } from '../utils/dom.js';
import { formatDateTime, toDateTimeLocalValue, toUtcDateTimeText } from '../utils/format.js';

const statusLabels = { TODO: '待办', IN_PROGRESS: '进行中', DONE: '已完成' };
const priorityLabels = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' };
const nextStatuses = {
  TODO: ['TODO', 'IN_PROGRESS'],
  IN_PROGRESS: ['TODO', 'IN_PROGRESS', 'DONE'],
  DONE: ['IN_PROGRESS', 'DONE']
};

/** 任务详情与独立评论页签。附件保留原有 /files 路由。 */
export async function renderTaskDetailPage({ root, params, searchParams, authStore, navigate, signal }) {
  let task = await getTask(params.taskId, { signal });
  const [project, teams] = await Promise.all([
    getProject(task.projectId, { signal }),
    listTeams({ signal })
  ]);
  const members = await listTeamMembers(project.teamId, { signal });
  const team = teams.find((item) => item.id === project.teamId);
  const editable = project.status === 'ACTIVE';
  const activeTab = searchParams.get('tab') === 'comments' ? 'comments' : 'details';
  let comments = activeTab === 'comments' ? await listComments(task.id, { signal }) : [];
  let activeModalClose = null;
  let conflictMessage = null;
  let scrollCommentsAfterRender = false;

  const render = () => {
    const actions = [];
    if (editable) {
      const edit = element('button', {
        className: 'button button--primary',
        textContent: '编辑任务',
        attributes: { type: 'button' }
      });
      edit.addEventListener('click', openEditModal);
      actions.push(edit, createMoreActions());
    }

    clearElement(root).append(element('section', {
      className: 'page task-detail-page',
      children: [
        createBreadcrumb([
          { label: '团队', href: '/teams' },
          { label: team?.name || project.teamId, href: `/teams/${encodeURIComponent(project.teamId)}` },
          { label: project.name, href: `/projects/${encodeURIComponent(project.id)}/board` },
          activeTab === 'details' ? { label: task.id } : { label: task.id, href: `/tasks/${encodeURIComponent(task.id)}` },
          activeTab === 'comments' ? { label: '评论' } : null
        ], navigate),
        createTitleBlock({
          eyebrow: `${project.projectKey} · ${task.id}`,
          title: task.title,
          description: `所属项目：${project.name}`,
          status: createStatusBadge(task.status, statusLabels),
          actions
        }),
        taskSummary(),
        createContextTabs(taskTabs(task.id), activeTab, navigate, '任务导航'),
        project.status === 'ARCHIVED'
          ? element('div', { className: 'notice', textContent: '所属项目已归档，任务、评论和附件只能查看。' })
          : null,
        conflictMessage ? createConflictNotice() : null,
        activeTab === 'comments' ? commentPanel() : detailsPanel()
      ]
    }));

    if (scrollCommentsAfterRender) {
      scrollCommentsAfterRender = false;
      queueMicrotask(() => {
        const list = root.querySelector('.comment-scroll');
        if (list) list.scrollTop = list.scrollHeight;
      });
    }
  };

  const taskSummary = () => element('dl', {
    className: 'task-summary-bar',
    children: [
      summary('状态', statusLabels[task.status] ?? task.status),
      summary('优先级', priorityLabels[task.priority] ?? task.priority),
      summary('负责人', memberName(task.assigneeId) || '未分配'),
      summary('截止时间', formatDateTime(task.dueAt)),
      summary('版本', `v${task.version}`)
    ]
  });

  const detailsPanel = () => element('div', {
    className: 'task-content-grid',
    children: [
      element('article', {
        className: 'card task-description',
        children: [
          element('div', { className: 'section-heading', children: [element('h2', { textContent: '任务描述' })] }),
          element('p', { textContent: task.description || '这个任务还没有填写描述。' })
        ]
      }),
      element('article', {
        className: 'card',
        children: [
          element('div', { className: 'section-heading', children: [element('h2', { textContent: '记录信息' })] }),
          element('dl', {
            className: 'meta-list',
            children: [
              meta('任务编号', task.id),
              meta('创建者', memberName(task.reporterId) || task.reporterId),
              meta('创建时间', formatDateTime(task.createdAt)),
              meta('最后更新', formatDateTime(task.updatedAt))
            ]
          })
        ]
      })
    ]
  });

  const commentPanel = () => {
    const list = element('div', {
      className: 'comment-scroll',
      attributes: { tabindex: '0', 'aria-label': '评论列表' }
    });
    if (comments.length === 0) list.append(createEmptyState('还没有评论，可以在下方发表第一条。'));
    for (const comment of comments) list.append(commentItem(comment));

    return element('article', {
      className: 'card comment-panel',
      children: [
        element('div', {
          className: 'section-heading comment-panel__heading',
          children: [
            element('div', { children: [element('h2', { textContent: '评论' }), element('span', { className: 'muted', textContent: `${comments.length} 条` })] }),
            element('span', { className: 'muted', textContent: '列表在区域内滚动' })
          ]
        }),
        list,
        editable ? createCommentForm() : element('p', { className: 'comment-panel__readonly', textContent: '归档项目不能继续发表评论。' })
      ]
    });
  };

  const commentItem = (comment) => {
    const actions = [];
    if (comment.authorId === authStore.currentUser.id && editable) {
      const remove = element('button', {
        className: 'button button--ghost button--small',
        textContent: '删除',
        attributes: { type: 'button' }
      });
      remove.addEventListener('click', () => void removeComment(comment, remove));
      actions.push(remove);
    }
    return element('article', {
      className: 'comment',
      children: [
        element('header', {
          children: [
            element('div', {
              className: 'comment__author',
              children: [
                element('span', { className: 'avatar avatar--small', textContent: initials(comment.authorName || comment.authorId) }),
                element('strong', { textContent: comment.authorName || memberName(comment.authorId) || comment.authorId })
              ]
            }),
            element('time', { textContent: formatDateTime(comment.createdAt) })
          ]
        }),
        element('p', { textContent: comment.content }),
        actions.length ? element('div', { className: 'button-row button-row--end', children: actions }) : null
      ]
    });
  };

  const createCommentForm = () => {
    const textarea = element('textarea', {
      attributes: {
        name: 'content',
        required: true,
        maxlength: 2000,
        rows: 3,
        placeholder: '写一条评论…',
        'aria-label': '评论内容'
      }
    });
    const submit = element('button', {
      className: 'button button--primary',
      textContent: '发表评论',
      attributes: { type: 'submit' }
    });
    const form = element('form', {
      className: 'comment-composer',
      children: [
        element('div', { className: 'form-error', attributes: { role: 'alert', hidden: true } }),
        textarea,
        element('div', {
          className: 'comment-composer__footer',
          children: [element('small', { className: 'muted', textContent: '最多 2000 字' }), submit]
        })
      ]
    });
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      setButtonBusy(submit, true, '发送中…');
      try {
        const values = formToObject(form);
        const created = await createComment(task.id, { content: values.content }, { signal });
        comments = [...comments, created];
        scrollCommentsAfterRender = true;
        showToast('评论已发布', 'success');
        render();
      } catch (error) {
        if (error?.name !== 'AbortError') showFormError(form, error, '发表评论失败');
      } finally {
        if (submit.isConnected) setButtonBusy(submit, false);
      }
    });
    return form;
  };

  const createConflictNotice = () => {
    const reload = element('button', {
      className: 'button button--secondary button--small',
      textContent: '重新加载最新数据',
      attributes: { type: 'button' }
    });
    reload.addEventListener('click', () => void reloadTask(reload));
    return element('div', {
      className: 'conflict-notice',
      attributes: { role: 'alert' },
      children: [
        element('div', { children: [element('strong', { textContent: '任务已发生并发修改' }), element('p', { textContent: conflictMessage })] }),
        reload
      ]
    });
  };

  const createMoreActions = () => {
    const remove = element('button', {
      className: 'action-menu__item action-menu__item--danger',
      textContent: '删除任务',
      attributes: { type: 'button' }
    });
    remove.addEventListener('click', () => void removeTask(remove));
    return element('details', {
      className: 'action-menu',
      children: [
        element('summary', { className: 'button button--ghost', textContent: '更多操作' }),
        element('div', { className: 'action-menu__panel', children: [remove] })
      ]
    });
  };

  function openEditModal() {
    const title = input('title', 'text', { value: task.title, required: true, maxlength: 200, autofocus: true });
    const description = element('textarea', { attributes: { name: 'description', rows: 6, maxlength: 5000 } });
    description.value = task.description ?? '';
    const status = select('status', nextStatuses[task.status].map((value) => [value, statusLabels[value]]), task.status);
    const priority = select('priority', Object.entries(priorityLabels), task.priority);
    const assigneeOptions = [['', '暂不分配'], ...members.map((member) => [member.userId, member.displayName || member.username])];
    if (task.assigneeId && !members.some((member) => member.userId === task.assigneeId)) {
      assigneeOptions.push([task.assigneeId, `${task.assigneeId}（已不在团队）`]);
    }
    const assignee = select('assigneeId', assigneeOptions, task.assigneeId ?? '');
    const dueAt = input('dueAt', 'datetime-local', { value: toDateTimeLocalValue(task.dueAt) });
    const save = element('button', {
      className: 'button button--primary',
      textContent: '保存修改',
      attributes: { type: 'submit' }
    });
    const form = element('form', {
      className: 'form-stack modal-form',
      children: [
        element('div', { className: 'form-error', attributes: { role: 'alert', hidden: true } }),
        createField('标题', title),
        createField('描述', description),
        element('div', { className: 'form-grid', children: [createField('状态', status), createField('优先级', priority)] }),
        element('div', { className: 'form-grid', children: [createField('负责人', assignee), createField('截止时间', dueAt)] }),
        element('div', { className: 'button-row button-row--end', children: [save] })
      ]
    });
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      setButtonBusy(save, true, '保存中…');
      try {
        const values = formToObject(form);
        task = await updateTask(task.id, {
          title: values.title.trim(),
          description: optionalText(values.description),
          status: values.status,
          priority: values.priority,
          assigneeId: optionalText(values.assigneeId),
          dueAt: toUtcDateTimeText(values.dueAt),
          version: task.version
        }, { signal });
        conflictMessage = null;
        activeModalClose?.();
        showToast('任务已更新', 'success');
        render();
      } catch (error) {
        if (error?.status === 409) {
          activeModalClose?.();
          conflictMessage = '你编辑期间，其他成员已保存新版本。请重新加载后再决定如何修改。';
          render();
        } else if (error?.name !== 'AbortError') {
          showFormError(form, error, '保存任务失败');
        }
      } finally {
        if (save.isConnected) setButtonBusy(save, false);
      }
    });
    activeModalClose = openModal({ title: '编辑任务', content: form, onClose: () => { activeModalClose = null; } });
  }

  const removeTask = async (button) => {
    button.closest('details')?.removeAttribute('open');
    const confirmed = await confirmAction({
      title: '删除任务',
      message: `确定删除“${task.title}”吗？任务将从看板中移除。`,
      confirmLabel: '确认删除',
      danger: true
    });
    if (!confirmed) return;
    setButtonBusy(button, true, '删除中…');
    try {
      await deleteTask(task.id, task.version, { signal });
      showToast('任务已删除', 'success');
      await navigate(`/projects/${encodeURIComponent(project.id)}/board`);
    } catch (error) {
      if (error?.status === 409) {
        conflictMessage = '任务版本已变化，请重新加载最新内容后再删除。';
        render();
      } else if (error?.name !== 'AbortError') {
        showToast(error.message || '删除任务失败', 'error');
      }
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  const removeComment = async (comment, button) => {
    const confirmed = await confirmAction({
      title: '删除评论',
      message: '确定删除这条评论吗？',
      confirmLabel: '删除',
      danger: true
    });
    if (!confirmed) return;
    setButtonBusy(button, true, '删除中…');
    try {
      await deleteComment(comment.id, { signal });
      comments = comments.filter((item) => item.id !== comment.id);
      showToast('评论已删除', 'success');
      render();
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '删除评论失败', 'error');
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  const reloadTask = async (button) => {
    setButtonBusy(button, true, '加载中…');
    try {
      task = await getTask(task.id, { signal });
      conflictMessage = null;
      showToast('已加载最新任务数据', 'success');
      render();
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '加载最新任务失败', 'error');
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  const memberName = (userId) => members.find((member) => member.userId === userId)?.displayName;

  render();
  return () => activeModalClose?.();
}

function input(name, type, attributes) {
  return element('input', { attributes: { name, type, ...attributes } });
}

function select(name, options, value) {
  const node = element('select', { attributes: { name, required: true } });
  for (const [optionValue, label] of options) node.append(element('option', { textContent: label, attributes: { value: optionValue } }));
  node.value = value;
  return node;
}

function summary(label, value) {
  return element('div', { children: [element('dt', { textContent: label }), element('dd', { textContent: value || '--' })] });
}

function meta(label, value) {
  return element('div', { children: [element('dt', { textContent: label }), element('dd', { textContent: value || '--' })] });
}

function initials(value) {
  return [...String(value || 'U').trim()].slice(0, 2).join('').toLocaleUpperCase('zh-CN');
}

function showFormError(form, error, fallback) {
  const node = form.querySelector('.form-error');
  if (node) {
    node.hidden = false;
    node.textContent = error?.message || fallback;
  }
  showToast(error?.message || fallback, 'error');
}
