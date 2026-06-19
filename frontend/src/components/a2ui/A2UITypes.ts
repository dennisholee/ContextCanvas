export type A2UIComponentType =
  | 'line_chart'
  | 'bar_chart'
  | 'pie_chart'
  | 'metric_card'
  | 'stat_card'
  | 'data_table'
  | 'progress_bar'
  | 'gauge'
  | 'area_chart'
  | 'confirmation_card'
  | 'error_card'
  | 'clarification_prompt'
  | 'proactive_insight'

export type DashboardLayout = 'vertical' | 'grid' | 'sidebar'

export interface A2UIPayload {
  componentType: A2UIComponentType
  title?: string
  description?: string
  data?: unknown
  config?: Record<string, unknown>
  action?: {
    type: 'create' | 'update' | 'delete'
    entity: string
    recordId?: string | number
    oldValues?: Record<string, unknown>
    newValues: Record<string, unknown>
  }
}

export interface A2UICompositePayload {
  componentType: 'dashboard'
  title?: string
  layout: DashboardLayout
  components: A2UIPayload[]
}

export type A2UIResponse = A2UIPayload | A2UICompositePayload