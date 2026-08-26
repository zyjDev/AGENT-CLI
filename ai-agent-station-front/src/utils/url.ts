/**
 * URL 工具函数
 */

/**
 * 获取 URL 查询参数
 * @param name 参数名
 * @returns 参数值或 null
 */
export function getUrlParam(name: string): string | null {
  const urlParams = new URLSearchParams(window.location.search);
  return urlParams.get(name);
}

/**
 * 获取所有 URL 查询参数
 * @returns 参数对象
 */
export function getAllUrlParams(): Record<string, string> {
  const urlParams = new URLSearchParams(window.location.search);
  const params: Record<string, string> = {};
  
  for (const [key, value] of urlParams.entries()) {
    params[key] = value;
  }
  
  return params;
}

/**
 * 设置 URL 查询参数
 * @param name 参数名
 * @param value 参数值
 */
export function setUrlParam(name: string, value: string): void {
  const url = new URL(window.location.href);
  url.searchParams.set(name, value);
  window.history.replaceState({}, '', url.toString());
}

/**
 * 删除 URL 查询参数
 * @param name 参数名
 */
export function removeUrlParam(name: string): void {
  const url = new URL(window.location.href);
  url.searchParams.delete(name);
  window.history.replaceState({}, '', url.toString());
}