import { createImportJob, getImportJob, startImportJob } from '../api/import-api.js';
import { getProject } from '../api/project-api.js';
import { listTeams } from '../api/team-api.js';
import { createBreadcrumb, createContextTabs, projectTabs } from '../components/context-navigation.js';
import { createField, createStatusBadge, createTitleBlock, setButtonBusy } from '../components/page-elements.js';
import { showToast } from '../components/toast.js';
import { clearElement, element } from '../utils/dom.js';
import { formatDateTime, formatFileSize } from '../utils/format.js';

const terminalStatuses = new Set(['COMPLETED', 'FAILED']);
const statusLabels = {
  PENDING: '等待启动',
  RUNNING: '正在导入',
  COMPLETED: '导入完成',
  FAILED: '导入失败'
};
const MAX_CSV_SIZE = 20 * 1024 * 1024;

/** CSV 批量导入页。 */
export async function renderImportPage({ root, params, navigate, signal }) {
  const [project, teams] = await Promise.all([
    getProject(params.projectId, { signal }),
    listTeams({ signal })
  ]);
  const team = teams.find((item) => item.id === project.teamId);
  let job = null;
  let pollTimer = null;
  let polling = false;

  const render = () => {
    const file = element('input', {
      attributes: { type: 'file', name: 'file', accept: '.csv,text/csv', required: true }
    });
    const createButton = element('button', {
      className: 'button button--primary',
      textContent: '上传并创建导入任务',
      attributes: { type: 'submit' }
    });
    const form = element('form', {
      className: 'card form-stack import-form',
      children: [
        element('h2', { textContent: '选择 CSV 文件' }),
        element('p', { className: 'muted', textContent: '上传 UTF-8 CSV，系统会先创建导入任务，由你确认后再开始处理。' }),
        createField('CSV 文件', file, `文件不能为空，最大 ${formatFileSize(MAX_CSV_SIZE)}。`),
        createButton,
        element('details', {
          className: 'help-details',
          children: [
            element('summary', { textContent: '查看 CSV 格式要求' }),
            element('p', { textContent: '表头：title, description, priority, assigneeEmail, dueAt。后端使用 Spring Batch 分块处理。' })
          ]
        })
      ]
    });
    createButton.disabled = Boolean(job && !terminalStatuses.has(job.status)) || project.status !== 'ACTIVE';
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const selected = file.files?.[0];
      setButtonBusy(createButton, true, '上传中…');
      try {
        validateCsv(selected);
        stopPolling();
        job = await createImportJob(project.id, selected, { signal });
        showToast('导入任务已创建，请确认后启动', 'success');
        render();
      } catch (error) {
        if (error?.name !== 'AbortError') showToast(error.message || '创建导入任务失败', 'error');
      } finally {
        if (createButton.isConnected) setButtonBusy(createButton, false);
      }
    });

    clearElement(root).append(element('section', {
      className: 'page import-page',
      children: [
        createBreadcrumb([
          { label: '团队', href: '/teams' },
          { label: team?.name || project.teamId, href: `/teams/${encodeURIComponent(project.teamId)}` },
          { label: project.name, href: `/projects/${encodeURIComponent(project.id)}/board` },
          { label: 'CSV 导入' }
        ], navigate),
        createTitleBlock({
          eyebrow: project.projectKey,
          title: 'CSV 批量导入',
          description: '按步骤上传文件、启动处理并查看真实导入结果。',
          status: createStatusBadge(project.status, { ACTIVE: '进行中', ARCHIVED: '已归档' })
        }),
        createContextTabs(projectTabs(project.id), 'import', navigate, '项目导航'),
        createImportSteps(job),
        project.status === 'ACTIVE' ? form : element('div', { className: 'notice', textContent: '项目已归档，不能创建新的导入任务。' }),
        job ? createJobPanel() : null
      ]
    }));
  };

  const createJobPanel = () => {
    const actions = [];
    if (job.status === 'PENDING') {
      const start = element('button', {
        className: 'button button--primary',
        textContent: '启动导入',
        attributes: { type: 'button' }
      });
      start.addEventListener('click', () => void startJob(start));
      actions.push(start);
    }
    if (job.status === 'RUNNING') {
      actions.push(element('span', { className: 'live-indicator', textContent: '正在自动刷新' }));
    }
    return element('article', {
      className: 'card import-status',
      children: [
        element('div', {
          className: 'section-heading',
          children: [
            element('div', { children: [element('h2', { textContent: '导入进度' }), element('span', { className: 'muted', textContent: job.id })] }),
            element('span', { className: `badge badge--${job.status.toLowerCase()}`, textContent: statusLabels[job.status] ?? job.status })
          ]
        }),
        element('div', {
          className: 'stat-grid',
          children: [stat('总行数', job.totalRows), stat('成功', job.successRows), stat('失败', job.failedRows)]
        }),
        element('dl', {
          className: 'meta-list meta-list--columns',
          children: [meta('创建时间', formatDateTime(job.createdAt)), meta('完成时间', formatDateTime(job.finishedAt))]
        }),
        actions.length ? element('div', { className: 'button-row', children: actions }) : null
      ]
    });
  };

  const startJob = async (button) => {
    if (!job || job.status !== 'PENDING') return;
    setButtonBusy(button, true, '启动中…');
    try {
      await startImportJob(job.id, { signal });
      job = { ...job, status: 'RUNNING' };
      showToast('导入任务已启动', 'success');
      render();
      schedulePoll(500);
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '启动导入失败', 'error');
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  const schedulePoll = (delay = 2000) => {
    if (signal.aborted || !job || terminalStatuses.has(job.status)) return;
    if (pollTimer != null) window.clearTimeout(pollTimer);
    pollTimer = window.setTimeout(() => {
      pollTimer = null;
      void poll();
    }, delay);
  };

  const poll = async () => {
    if (polling || signal.aborted || !job) return;
    polling = true;
    try {
      job = await getImportJob(job.id, { signal });
      render();
      if (!terminalStatuses.has(job.status)) {
        schedulePoll();
      } else {
        showToast(job.status === 'COMPLETED' ? 'CSV 导入完成' : 'CSV 导入失败', job.status === 'COMPLETED' ? 'success' : 'error');
      }
    } catch (error) {
      if (error?.name !== 'AbortError') {
        showToast('暂时无法刷新导入进度，将稍后重试', 'info');
        schedulePoll(5000);
      }
    } finally {
      polling = false;
    }
  };

  const stopPolling = () => {
    if (pollTimer != null) window.clearTimeout(pollTimer);
    pollTimer = null;
  };

  render();
  return stopPolling;
}

function createImportSteps(job) {
  const current = !job ? 1 : job.status === 'PENDING' ? 2 : job.status === 'RUNNING' ? 3 : 4;
  const labels = ['选择文件', '确认并启动', '处理数据', '查看结果'];
  return element('ol', {
    className: 'stepper',
    attributes: { 'aria-label': 'CSV 导入步骤' },
    children: labels.map((label, index) => element('li', {
      className: `stepper__item${index + 1 === current ? ' is-current' : ''}${index + 1 < current ? ' is-complete' : ''}`,
      attributes: { 'aria-current': index + 1 === current ? 'step' : null },
      children: [element('span', { textContent: String(index + 1) }), element('strong', { textContent: label })]
    }))
  });
}

function validateCsv(file) {
  if (!(file instanceof File)) throw new TypeError('请选择 CSV 文件');
  if (file.size === 0) throw new Error('CSV 文件不能为空');
  if (file.size > MAX_CSV_SIZE) throw new Error('CSV 文件不能超过 20 MB');
  if (!file.name.toLocaleLowerCase('en-US').endsWith('.csv')) throw new Error('文件扩展名必须是 .csv');
}

function stat(label, value) {
  return element('div', { className: 'stat-card', children: [element('span', { textContent: label }), element('strong', { textContent: String(value ?? 0) })] });
}

function meta(label, value) {
  return element('div', { children: [element('dt', { textContent: label }), element('dd', { textContent: value })] });
}
