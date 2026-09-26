import { getProjectDashboard } from '../api/dashboard-api.js';
import { getProject } from '../api/project-api.js';
import { listTeams } from '../api/team-api.js';
import { createBreadcrumb, createContextTabs, projectTabs } from '../components/context-navigation.js';
import { createEmptyState } from '../components/loading.js';
import { createStatusBadge, createTitleBlock, setButtonBusy } from '../components/page-elements.js';
import { showToast } from '../components/toast.js';
import { clearElement, element } from '../utils/dom.js';
import { formatDateTime } from '../utils/format.js';

const statusLabels = { TODO: '待办', IN_PROGRESS: '进行中', DONE: '已完成' };

/** 项目概览页。 */
export async function renderDashboardPage({ root, params, navigate, signal }) {
  let [project, dashboard, teams] = await Promise.all([
    getProject(params.projectId, { signal }),
    getProjectDashboard(params.projectId, { signal }),
    listTeams({ signal })
  ]);
  const team = teams.find((item) => item.id === project.teamId);

  const render = () => {
    const refresh = element('button', {
      className: 'button button--primary',
      textContent: '刷新统计',
      attributes: { type: 'button' }
    });
    refresh.addEventListener('click', () => void reload(refresh));

    const memberRows = Object.entries(dashboard.memberCompletedCounts ?? {})
      .sort(([left], [right]) => left.localeCompare(right));
    const members = memberRows.length ? element('table', {
      children: [
        element('thead', { children: [element('tr', { children: [element('th', { textContent: '成员编号' }), element('th', { textContent: '已完成任务' })] })] }),
        element('tbody', { children: memberRows.map(([userId, count]) => element('tr', { children: [element('td', { textContent: userId }), element('td', { textContent: String(count) })] })) })
      ]
    }) : createEmptyState('还没有成员完成任务');

    clearElement(root).append(element('section', {
      className: 'page dashboard-page',
      children: [
        createBreadcrumb([
          { label: '团队', href: '/teams' },
          { label: team?.name || project.teamId, href: `/teams/${encodeURIComponent(project.teamId)}` },
          { label: project.name, href: `/projects/${encodeURIComponent(project.id)}/board` },
          { label: '数据仪表盘' }
        ], navigate),
        createTitleBlock({
          eyebrow: project.projectKey,
          title: project.name,
          description: `统计快照生成于 ${formatDateTime(dashboard.generatedAt)}`,
          status: createStatusBadge(project.status, { ACTIVE: '进行中', ARCHIVED: '已归档' }),
          actions: [refresh]
        }),
        createContextTabs(projectTabs(project.id), 'dashboard', navigate, '项目导航'),
        element('div', {
          className: 'stat-grid stat-grid--dashboard',
          children: [
            stat('任务总数', dashboard.totalTasks, '全部可见任务'),
            ...['TODO', 'IN_PROGRESS', 'DONE'].map((status) => stat(
              statusLabels[status],
              dashboard.statusCounts?.[status] ?? 0,
              '占全部任务',
              dashboard.totalTasks
            )),
            stat('已逾期', dashboard.overdueTasks, '未完成且截止时间已过', dashboard.totalTasks)
          ]
        }),
        element('article', {
          className: 'card',
          children: [
            element('div', { className: 'section-heading', children: [element('h2', { textContent: '成员完成情况' }), element('span', { className: 'muted', textContent: `${memberRows.length} 位成员` })] }),
            element('div', { className: 'table-scroll table-scroll--bounded', children: [members] })
          ]
        })
      ]
    }));
  };

  const reload = async (button) => {
    setButtonBusy(button, true, '刷新中…');
    try {
      [project, dashboard] = await Promise.all([
        getProject(params.projectId, { signal }),
        getProjectDashboard(params.projectId, { signal })
      ]);
      showToast('仪表盘已刷新', 'success');
      render();
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '刷新仪表盘失败', 'error');
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  render();
}

function stat(label, value, description, total = null) {
  const numericValue = Number(value ?? 0);
  const percent = total > 0 ? Math.min(100, Math.round((numericValue / total) * 100)) : 0;
  const progressMax = Math.max(1, Number(total ?? 1));
  return element('article', {
    className: 'stat-card',
    children: [
      element('span', { textContent: label }),
      element('strong', { textContent: String(numericValue) }),
      element('small', { textContent: total == null ? description : `${description} · ${percent}%` }),
      total == null ? null : element('progress', {
        className: 'ratio-bar',
        attributes: { 'aria-label': label, max: String(progressMax), value: String(Math.min(numericValue, progressMax)) }
      })
    ]
  });
}
