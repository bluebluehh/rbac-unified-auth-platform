/** A40：集中枚举与字典（只读；字典是配置读取，前端不另建中文映射） */
import { httpGet } from './request'
import type { DictionaryVO } from '@/types'

export function getDictionaries(): Promise<DictionaryVO> {
  return httpGet('/meta/dictionaries')
}
