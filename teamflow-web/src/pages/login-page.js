import { login, register } from '../api/auth-api.js';
import { getCurrentUser } from '../api/user-api.js';
import { showToast } from '../components/toast.js';
import { createField, createPageHeader, setButtonBusy } from '../components/page-elements.js';
import { clearElement, element, formToObject } from '../utils/dom.js';

/** 登录/注册页。 */
export async function renderLoginPage({ root, authStore, navigate, searchParams, signal }) {
  const loginForm = createLoginForm();
  const registerForm = createRegisterForm();
  const page = element('section', {
    className: 'page auth-page',
    children: [
      createPageHeader({
        eyebrow: 'TEAMFLOW',
        title: '让协作保持同一方向。',
        description: '在一个工作区里组织团队、推进项目、跟进任务与消息。'
      }),
      element('div', {
        className: 'auth-grid',
        children: [
          formCard('登录', '使用用户名或邮箱登录。', loginForm),
          formCard('创建账号', '首次体验时先注册，随后会自动登录。', registerForm)
        ]
      })
    ]
  });
  clearElement(root).append(page);

  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = loginForm.querySelector('[type="submit"]');
    setButtonBusy(submit, true, '正在登录…');
    try {
      const values = formToObject(loginForm);
      const tokens = await login({ identifier: values.identifier.trim(), password: values.password }, { signal });
      const user = await establishSession(tokens, authStore, signal);
      showToast(`欢迎回来，${user.displayName || user.username}`, 'success');
      await navigate(safeNextPath(searchParams.get('next')));
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '登录失败', 'error');
    } finally {
      if (submit.isConnected) setButtonBusy(submit, false);
    }
  });

  registerForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = registerForm.querySelector('[type="submit"]');
    setButtonBusy(submit, true, '正在创建…');
    try {
      const values = formToObject(registerForm);
      if (values.password !== values.confirmPassword) {
        throw new Error('两次输入的密码不一致');
      }
      await register({
        username: values.username.trim(),
        email: values.email.trim(),
        password: values.password,
        displayName: values.displayName.trim()
      }, { signal });
      const tokens = await login({ identifier: values.username.trim(), password: values.password }, { signal });
      await establishSession(tokens, authStore, signal);
      showToast('账号创建成功', 'success');
      await navigate('/teams');
    } catch (error) {
      if (error?.name !== 'AbortError') showToast(error.message || '注册失败', 'error');
    } finally {
      if (submit.isConnected) setButtonBusy(submit, false);
    }
  });
}

async function establishSession(tokens, authStore, signal) {
  authStore.setSession(tokens, null, { notify: false });
  try {
    const user = await getCurrentUser({ signal });
    authStore.setSession(tokens, user);
    return user;
  } catch (error) {
    authStore.clear();
    throw error;
  }
}

function safeNextPath(value) {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '/teams';
  return value;
}

function formCard(title, description, form) {
  return element('article', {
    className: 'card auth-card',
    children: [
      element('h2', { textContent: title }),
      element('p', { className: 'muted', textContent: description }),
      form
    ]
  });
}

function createLoginForm() {
  return element('form', {
    className: 'form-stack',
    children: [
      createField('用户名或邮箱', input('identifier', 'text', {
        required: true, maxlength: 128, autocomplete: 'username', autofocus: true
      })),
      createField('密码', input('password', 'password', {
        required: true, maxlength: 72, autocomplete: 'current-password'
      })),
      submitButton('登录')
    ]
  });
}

function createRegisterForm() {
  return element('form', {
    className: 'form-stack',
    children: [
      createField('用户名', input('username', 'text', {
        required: true, minlength: 3, maxlength: 32, pattern: '[A-Za-z][A-Za-z0-9_]{2,31}', autocomplete: 'username'
      }), '以字母开头，可包含字母、数字和下划线。'),
      createField('邮箱', input('email', 'email', { required: true, maxlength: 128, autocomplete: 'email' })),
      createField('展示名称', input('displayName', 'text', { required: true, maxlength: 64, autocomplete: 'name' })),
      createField('密码', input('password', 'password', { required: true, minlength: 8, maxlength: 72, autocomplete: 'new-password' })),
      createField('确认密码', input('confirmPassword', 'password', { required: true, minlength: 8, maxlength: 72, autocomplete: 'new-password' })),
      submitButton('注册并登录')
    ]
  });
}

function input(name, type, attributes) {
  return element('input', { attributes: { name, type, ...attributes } });
}

function submitButton(label) {
  return element('button', {
    className: 'button button--primary button--block',
    textContent: label,
    attributes: { type: 'submit' }
  });
}
