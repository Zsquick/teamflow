/** 清空节点。 */
export function clearElement(element) {
  if (!element || typeof element.replaceChildren !== 'function') {
    throw new TypeError('需要提供可清空的 DOM 节点');
  }
  element.replaceChildren();
  return element;
}

/** 用文本创建元素，避免 innerHTML 引入 XSS。 */
export function element(tagName, options = {}) {
  if (typeof tagName !== 'string' || !/^[a-z][a-z0-9-]*$/i.test(tagName)) {
    throw new TypeError('无效的元素标签名');
  }

  const allowedOptions = new Set([
    'className',
    'textContent',
    'attributes',
    'children'
  ]);
  for (const key of Object.keys(options)) {
    if (!allowedOptions.has(key)) {
      throw new TypeError(`不支持的元素选项：${key}`);
    }
  }

  const node = document.createElement(tagName);
  if (options.className != null) {
    node.className = String(options.className);
  }
  if (options.textContent != null) {
    node.textContent = String(options.textContent);
  }

  for (const [name, value] of Object.entries(options.attributes ?? {})) {
    if (/^on/i.test(name)) {
      throw new TypeError('事件必须使用 addEventListener 注册');
    }
    if (value == null || value === false) {
      continue;
    }
    node.setAttribute(name, value === true ? '' : String(value));
  }

  const children = options.children == null
    ? []
    : Array.isArray(options.children)
      ? options.children
      : [options.children];
  for (const child of children.flat(Infinity)) {
    if (child == null || child === false) {
      continue;
    }
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

/** 表单转普通对象；同名多值字段需单独处理。 */
export function formToObject(form) {
  if (!(form instanceof HTMLFormElement)) {
    throw new TypeError('需要提供 HTMLFormElement');
  }

  const data = new FormData(form);
  const result = {};
  const controls = Array.from(form.elements).filter((control) => control.name);
  const names = new Set(controls.map((control) => control.name));

  for (const name of names) {
    const namedControls = controls.filter((control) => control.name === name);
    const values = data.getAll(name);
    const first = namedControls[0];
    const isCheckboxGroup = namedControls.every(
      (control) => control instanceof HTMLInputElement && control.type === 'checkbox'
    );

    if (isCheckboxGroup && namedControls.length === 1) {
      result[name] = first.checked;
    } else if (
      namedControls.length > 1
      || first instanceof HTMLSelectElement && first.multiple
      || isCheckboxGroup
    ) {
      result[name] = values;
    } else if (first instanceof HTMLInputElement && first.type === 'file') {
      result[name] = first.multiple ? values : (values[0] ?? null);
    } else if (values.length > 0) {
      result[name] = values[0];
    }
  }
  return result;
}
