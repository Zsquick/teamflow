const SVG_NAMESPACE = 'http://www.w3.org/2000/svg';

const iconPaths = {
  teams: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2',
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
    'M22 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75'
  ],
  notifications: [
    'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9',
    'M13.73 21a2 2 0 0 1-3.46 0'
  ],
  invitations: [
    'M4 4h16v16H4Z',
    'm4 7 8 6 8-6',
    'M9 17h6'
  ],
  search: [
    'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z',
    'm21 21-4.35-4.35'
  ],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  logout: [
    'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4',
    'm16 17 5-5-5-5',
    'M21 12H9'
  ],
  arrow: ['m9 18 6-6-6-6'],
  folder: [
    'M3 6a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z'
  ],
  board: [
    'M3 3h7v18H3Z',
    'M14 3h7v10h-7Z',
    'M14 17h7v4h-7Z'
  ]
};

/** 创建硬编码线性 SVG 图标，不注入任何外部 HTML。 */
export function createIcon(name, className = 'icon') {
  const svg = document.createElementNS(SVG_NAMESPACE, 'svg');
  svg.setAttribute('class', className);
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');

  for (const definition of iconPaths[name] ?? iconPaths.folder) {
    const path = document.createElementNS(SVG_NAMESPACE, 'path');
    path.setAttribute('d', definition);
    svg.append(path);
  }
  return svg;
}
