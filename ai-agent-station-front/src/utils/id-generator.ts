/**
 * 生成8位随机数字ID
 * @param existingIds 已存在的ID数组，用于确保不重复
 * @returns 8位数字字符串
 */
export function generateEightDigitId(existingIds: string[] = []): string {
  const generateRandomId = (): string => {
    // 生成10000000到99999999之间的随机数，确保是8位数字
    const min = 10000000;
    const max = 99999999;
    return Math.floor(Math.random() * (max - min + 1) + min).toString();
  };

  let newId = generateRandomId();
  
  // 确保生成的ID不重复
  while (existingIds.includes(newId)) {
    newId = generateRandomId();
  }
  
  return newId;
}

/**
 * 从JSON数据中提取所有现有的ID
 * @param jsonData 包含id字段的对象数组
 * @returns ID字符串数组
 */
export function extractExistingIds(jsonData: Array<{ id: string }>): string[] {
  return jsonData.map(item => item.id);
}