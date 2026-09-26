import {
  acceptInvitation,
  listReceivedInvitations,
  rejectInvitation
} from '../api/invitation-api.js';
import { confirmAction } from '../components/confirm-dialog.js';
import { createBreadcrumb } from '../components/context-navigation.js';
import { createEmptyState } from '../components/loading.js';
import {
  createInternalLink,
  createStatusBadge,
  createTitleBlock,
  setButtonBusy
} from '../components/page-elements.js';
import { showToast } from '../components/toast.js';
import { clearElement, element } from '../utils/dom.js';
import { formatDateTime } from '../utils/format.js';

const roleLabels = { ADMIN: '管理员', MEMBER: '成员' };
const statusLabels = {
  PENDING: '待确认',
  ACCEPTED: '已接受',
  REJECTED: '已拒绝',
  REVOKED: '已撤销'
};

/** 当前用户以邀请记录为依据处理收到的团队邀请。 */
export async function renderInvitationsPage({ root, navigate, signal }) {
  let invitations = await listReceivedInvitations({ signal });

  const refresh = async () => {
    invitations = await listReceivedInvitations({ signal });
    render();
  };

  const render = () => {
    const pendingCount = invitations.filter((item) => item.status === 'PENDING').length;
    const list = element('div', { className: 'invitation-list' });
    if (invitations.length === 0) {
      list.append(createEmptyState('暂无团队邀请。'));
    }
    for (const invitation of invitations) {
      list.append(invitationCard(invitation));
    }
    clearElement(root).append(element('section', {
      className: 'page invitations-page',
      children: [
        createBreadcrumb([{ label: '团队邀请' }], navigate),
        createTitleBlock({
          eyebrow: '个人工作台',
          title: '团队邀请',
          description: '在这里确认或拒绝邀请；只有接受后才会成为团队成员。',
          status: element('span', { className: 'counter-pill', textContent: `${pendingCount} 条待处理` })
        }),
        list
      ]
    }));
  };

  const invitationCard = (invitation) => {
    const actions = [];
    if (invitation.status === 'PENDING') {
      const accept = button('接受邀请', 'button button--primary',
        () => void respond(invitation, 'accept', accept));
      const reject = button('拒绝', 'button button--danger',
        () => void respond(invitation, 'reject', reject));
      actions.push(accept, reject);
    } else if (invitation.status === 'ACCEPTED') {
      actions.push(createInternalLink(
        `/teams/${encodeURIComponent(invitation.teamId)}`,
        '进入团队', navigate, 'button button--secondary'
      ));
    }
    return element('article', {
      className: `invitation-card invitation-card--${invitation.status.toLowerCase()}`,
      children: [
        element('div', {
          className: 'invitation-card__content',
          children: [
            element('div', {
              className: 'invitation-item__heading',
              children: [
                element('h2', { textContent: invitation.teamName }),
                createStatusBadge(invitation.status, statusLabels)
              ]
            }),
            element('p', {
              textContent: `${invitation.inviterDisplayName} 邀请你以${roleLabels[invitation.role] ?? invitation.role}身份加入团队。`
            }),
            element('small', { className: 'muted', textContent: `发送于 ${formatDateTime(invitation.createdAt)} · ${invitation.id}` })
          ]
        }),
        actions.length ? element('div', { className: 'button-row', children: actions }) : null
      ]
    });
  };

  const respond = async (invitation, action, clickedButton) => {
    const accepting = action === 'accept';
    const confirmed = await confirmAction({
      title: accepting ? '接受团队邀请' : '拒绝团队邀请',
      message: accepting
        ? `确定以${roleLabels[invitation.role]}身份加入“${invitation.teamName}”吗？`
        : `确定拒绝“${invitation.teamName}”的邀请吗？`,
      confirmLabel: accepting ? '确认接受' : '确认拒绝',
      danger: !accepting
    });
    if (!confirmed) return;
    setButtonBusy(clickedButton, true, '处理中…');
    try {
      if (accepting) await acceptInvitation(invitation.id, { signal });
      else await rejectInvitation(invitation.id, { signal });
      showToast(accepting ? '已加入团队' : '已拒绝邀请', 'success');
      await refresh();
    } catch (error) {
      if (error?.name !== 'AbortError') {
        showToast(error.message || '处理邀请失败', 'error');
        await refresh().catch(() => {});
      }
    } finally {
      if (clickedButton.isConnected) setButtonBusy(clickedButton, false);
    }
  };

  render();
}

function button(label, className, onClick) {
  const node = element('button', {
    className,
    textContent: label,
    attributes: { type: 'button' }
  });
  node.addEventListener('click', onClick);
  return node;
}
