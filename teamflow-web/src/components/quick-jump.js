import { listProjects } from '../api/project-api.js';
import { listTeams } from '../api/team-api.js';
import { createErrorState, createLoadingState } from './loading.js';
import { createIcon } from './icons.js';
import { clearElement, element } from '../utils/dom.js';

/** 打开只使用真实团队和项目接口的快速跳转面板。 */
export function openQuickJump({ navigate, onClose }) {
  const abortController = new AbortController();
  const previousFocus = document.activeElement;
  const input = element('input', {
    className: 'quick-jump__input',
    attributes: {
      type: 'search',
      placeholder: '筛选已加载的团队或项目…',
      'aria-label': '快速跳转筛选',
      autocomplete: 'off'
    }
  });
  const back = element('button', {
    className: 'quick-jump__back',
    textContent: '返回团队',
    attributes: { type: 'button', hidden: true }
  });
  const resultHost = element('div', {
    className: 'quick-jump__results',
    attributes: { role: 'listbox', 'aria-label': '快速跳转结果' }
  });
  const dialog = element('section', {
    className: 'quick-jump',
    attributes: { role: 'dialog', 'aria-modal': 'true', 'aria-label': '快速跳转' },
    children: [
      element('header', {
        className: 'quick-jump__header',
        children: [createIcon('search'), input, element('kbd', { textContent: 'Esc' })]
      }),
      back,
      resultHost,
      element('footer', {
        className: 'quick-jump__footer',
        children: [
          element('span', { textContent: '↑↓ 选择' }),
          element('span', { textContent: 'Enter 进入' })
        ]
      })
    ]
  });
  const overlay = element('div', { className: 'quick-jump-overlay', children: [dialog] });
  document.body.append(overlay);

  let teams = [];
  let projects = [];
  let selectedTeam = null;
  let items = [];
  let activeIndex = 0;
  let closed = false;

  const close = () => {
    if (closed) return;
    closed = true;
    abortController.abort();
    document.removeEventListener('keydown', onKeyDown);
    overlay.remove();
    if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    onClose?.();
  };

  const openPath = async (path) => {
    close();
    await navigate(path);
  };

  const filteredItems = () => {
    const needle = input.value.trim().toLocaleLowerCase('zh-CN');
    return items.filter((item) => !needle || `${item.label} ${item.detail ?? ''}`
      .toLocaleLowerCase('zh-CN')
      .includes(needle));
  };

  const renderItems = () => {
    const visible = filteredItems();
    activeIndex = Math.min(activeIndex, Math.max(0, visible.length - 1));
    clearElement(resultHost);
    if (visible.length === 0) {
      resultHost.append(element('p', {
        className: 'quick-jump__empty',
        textContent: '已加载的内容中没有匹配项'
      }));
      return;
    }
    visible.forEach((item, index) => {
      const button = element('button', {
        className: `quick-jump__item${index === activeIndex ? ' is-active' : ''}`,
        attributes: {
          type: 'button',
          role: 'option',
          'aria-selected': index === activeIndex,
          'data-index': index
        },
        children: [
          element('span', {
            className: 'quick-jump__item-icon',
            children: [createIcon(item.kind === 'team' ? 'teams' : item.kind === 'project' ? 'folder' : 'arrow')]
          }),
          element('span', {
            className: 'quick-jump__item-copy',
            children: [
              element('strong', { textContent: item.label }),
              item.detail ? element('small', { textContent: item.detail }) : null
            ]
          }),
          element('span', { className: 'quick-jump__enter', textContent: '↵' })
        ]
      });
      button.addEventListener('mouseenter', () => {
        activeIndex = index;
        renderItems();
      });
      button.addEventListener('click', () => void item.activate());
      resultHost.append(button);
    });
  };

  const showTeams = () => {
    selectedTeam = null;
    projects = [];
    back.hidden = true;
    input.value = '';
    input.placeholder = '按名称筛选已加载的团队…';
    items = teams.map((team) => ({
      kind: 'team',
      label: team.name,
      detail: team.currentUserRole || '团队',
      activate: () => void showProjects(team)
    }));
    activeIndex = 0;
    renderItems();
  };

  const showProjects = async (team) => {
    selectedTeam = team;
    back.hidden = false;
    input.value = '';
    input.placeholder = `筛选“${team.name}”的项目…`;
    clearElement(resultHost).append(createLoadingState('正在加载项目…'));
    try {
      projects = await listProjects(team.id, { signal: abortController.signal });
      items = [
        {
          kind: 'link',
          label: '打开团队概览',
          detail: team.name,
          activate: () => void openPath(`/teams/${encodeURIComponent(team.id)}`)
        },
        ...projects.map((project) => ({
          kind: 'project',
          label: project.name,
          detail: `${project.projectKey} · ${project.status === 'ACTIVE' ? '进行中' : '已归档'}`,
          activate: () => void openPath(`/projects/${encodeURIComponent(project.id)}/board`)
        }))
      ];
      activeIndex = 0;
      renderItems();
    } catch (error) {
      if (error?.name === 'AbortError') return;
      clearElement(resultHost).append(createErrorState(error, () => void showProjects(team)));
    }
  };

  const onKeyDown = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
      return;
    }
    if (!['ArrowDown', 'ArrowUp', 'Enter'].includes(event.key)) return;
    const visible = filteredItems();
    if (visible.length === 0) return;
    event.preventDefault();
    if (event.key === 'ArrowDown') activeIndex = (activeIndex + 1) % visible.length;
    if (event.key === 'ArrowUp') activeIndex = (activeIndex - 1 + visible.length) % visible.length;
    if (event.key === 'Enter') {
      void visible[activeIndex]?.activate();
      return;
    }
    renderItems();
    resultHost.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' });
  };

  input.addEventListener('input', () => {
    activeIndex = 0;
    renderItems();
  });
  back.addEventListener('click', showTeams);
  overlay.addEventListener('mousedown', (event) => {
    if (event.target === overlay) close();
  });
  document.addEventListener('keydown', onKeyDown);

  clearElement(resultHost).append(createLoadingState('正在加载团队…'));
  queueMicrotask(() => input.focus());
  void listTeams({ signal: abortController.signal })
    .then((loadedTeams) => {
      teams = loadedTeams;
      showTeams();
    })
    .catch((error) => {
      if (error?.name !== 'AbortError') {
        clearElement(resultHost).append(createErrorState(error, () => {
          close();
        }));
      }
    });

  return close;
}
