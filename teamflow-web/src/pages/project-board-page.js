import { createTask, changeTaskStatus, listTasks } from '../api/task-api.js';
import { getProject } from '../api/project-api.js';
import { listTeamMembers, listTeams } from '../api/team-api.js';
import { createTaskCard } from '../components/task-card.js';
import { createEmptyState } from '../components/loading.js';
import { openModal } from '../components/modal.js';
import { createBreadcrumb, createContextTabs, projectTabs } from '../components/context-navigation.js';
import {
  createField,
  createStatusBadge,
  createTitleBlock,
  optionalText,
  setButtonBusy
} from '../components/page-elements.js';
import { showToast } from '../components/toast.js';
import { clearElement, element, formToObject } from '../utils/dom.js';
import { toUtcDateTimeText } from '../utils/format.js';

const statuses = [
  ['TODO', '待办'],
  ['IN_PROGRESS', '进行中'],
  ['DONE', '已完成']
];
const validMoves = {
  TODO: new Set(['IN_PROGRESS']),
  IN_PROGRESS: new Set(['TODO', 'DONE']),
  DONE: new Set(['IN_PROGRESS'])
};
const TASK_PAGE_SIZE = 30;
const SEARCH_DEBOUNCE_MS = 200;

/** 按 TODO/IN_PROGRESS/DONE 分栏的项目看板。 */
export async function renderProjectBoardPage({ root, params, navigate, signal }) {
  const project = await getProject(params.projectId, { signal });
  const [initialPages, members, teams] = await Promise.all([
    Promise.all(statuses.map(([status]) => listTasks(project.id, 1, TASK_PAGE_SIZE, status, { signal }))),
    listTeamMembers(project.teamId, { signal }),
    listTeams({ signal })
  ]);
  const team = teams.find((item) => item.id === project.teamId);
  const columns = new Map(statuses.map(([status], index) => [status, createColumnState(initialPages[index])]));
  const columnGenerations = new Map(statuses.map(([status]) => [status, 0]));
  let query = '';
  let searchText = '';
  let searchTimer = null;
  let boardElement = null;
  let draggedTask = null;
  let activeModalClose = null;
  const movingTaskIds = new Set();

  const render = () => {
    const createButton = element('button', {
      className: 'button button--primary',
      textContent: '新建任务',
      attributes: { type: 'button' }
    });
    createButton.disabled = project.status !== 'ACTIVE';
    createButton.addEventListener('click', () => openCreateTaskModal());

    const search = element('input', {
      attributes: { type: 'search', placeholder: '筛选已加载的任务', value: searchText, 'aria-label': '筛选已加载的看板任务' }
    });
    search.addEventListener('input', () => {
      searchText = search.value;
      if (searchTimer != null) window.clearTimeout(searchTimer);
      searchTimer = window.setTimeout(() => {
        searchTimer = null;
        query = searchText;
        if (boardElement?.isConnected) renderColumns(boardElement);
      }, SEARCH_DEBOUNCE_MS);
    });

    const board = element('div', { className: 'task-board' });
    boardElement = board;
    renderColumns(board);
    const totalTasks = [...columns.values()].reduce((sum, column) => sum + column.total, 0);
    const loadedTasks = [...columns.values()].reduce((sum, column) => sum + column.items.length, 0);
    clearElement(root).append(element('section', {
      className: 'page board-page',
      children: [
        createBreadcrumb([
          { label: '团队', href: '/teams' },
          { label: team?.name || project.teamId, href: `/teams/${encodeURIComponent(project.teamId)}` },
          { label: project.name, href: `/projects/${encodeURIComponent(project.id)}/board` },
          { label: '任务看板' }
        ], navigate),
        createTitleBlock({
          eyebrow: project.projectKey,
          title: project.name,
          description: project.description || '按任务状态跟踪进度，点击任务卡查看详情。',
          status: createStatusBadge(project.status, { ACTIVE: '进行中', ARCHIVED: '已归档' }),
          actions: [createButton]
        }),
        createContextTabs(projectTabs(project.id), 'board', navigate, '项目导航'),
        project.status === 'ARCHIVED' ? element('div', { className: 'notice', textContent: '项目已归档，任务只能查看。' }) : null,
        element('div', {
          className: 'board-toolbar',
          children: [
            search,
            element('span', {
              className: 'muted',
              textContent: loadedTasks < totalTasks
                ? `已加载 ${loadedTasks} / 共 ${totalTasks} 个任务`
                : `共 ${totalTasks} 个任务`
            })
          ]
        }),
        board
      ]
    }));
  };

  const renderColumns = (board) => {
    clearElement(board);
    const needle = query.trim().toLocaleLowerCase('zh-CN');
    for (const [status, label] of statuses) {
      const column = columns.get(status);
      const items = column.items.filter((task) => !needle || [task.title, task.description, task.assigneeId]
        .filter(Boolean).some((value) => String(value).toLocaleLowerCase('zh-CN').includes(needle)));
      const list = element('div', {
        className: 'board-column__list',
        attributes: { 'data-status': status, 'aria-label': `${label}任务` }
      });
      if (items.length === 0) {
        list.append(createEmptyState(needle ? '已加载的任务中没有匹配项' : '暂无任务'));
      }
      for (const task of items) {
        const card = createTaskCard(task, {
          onOpen: (item) => void navigate(`/tasks/${encodeURIComponent(item.id)}`),
          onMove: project.status === 'ACTIVE' ? (item, target) => void moveTask(item, target) : null,
          onDropStatus: project.status === 'ACTIVE',
          onDragStart: (item) => { draggedTask = item; },
          onDragEnd: () => { draggedTask = null; }
        });
        if (movingTaskIds.has(task.id)) {
          card.setAttribute('aria-busy', 'true');
          card.classList.add('task-card--busy');
        }
        list.append(card);
      }
      if (project.status === 'ACTIVE') {
        list.addEventListener('dragover', (event) => {
          if (draggedTask && validMoves[draggedTask.status]?.has(status)) {
            event.preventDefault();
            if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
            list.classList.add('board-column__list--drop-target');
          }
        });
        list.addEventListener('dragleave', (event) => {
          if (!list.contains(event.relatedTarget)) list.classList.remove('board-column__list--drop-target');
        });
        list.addEventListener('drop', (event) => {
          event.preventDefault();
          list.classList.remove('board-column__list--drop-target');
          if (draggedTask) void moveTask(draggedTask, status);
        });
      }
      const hasMore = column.page < Math.ceil(column.total / column.size);
      const loadMore = hasMore ? element('button', {
        className: 'button button--ghost button--small board-column__load-more',
        textContent: column.loading ? '加载中…' : `加载更多（还有 ${Math.max(0, column.total - column.items.length)} 项）`,
        attributes: { type: 'button' }
      }) : null;
      if (loadMore) {
        loadMore.disabled = column.loading;
        loadMore.addEventListener('click', () => void loadMoreTasks(status));
      }
      board.append(element('section', {
        className: `board-column board-column--${status.toLowerCase().replace('_', '-')}`,
        children: [
          element('header', {
            className: 'board-column__header',
            children: [element('h2', { textContent: label }), element('span', { className: 'badge', textContent: String(column.total) })]
          }),
          list,
          loadMore
        ]
      }));
    }
  };

  const reloadTasks = async () => {
    await reloadTaskColumns(...statuses.map(([status]) => status));
    render();
  };

  const reloadTaskColumns = async (...columnStatuses) => {
    const uniqueStatuses = [...new Set(columnStatuses)];
    const requests = uniqueStatuses.map((status) => {
      const generation = (columnGenerations.get(status) ?? 0) + 1;
      columnGenerations.set(status, generation);
      return {
        status,
        generation,
        result: listTasks(project.id, 1, TASK_PAGE_SIZE, status, { signal })
      };
    });
    const pages = await Promise.all(requests.map(({ result }) => result));
    requests.forEach(({ status, generation }, index) => {
      if (columnGenerations.get(status) === generation) {
        columns.set(status, createColumnState(pages[index]));
      }
    });
  };

  const loadMoreTasks = async (status) => {
    const column = columns.get(status);
    if (!column || column.loading || column.page >= Math.ceil(column.total / column.size)) return;
    column.loading = true;
    render();
    try {
      const generation = columnGenerations.get(status) ?? 0;
      const expectedTotal = column.total;
      const nextPage = await listTasks(project.id, column.page + 1, column.size, status, { signal });
      if (columnGenerations.get(status) !== generation || columns.get(status) !== column) return;
      const knownIds = new Set(column.items.map((task) => task.id));
      const newItems = nextPage.items.filter((task) => !knownIds.has(task.id));
      const duplicateDetected = newItems.length !== nextPage.items.length;
      const totalChanged = nextPage.total !== expectedTotal;
      const reachedLastPage = nextPage.page >= Math.ceil(nextPage.total / nextPage.size);
      const finalPageIncomplete = reachedLastPage
        && column.items.length + newItems.length < nextPage.total;
      if (duplicateDetected || totalChanged || finalPageIncomplete) {
        await reloadTaskColumns(status);
        if (!signal.aborted) showToast('任务列表已发生变化，已从第一页重新同步', 'info');
        return;
      }
      column.items.push(...newItems);
      column.page = nextPage.page;
      column.size = nextPage.size;
      column.total = nextPage.total;
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '加载更多任务失败', 'error');
    } finally {
      column.loading = false;
      if (!signal.aborted) render();
    }
  };

  const moveTask = async (task, targetStatus) => {
    if (task.status === targetStatus || movingTaskIds.has(task.id)) return;
    if (!validMoves[task.status]?.has(targetStatus)) {
      showToast('任务只能移动到相邻状态', 'info');
      return;
    }
    movingTaskIds.add(task.id);
    render();
    try {
      const updated = await changeTaskStatus(task.id, { status: targetStatus, version: task.version }, { signal });
      const sourceColumn = columns.get(task.status);
      const targetColumn = columns.get(updated.status);
      for (const column of columns.values()) {
        column.items = column.items.filter((item) => item.id !== updated.id);
      }
      sourceColumn.total = Math.max(0, sourceColumn.total - 1);
      targetColumn.total += 1;
      targetColumn.items = [updated, ...targetColumn.items];
      try {
        await reloadTaskColumns(task.status, updated.status);
      } catch (reloadError) {
        if (reloadError?.name !== 'AbortError') {
          showToast('状态已更新，但看板数量暂未同步；稍后重新打开即可刷新', 'info');
        }
      }
      showToast('任务状态已更新', 'success');
    } catch (error) {
      if (error?.status === 409) {
        showToast('任务已被其他成员修改，正在重新加载最新内容', 'info');
        try {
          await reloadTasks();
        } catch (reloadError) {
          if (reloadError?.name !== 'AbortError') {
            showToast(reloadError.message || '重新加载任务失败', 'error');
          }
        }
        return;
      }
      if (error?.name !== 'AbortError') showToast(error.message || '移动任务失败', 'error');
    } finally {
      movingTaskIds.delete(task.id);
      if (!signal.aborted) render();
    }
  };

  const openCreateTaskModal = () => {
    const priority = select('priority', [['LOW', '低'], ['MEDIUM', '中'], ['HIGH', '高'], ['URGENT', '紧急']], 'MEDIUM');
    const assignee = select('assigneeId', [['', '暂不分配'], ...members.map((member) => [member.userId, member.displayName || member.username])], '');
    const form = element('form', {
      className: 'form-stack modal-form',
      children: [
        createField('标题', element('input', { attributes: { name: 'title', required: true, maxlength: 200, autofocus: true } })),
        createField('描述', element('textarea', { attributes: { name: 'description', rows: 5, maxlength: 5000 } })),
        element('div', { className: 'form-grid', children: [createField('优先级', priority), createField('负责人', assignee)] }),
        createField('截止时间', element('input', { attributes: { name: 'dueAt', type: 'datetime-local' } })),
        element('button', { className: 'button button--primary', textContent: '创建任务', attributes: { type: 'submit' } })
      ]
    });
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = form.querySelector('[type="submit"]');
      setButtonBusy(button, true, '创建中…');
      try {
        const values = formToObject(form);
        const created = await createTask({
          projectId: project.id,
          title: values.title.trim(),
          description: optionalText(values.description),
          priority: values.priority,
          assigneeId: optionalText(values.assigneeId),
          dueAt: toUtcDateTimeText(values.dueAt)
        }, { signal });
        const targetColumn = columns.get(created.status);
        targetColumn.items = [created, ...targetColumn.items];
        targetColumn.total += 1;
        try {
          await reloadTaskColumns(created.status);
        } catch (reloadError) {
          if (reloadError?.name !== 'AbortError') {
            showToast('任务已创建，但列表暂未同步；稍后重新打开即可刷新', 'info');
          }
        }
        activeModalClose?.();
        showToast('任务创建成功', 'success');
        render();
      } catch (error) {
        if (error?.name !== 'AbortError') showToast(error.message || '创建任务失败', 'error');
      } finally {
        if (button.isConnected) setButtonBusy(button, false);
      }
    });
    activeModalClose = openModal({ title: '新建任务', content: form, onClose: () => { activeModalClose = null; } });
  };

  render();
  return () => {
    if (searchTimer != null) window.clearTimeout(searchTimer);
    activeModalClose?.();
  };
}

function createColumnState(pageResult) {
  return {
    items: [...pageResult.items],
    page: pageResult.page,
    size: pageResult.size,
    total: pageResult.total,
    loading: false
  };
}

function select(name, options, selectedValue) {
  const node = element('select', { attributes: { name } });
  for (const [value, label] of options) {
    node.append(element('option', { textContent: label, attributes: { value } }));
  }
  node.value = selectedValue;
  return node;
}
