import './styles/main.css';
import { createApp } from './app.js';
import { ping } from './api/system-api.js';
import { createErrorState } from './components/loading.js';
import { showToast } from './components/toast.js';

/**
 * 先渲染应用，再在后台探测后端，避免探活故障阻断登录页。
 */
async function bootstrap() {
  const root = document.querySelector('#app');
  const app = createApp(root);
  try {
    await app.start();
    void ping().then((probe) => {
      if (probe !== 'pong') {
        showToast('后端探活响应不符合约定', 'error');
      }
    }).catch((error) => {
      console.warn('后端探活暂时失败', error);
      showToast('后端暂时不可用，页面仍可重试', 'error');
    });
  } catch (error) {
    console.error('TeamFlow 启动失败', error);
    root?.replaceChildren(createErrorState(error, () => window.location.reload()));
  }
}

void bootstrap();
