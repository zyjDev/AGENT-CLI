/**
 * LogicFlow 自定义节点：圆角卡片（类型色条 + 类型标签 + 标题 + 副标题）。
 *
 * 约定（properties 只放基本类型，不放业务 JSON）：
 * - nodeType：业务类型（agent / client / model / ...），决定颜色与默认值
 * - title：卡片标题（保存时写入 data.title）
 * - hint：卡片副标题（仅展示，不参与持久化）
 *
 * ⚠️ 两个必须遵守的约束（都踩过坑）：
 * 1. 不要在 setAttributes() 里调用 setProperties()：会「设置属性 → 重算属性 → 再设置」无限递归，
 *    表现为 Maximum call stack size exceeded、整页空白；
 * 2. 不要把后端原始 JSON 放进 properties：LogicFlow 会深度观测它，
 *    既拖慢渲染，也容易在深/宽对象上爆栈。原始 JSON 由页面用 Map 保管。
 */
import { h, RectNode, RectNodeModel } from '@logicflow/core'
import { catalogOf } from './catalog'

const CARD_WIDTH = 190
const CARD_HEIGHT = 62

export class ZhishuNodeModel extends RectNodeModel {
  setAttributes(): void {
    this.width = CARD_WIDTH
    this.height = CARD_HEIGHT
    this.radius = 12
    this.text.editable = false
  }

  /** 节点业务类型 */
  getNodeBusinessType(): string {
    const properties = this.getProperties() as Record<string, unknown>
    return String(properties.nodeType ?? 'agent')
  }

  getNodeStyle(): Record<string, unknown> {
    const style = super.getNodeStyle() as Record<string, unknown>
    const catalog = catalogOf(this.getNodeBusinessType())
    style.fill = '#ffffff'
    style.stroke = this.isSelected ? catalog.color : '#dfe4f2'
    style.strokeWidth = this.isSelected ? 2 : 1
    return style
  }

  /** 只保留「左入 / 右出」两个锚点 */
  getDefaultAnchor(): Array<{ x: number; y: number; id: string }> {
    const { x, y, width, height, id } = this
    return [
      { x: x - width / 2, y, id: `${id}_in` },
      { x: x + width / 2, y, id: `${id}_out` },
    ]
  }
}

export class ZhishuNode extends RectNode {
  getShape() {
    const model = this.props.model as ZhishuNodeModel
    const { x, y, width, height, radius } = model
    const style = model.getNodeStyle()
    const properties = model.getProperties() as Record<string, unknown>
    const catalog = catalogOf(model.getNodeBusinessType())
    const title = String(properties.title ?? catalog.label)
    const hint = String(properties.hint ?? catalog.hint)

    const left = x - width / 2
    const top = y - height / 2

    return h('g', {}, [
      // 卡片
      h('rect', { x: left, y: top, width, height, rx: radius, ry: radius, ...style }),
      // 左侧类型色条
      h('rect', { x: left, y: top, width: 4, height, rx: 2, ry: 2, fill: catalog.color, stroke: 'none' }),
      // 类型标签
      h('text', { x: left + 16, y: top + 20, fontSize: 11, fill: catalog.color, fontWeight: 600 }, catalog.label),
      // 标题
      h(
        'text',
        { x: left + 16, y: top + 38, fontSize: 13, fill: '#1F2430', fontWeight: 500 },
        title.length > 12 ? `${title.slice(0, 12)}…` : title,
      ),
      // 副标题
      h(
        'text',
        { x: left + 16, y: top + 53, fontSize: 10.5, fill: '#8C94A3' },
        hint.length > 14 ? `${hint.slice(0, 14)}…` : hint,
      ),
    ])
  }
}

export const NODE_TYPE_NAME = 'zhishu-node'

export const nodeRegistration = {
  type: NODE_TYPE_NAME,
  view: ZhishuNode,
  model: ZhishuNodeModel,
}
