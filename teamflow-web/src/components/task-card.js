import { element } from '../utils/dom.js';
import { formatDateTime } from '../utils/format.js';

const priorityLabels = {
  LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急'
};
const statusLabels = { TODO: '待办', IN_PROGRESS: '进行中', DONE: '已完成' };
const moves = {
  TODO: [['IN_PROGRESS', '移到进行中']],
  IN_PROGRESS: [['TODO', '移回待办'], ['DONE', '标记完成']],
  DONE: [['IN_PROGRESS', '重新打开']]
};

/** 创建看板任务卡片。 */
export function createTaskCard(task, handlers = {}) {
  if (!task?.id) {
    throw new TypeError('任务卡片缺少任务数据');
  }
  const card = element('article', {
    className: 'task-card',
    attributes: {
      draggable: handlers.onDropStatus ? 'true' : 'false',
      'data-task-id': task.id,
      role: 'link',
      tabindex: '0',
      'aria-label': `打开任务 ${task.title || task.id}`
    },
    children: [
      element('div', {
        className: 'task-card__heading',
        children: [
          element('div', {
            className: 'task-card__identity',
            children: [
              element('small', { textContent: task.id }),
              element('h3', { className: 'task-card__title', textContent: task.title || '未命名任务' })
            ]
          }),
          element('span', {
            className: `badge badge--priority-${String(task.priority).toLowerCase()}`,
            textContent: priorityLabels[task.priority] ?? task.priority ?? '未设置'
          })
        ]
      }),
      element('p', {
        className: 'task-card__meta',
        textContent: `负责人：${task.assigneeId || '未分配'}`
      }),
      element('p', {
        className: 'task-card__meta',
        textContent: `截止：${formatDateTime(task.dueAt)}`
      }),
      element('span', {
        className: `task-card__status task-card__status--${String(task.status).toLowerCase().replace('_', '-')}`,
        textContent: statusLabels[task.status] ?? task.status
      })
    ]
  });

  card.addEventListener('click', (event) => {
    if (event.target.closest('button, a, select, input')) return;
    handlers.onOpen?.(task);
  });
  card.addEventListener('keydown', (event) => {
    if (event.target !== card || !['Enter', ' '].includes(event.key)) return;
    event.preventDefault();
    handlers.onOpen?.(task);
  });

  if (handlers.onMove) {
    const actions = element('div', { className: 'task-card__actions' });
    for (const [status, label] of moves[task.status] ?? []) {
      const button = element('button', {
        className: 'button button--ghost button--small',
        textContent: label,
        attributes: { type: 'button' }
      });
      button.addEventListener('click', () => handlers.onMove(task, status));
      actions.append(button);
    }
    card.append(actions);
  }

  card.addEventListener('dragstart', (event) => {
    event.dataTransfer?.setData('text/plain', task.id);
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
    card.classList.add('task-card--dragging');
    handlers.onDragStart?.(task);
  });
  card.addEventListener('dragend', () => {
    card.classList.remove('task-card--dragging');
    handlers.onDragEnd?.(task);
  });
  return card;
}
