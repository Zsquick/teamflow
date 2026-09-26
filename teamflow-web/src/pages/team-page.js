import {
  createTeam,
  listTeamInvitations,
  listTeamMembers,
  listTeams,
  previewTeamInvitation,
  removeTeamMember,
  revokeTeamInvitation,
  sendTeamInvitation,
  updateTeamMemberRole
} from '../api/team-api.js';
import { createProject, listProjects, updateProject } from '../api/project-api.js';
import { confirmAction } from '../components/confirm-dialog.js';
import { createBreadcrumb, createContextTabs, teamTabs } from '../components/context-navigation.js';
import { createEmptyState, createErrorState } from '../components/loading.js';
import { openModal } from '../components/modal.js';
import {
  createField,
  createInternalLink,
  createStatusBadge,
  createTitleBlock,
  optionalText,
  setButtonBusy
} from '../components/page-elements.js';
import { showToast } from '../components/toast.js';
import { clearElement, element, formToObject } from '../utils/dom.js';
import { formatDateTime } from '../utils/format.js';

const roleLabels = { OWNER: '所有者', ADMIN: '管理员', MEMBER: '成员' };
const roleRanks = { OWNER: 300, ADMIN: 200, MEMBER: 100 };
const invitationStatusLabels = {
  PENDING: '待确认',
  ACCEPTED: '已接受',
  REJECTED: '已拒绝',
  REVOKED: '已撤销'
};
const validTabs = new Set(['overview', 'projects', 'members']);

/** 团队列表与团队上下文页面。 */
export async function renderTeamPage({ root, params, searchParams, authStore, navigate, signal }) {
  let teams = await listTeams({ signal });
  let selectedTeam = params.teamId
    ? teams.find((team) => team.id === params.teamId)
    : null;
  let members = [];
  let projects = [];
  let sentInvitations = [];
  let activeModalClose = null;
  let teamSearch = '';
  const requestedTab = searchParams.get('tab') || 'overview';
  const activeTab = validTabs.has(requestedTab) ? requestedTab : 'overview';

  if (params.teamId && !selectedTeam) {
    clearElement(root).append(createErrorState(
      new Error('团队不存在或你无权访问'),
      () => void navigate('/teams')
    ));
    return;
  }

  const canAdmin = Boolean(selectedTeam
    && ['OWNER', 'ADMIN'].includes(selectedTeam.currentUserRole));
  if (selectedTeam) {
    [members, projects, sentInvitations] = await Promise.all([
      listTeamMembers(selectedTeam.id, { signal }),
      listProjects(selectedTeam.id, { signal }),
      canAdmin ? listTeamInvitations(selectedTeam.id, { signal }) : Promise.resolve([])
    ]);
  }

  const render = () => {
    if (!selectedTeam) renderTeamDirectory();
    else renderTeamWorkspace();
  };

  const renderTeamDirectory = () => {
    const createButton = actionButton('创建团队', 'button button--primary', openCreateTeamModal);
    const search = element('input', {
      attributes: {
        type: 'search',
        value: teamSearch,
        placeholder: '按团队名称或描述筛选',
        'aria-label': '筛选团队'
      }
    });
    const listHost = element('div', { className: 'directory-list' });
    const renderList = () => {
      const needle = teamSearch.trim().toLocaleLowerCase('zh-CN');
      const visible = teams.filter((team) => !needle || [team.name, team.description, team.id]
        .filter(Boolean)
        .some((value) => String(value).toLocaleLowerCase('zh-CN').includes(needle)));
      clearElement(listHost);
      if (visible.length === 0) {
        listHost.append(createEmptyState(
          teams.length === 0
            ? '还没有团队，使用右上角“创建团队”开始协作。'
            : '已加载的团队中没有匹配项。'
        ));
        return;
      }
      for (const team of visible) listHost.append(createTeamDirectoryItem(team));
    };
    search.addEventListener('input', () => {
      teamSearch = search.value;
      renderList();
    });
    renderList();

    clearElement(root).append(element('section', {
      className: 'page team-directory-page',
      children: [
        createBreadcrumb([{ label: '团队' }], navigate),
        createTitleBlock({
          eyebrow: '全局工作区',
          title: '团队',
          description: '选择团队进入项目与成员协作空间。',
          actions: [createButton]
        }),
        element('section', {
          className: 'directory-toolbar',
          children: [search, element('span', { className: 'muted', textContent: `共 ${teams.length} 个团队` })]
        }),
        listHost
      ]
    }));
  };

  const createTeamDirectoryItem = (team) => {
    const path = `/teams/${encodeURIComponent(team.id)}`;
    const card = element('article', {
      className: 'directory-item',
      children: [
        element('div', {
          className: 'directory-item__main',
          children: [
            element('div', {
              className: 'directory-item__heading',
              children: [
                createInternalLink(path, team.name, navigate, 'directory-item__title'),
                element('span', { className: 'badge', textContent: roleLabels[team.currentUserRole] ?? team.currentUserRole })
              ]
            }),
            element('p', { className: 'muted line-clamp-2', textContent: team.description || '暂无团队描述' }),
            element('small', { className: 'directory-item__meta', textContent: `${team.id} · 进入后可查看项目和成员` })
          ]
        }),
        createInternalLink(path, '进入团队', navigate, 'button button--secondary')
      ]
    });
    return makeNavigableCard(card, path, navigate, team.name);
  };

  const renderTeamWorkspace = () => {
    const action = primaryTeamAction(canAdmin);
    const tabLabel = { overview: '概览', projects: '项目', members: '成员' }[activeTab];
    const content = activeTab === 'projects'
      ? projectsPanel(canAdmin)
      : activeTab === 'members'
        ? membersPanel(canAdmin)
        : overviewPanel();

    clearElement(root).append(element('section', {
      className: 'page team-workspace-page',
      children: [
        createBreadcrumb([
          { label: '团队', href: '/teams' },
          activeTab === 'overview' ? { label: selectedTeam.name } : { label: selectedTeam.name, href: `/teams/${encodeURIComponent(selectedTeam.id)}` },
          activeTab === 'overview' ? null : { label: tabLabel }
        ], navigate),
        createTitleBlock({
          eyebrow: selectedTeam.id,
          title: selectedTeam.name,
          description: selectedTeam.description || '这个团队还没有填写描述。',
          status: element('span', { className: 'badge', textContent: roleLabels[selectedTeam.currentUserRole] ?? selectedTeam.currentUserRole }),
          actions: action ? [action] : []
        }),
        createContextTabs(teamTabs(selectedTeam.id, activeTab), activeTab, navigate, '团队导航'),
        content
      ]
    }));
  };

  const primaryTeamAction = (canAdmin) => {
    if (activeTab === 'projects' && canAdmin) return actionButton('创建项目', 'button button--primary', openCreateProjectModal);
    if (activeTab === 'members' && canAdmin) return actionButton('邀请成员', 'button button--primary', openInviteMemberModal);
    if (activeTab === 'overview') {
      return createInternalLink(`/teams/${encodeURIComponent(selectedTeam.id)}?tab=projects`, '查看项目', navigate, 'button button--primary');
    }
    return null;
  };

  const overviewPanel = () => element('div', {
    className: 'overview-layout',
    children: [
      element('section', {
        className: 'summary-strip',
        children: [
          summaryMetric('项目', projects.length, '当前团队项目数'),
          summaryMetric('成员', members.length, '已加入团队'),
          summaryMetric('我的角色', roleLabels[selectedTeam.currentUserRole] ?? selectedTeam.currentUserRole, '当前权限级别')
        ]
      }),
      element('section', {
        className: 'card overview-card',
        children: [
          element('div', {
            children: [
              element('p', { className: 'eyebrow', textContent: '团队概览' }),
              element('h2', { textContent: '快速进入协作内容' }),
              element('p', { className: 'muted', textContent: '项目和成员使用独立页签，避免在同一长页面中反复查找。' })
            ]
          }),
          element('div', {
            className: 'shortcut-grid',
            children: [
              shortcut('打开项目', `${projects.length} 个项目`, `/teams/${encodeURIComponent(selectedTeam.id)}?tab=projects`),
              shortcut('查看成员', `${members.length} 位成员`, `/teams/${encodeURIComponent(selectedTeam.id)}?tab=members`)
            ]
          })
        ]
      }),
      element('dl', {
        className: 'card meta-list meta-list--columns',
        children: [meta('团队编号', selectedTeam.id), meta('创建时间', formatDateTime(selectedTeam.createdAt))]
      })
    ]
  });

  const shortcut = (title, detail, href) => element('article', {
    className: 'shortcut-card',
    children: [
      createInternalLink(href, title, navigate, 'shortcut-card__link'),
      element('span', { className: 'muted', textContent: detail })
    ]
  });

  const projectsPanel = (canAdmin) => {
    const grid = element('div', { className: 'project-directory' });
    if (projects.length === 0) {
      grid.append(createEmptyState(canAdmin
        ? '还没有项目，使用右上角“创建项目”开始。'
        : '这个团队还没有项目。'));
    } else {
      projects.forEach((project) => grid.append(projectItem(project, canAdmin)));
    }
    return element('section', {
      className: 'bounded-section',
      children: [
        element('div', {
          className: 'section-heading',
          children: [element('div', { children: [element('h2', { textContent: '项目' }), element('span', { className: 'muted', textContent: `${projects.length} 个` })] })]
        }),
        grid
      ]
    });
  };

  const projectItem = (project, canAdmin) => {
    const boardPath = `/projects/${encodeURIComponent(project.id)}/board`;
    const actions = [
      createInternalLink(boardPath, '任务看板', navigate, 'button button--secondary button--small'),
      createInternalLink(`/projects/${encodeURIComponent(project.id)}/dashboard`, '仪表盘', navigate, 'button button--ghost button--small'),
      createInternalLink(`/projects/${encodeURIComponent(project.id)}/import`, 'CSV 导入', navigate, 'button button--ghost button--small')
    ];
    if (canAdmin && project.status === 'ACTIVE') {
      actions.push(actionButton('项目设置', 'button button--ghost button--small', () => openEditProjectModal(project)));
    }
    const card = element('article', {
      className: 'project-row',
      children: [
        element('div', {
          className: 'project-row__body',
          children: [
            element('div', {
              className: 'project-row__heading',
              children: [
                element('div', { children: [element('small', { textContent: project.projectKey }), createInternalLink(boardPath, project.name, navigate, 'project-row__title')] }),
                createStatusBadge(project.status, { ACTIVE: '进行中', ARCHIVED: '已归档' })
              ]
            }),
            element('p', { className: 'muted line-clamp-2', textContent: project.description || '暂无项目描述' })
          ]
        }),
        element('div', { className: 'button-row', children: actions })
      ]
    });
    return makeNavigableCard(card, boardPath, navigate, project.name);
  };

  const membersPanel = (canAdmin) => {
    const tbody = element('tbody');
    for (const member of members) tbody.append(memberRow(member, canAdmin));
    const memberSection = element('section', {
      className: 'bounded-section',
      children: [
        element('div', {
          className: 'section-heading',
          children: [element('div', { children: [element('h2', { textContent: '成员' }), element('span', { className: 'muted', textContent: `${members.length} 人` })] })]
        }),
        element('div', {
          className: 'table-scroll table-scroll--bounded',
          children: [element('table', {
            className: 'member-table',
            children: [
              element('thead', { children: [element('tr', { children: ['成员', '角色', '加入时间', '管理操作'].map((label) => element('th', { textContent: label })) })] }),
              tbody
            ]
          })]
        })
      ]
    });
    if (!canAdmin) return memberSection;
    return element('div', {
      className: 'stack-layout',
      children: [memberSection, sentInvitationsPanel()]
    });
  };

  const sentInvitationsPanel = () => {
    const list = element('div', { className: 'invitation-list' });
    if (sentInvitations.length === 0) {
      list.append(createEmptyState('还没有发送过团队邀请。'));
    }
    for (const invitation of sentInvitations) {
      const actions = [];
      if (invitation.status === 'PENDING') {
        const revoke = actionButton('撤销邀请', 'button button--danger button--small',
          () => void revokeInvitation(invitation, revoke));
        actions.push(revoke);
      }
      list.append(element('article', {
        className: 'invitation-item',
        children: [
          element('div', {
            className: 'invitation-item__body',
            children: [
              element('div', {
                className: 'invitation-item__heading',
                children: [
                  element('strong', { textContent: invitation.inviteeDisplayName }),
                  createStatusBadge(invitation.status, invitationStatusLabels)
                ]
              }),
              element('p', { className: 'muted', textContent: `${invitation.maskedInviteeUsername} · ${invitation.maskedInviteeEmail}` }),
              element('small', { textContent: `${roleLabels[invitation.role]} · ${formatDateTime(invitation.createdAt)}` })
            ]
          }),
          actions.length ? element('div', { className: 'button-row', children: actions }) : null
        ]
      }));
    }
    return element('section', {
      className: 'bounded-section',
      children: [
        element('div', {
          className: 'section-heading',
          children: [element('div', {
            children: [element('h2', { textContent: '成员邀请' }), element('span', { className: 'muted', textContent: `${sentInvitations.length} 条` })]
          })]
        }),
        list
      ]
    });
  };

  const memberRow = (member, canAdmin) => {
    const actions = element('div', { className: 'button-row' });
    const canManageTarget = canAdmin
      && member.userId !== authStore.currentUser.id
      && roleRanks[selectedTeam.currentUserRole] > roleRanks[member.role];
    if (canManageTarget && selectedTeam.currentUserRole === 'OWNER') {
      const roleSelect = element('select', { attributes: { 'aria-label': `修改 ${member.displayName || member.username} 的角色` } });
      for (const role of ['ADMIN', 'MEMBER']) roleSelect.append(element('option', { textContent: roleLabels[role], attributes: { value: role } }));
      roleSelect.value = member.role;
      const save = actionButton('保存角色', 'button button--secondary button--small', () => void changeRole(member, roleSelect.value, save));
      actions.append(roleSelect, save);
    }
    if (canManageTarget) {
      const remove = actionButton('移除', 'button button--danger button--small', () => void removeMember(member, remove));
      actions.append(remove);
    }
    return element('tr', {
      children: [
        element('td', { children: [element('strong', { textContent: member.displayName || member.username }), element('small', { textContent: `@${member.username} · ${member.userId}` })] }),
        element('td', { children: [element('span', { className: 'badge', textContent: roleLabels[member.role] ?? member.role })] }),
        element('td', { textContent: formatDateTime(member.joinedAt) }),
        element('td', { children: [actions.childElementCount ? actions : element('span', { className: 'muted', textContent: '无可用操作' })] })
      ]
    });
  };

  const refreshDetails = async () => {
    [members, projects, sentInvitations] = await Promise.all([
      listTeamMembers(selectedTeam.id, { signal }),
      listProjects(selectedTeam.id, { signal }),
      canAdmin ? listTeamInvitations(selectedTeam.id, { signal }) : Promise.resolve([])
    ]);
    render();
  };

  function openCreateTeamModal() {
    const form = modalForm([
      createField('团队名称', element('input', { attributes: { name: 'name', required: true, maxlength: 64, autofocus: true } })),
      createField('团队描述', element('textarea', { attributes: { name: 'description', rows: 4, maxlength: 500 } }))
    ], '创建团队');
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = form.querySelector('[type="submit"]');
      setButtonBusy(button, true, '创建中…');
      try {
        const values = formToObject(form);
        const team = await createTeam({ name: values.name.trim(), description: optionalText(values.description) }, { signal });
        teams = [...teams, team];
        activeModalClose?.();
        showToast('团队创建成功', 'success');
        await navigate(`/teams/${encodeURIComponent(team.id)}`);
      } catch (error) {
        if (error?.name !== 'AbortError') showFormError(form, error, '创建团队失败');
      } finally {
        if (button.isConnected) setButtonBusy(button, false);
      }
    });
    activeModalClose = openModal({ title: '创建团队', content: form, onClose: () => { activeModalClose = null; } });
  }

  function openInviteMemberModal() {
    let preview = null;
    let previewIdentifier = '';
    const identifier = element('input', {
      attributes: {
        name: 'identifier', required: true, maxlength: 128,
        placeholder: '完整用户名或邮箱', autofocus: true,
        autocomplete: 'off'
      }
    });
    const role = element('select', {
      attributes: { name: 'role', required: true, disabled: true }
    });
    const previewHost = element('div', { className: 'invitation-preview', attributes: { hidden: true } });
    const lookup = actionButton('查找用户', 'button button--secondary', () => void lookupUser());
    const send = element('button', {
      className: 'button button--primary', textContent: '发送邀请',
      attributes: { type: 'submit', disabled: true }
    });
    const form = element('form', {
      className: 'form-stack modal-form',
      children: [
        element('div', { className: 'form-error', attributes: { role: 'alert', hidden: true } }),
        createField('用户名或邮箱', identifier, '仅支持完整用户名或完整邮箱精确查找。'),
        previewHost,
        createField('团队角色', role),
        element('div', { className: 'button-row button-row--end', children: [lookup, send] })
      ]
    });
    const resetPreview = () => {
      preview = null;
      previewIdentifier = '';
      previewHost.hidden = true;
      previewHost.replaceChildren();
      role.replaceChildren();
      role.disabled = true;
      send.disabled = true;
    };
    identifier.addEventListener('input', resetPreview);

    async function lookupUser() {
      if (!identifier.reportValidity()) return;
      setButtonBusy(lookup, true, '查找中…');
      resetFormError(form);
      try {
        const requestedIdentifier = identifier.value.trim();
        const result = await previewTeamInvitation(
          selectedTeam.id,
          { identifier: requestedIdentifier },
          { signal }
        );
        if (identifier.value.trim() !== requestedIdentifier) return;
        previewIdentifier = requestedIdentifier;
        preview = result;
        role.replaceChildren(...preview.assignableRoles.map((value) =>
          element('option', { textContent: roleLabels[value], attributes: { value } })));
        role.disabled = false;
        send.disabled = false;
        previewHost.hidden = false;
        previewHost.replaceChildren(
          element('p', { className: 'eyebrow', textContent: '查找结果' }),
          element('strong', { textContent: preview.displayName }),
          element('span', { textContent: preview.maskedUsername }),
          element('span', { textContent: preview.maskedEmail })
        );
      } catch (error) {
        resetPreview();
        if (error?.name !== 'AbortError') showFormError(form, error, '查找用户失败');
      } finally {
        if (lookup.isConnected) setButtonBusy(lookup, false);
      }
    }

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const currentIdentifier = identifier.value.trim();
      if (!preview || currentIdentifier !== previewIdentifier) {
        showFormError(form, null, '请重新查找并确认目标用户');
        return;
      }
      const confirmed = await confirmAction({
        title: '确认发送团队邀请',
        message: `确定邀请“${preview.displayName}”（${preview.maskedEmail}）以${roleLabels[role.value]}身份加入“${selectedTeam.name}”吗？`,
        confirmLabel: '确认发送'
      });
      if (!confirmed) return;
      setButtonBusy(send, true, '发送中…');
      try {
        await sendTeamInvitation(selectedTeam.id, {
          identifier: currentIdentifier,
          role: role.value
        }, { signal });
        activeModalClose?.();
        showToast('团队邀请已发送，对方接受后才会加入团队', 'success');
        await refreshDetails();
      } catch (error) {
        if (error?.name !== 'AbortError') {
          resetPreview();
          showFormError(form, error, '发送邀请失败，请重新查找后再试');
        }
      } finally {
        if (send.isConnected) {
          setButtonBusy(send, false);
          send.disabled = !preview;
        }
      }
    });
    activeModalClose = openModal({ title: '邀请团队成员', content: form, onClose: () => { activeModalClose = null; } });
  }

  const revokeInvitation = async (invitation, button) => {
    const confirmed = await confirmAction({
      title: '撤销团队邀请',
      message: `确定撤销发送给“${invitation.inviteeDisplayName}”的邀请吗？`,
      confirmLabel: '确认撤销',
      danger: true
    });
    if (!confirmed) return;
    setButtonBusy(button, true, '撤销中…');
    try {
      await revokeTeamInvitation(selectedTeam.id, invitation.id, { signal });
      showToast('邀请已撤销', 'success');
      await refreshDetails();
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '撤销邀请失败', 'error');
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  const changeRole = async (member, role, button) => {
    if (role === member.role) return;
    setButtonBusy(button, true, '保存中…');
    try {
      await updateTeamMemberRole(selectedTeam.id, member.userId, { role }, { signal });
      showToast('成员角色已更新', 'success');
      await refreshDetails();
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '更新角色失败', 'error');
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  const removeMember = async (member, button) => {
    const confirmed = await confirmAction({
      title: '移除团队成员',
      message: `确定将“${member.displayName || member.username}”移出当前团队吗？`,
      confirmLabel: '确认移除',
      danger: true
    });
    if (!confirmed) return;
    setButtonBusy(button, true, '移除中…');
    try {
      await removeTeamMember(selectedTeam.id, member.userId, { signal });
      showToast('成员已移除', 'success');
      await refreshDetails();
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '移除成员失败', 'error');
    } finally {
      if (button.isConnected) setButtonBusy(button, false);
    }
  };

  function openCreateProjectModal() {
    const form = modalForm([
      createField('项目名称', element('input', { attributes: { name: 'name', required: true, maxlength: 100, autofocus: true } })),
      createField('项目短标识', element('input', { attributes: { name: 'projectKey', required: true, minlength: 2, maxlength: 16, pattern: '[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*', placeholder: 'TEAM-WEB' } }), '以字母开头，可包含字母、数字和连字号。'),
      createField('项目描述', element('textarea', { attributes: { name: 'description', rows: 4, maxlength: 1000 } }))
    ], '创建项目');
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = form.querySelector('[type="submit"]');
      setButtonBusy(button, true, '创建中…');
      try {
        const values = formToObject(form);
        await createProject({
          teamId: selectedTeam.id,
          name: values.name.trim(),
          projectKey: values.projectKey.trim(),
          description: optionalText(values.description)
        }, { signal });
        activeModalClose?.();
        showToast('项目创建成功', 'success');
        await refreshDetails();
      } catch (error) {
        if (error?.name !== 'AbortError') showFormError(form, error, '创建项目失败');
      } finally {
        if (button.isConnected) setButtonBusy(button, false);
      }
    });
    activeModalClose = openModal({ title: '创建项目', content: form, onClose: () => { activeModalClose = null; } });
  }

  const openEditProjectModal = (project) => {
    const status = element('select', { attributes: { name: 'status', required: true } });
    status.append(
      element('option', { textContent: '进行中', attributes: { value: 'ACTIVE' } }),
      element('option', { textContent: '归档（不可恢复）', attributes: { value: 'ARCHIVED' } })
    );
    status.value = project.status;
    const name = element('input', { attributes: { name: 'name', value: project.name, required: true, maxlength: 100, autofocus: true } });
    const description = element('textarea', { attributes: { name: 'description', rows: 4, maxlength: 1000 } });
    description.value = project.description ?? '';
    const form = modalForm([
      createField('项目名称', name),
      createField('项目描述', description),
      createField('项目状态', status, '归档后项目只读，请谨慎操作。')
    ], '保存设置');
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = form.querySelector('[type="submit"]');
      setButtonBusy(button, true, '保存中…');
      try {
        const values = formToObject(form);
        await updateProject(project.id, {
          name: values.name.trim(),
          description: optionalText(values.description),
          status: values.status,
          version: project.version
        }, { signal });
        activeModalClose?.();
        showToast('项目设置已保存', 'success');
        await refreshDetails();
      } catch (error) {
        if (error?.status === 409) {
          activeModalClose?.();
          showToast('项目已被其他成员修改，已重新读取', 'info');
          await refreshDetails();
        } else if (error?.name !== 'AbortError') {
          showFormError(form, error, '保存项目失败');
        }
      } finally {
        if (button.isConnected) setButtonBusy(button, false);
      }
    });
    activeModalClose = openModal({ title: '项目设置', content: form, onClose: () => { activeModalClose = null; } });
  };

  render();
  return () => activeModalClose?.();

  function summaryMetric(label, value, hint) {
    return element('article', {
      className: 'summary-metric',
      children: [element('span', { textContent: label }), element('strong', { textContent: String(value) }), element('small', { textContent: hint })]
    });
  }

  function meta(label, value) {
    return element('div', { children: [element('dt', { textContent: label }), element('dd', { textContent: value || '--' })] });
  }

}

function actionButton(label, className, action) {
  const button = element('button', { className, textContent: label, attributes: { type: 'button' } });
  button.addEventListener('click', action);
  return button;
}

function modalForm(fields, submitLabel) {
  return element('form', {
    className: 'form-stack modal-form',
    children: [
      element('div', { className: 'form-error', attributes: { role: 'alert', hidden: true } }),
      ...fields,
      element('div', {
        className: 'button-row button-row--end',
        children: [element('button', { className: 'button button--primary', textContent: submitLabel, attributes: { type: 'submit' } })]
      })
    ]
  });
}

function showFormError(form, error, fallback) {
  const node = form.querySelector('.form-error');
  if (node) {
    node.hidden = false;
    node.textContent = error?.message || fallback;
  }
  showToast(error?.message || fallback, 'error');
}

function resetFormError(form) {
  const node = form.querySelector('.form-error');
  if (!node) return;
  node.hidden = true;
  node.textContent = '';
}

function makeNavigableCard(card, path, navigate, label) {
  card.setAttribute('role', 'link');
  card.setAttribute('tabindex', '0');
  card.setAttribute('aria-label', `进入${label}`);
  card.addEventListener('click', (event) => {
    if (event.target.closest('a, button, input, select, textarea, summary')) return;
    void navigate(path);
  });
  card.addEventListener('keydown', (event) => {
    if (event.target !== card || !['Enter', ' '].includes(event.key)) return;
    event.preventDefault();
    void navigate(path);
  });
  return card;
}
