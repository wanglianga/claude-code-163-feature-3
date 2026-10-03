import dayjs from 'dayjs'

export const fmtTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

export const INCIDENT_STATUS = {
  REPORTED: { label: '已上报', type: 'danger' },
  SECURED: { label: '已临时围护', type: 'warning' },
  OWNER_CONFIRMED: { label: '权属已确认', type: 'primary' },
  REPAIRING: { label: '维修中', type: 'primary' },
  REPAIRED: { label: '待验收', type: 'warning' },
  ACCEPTED: { label: '验收通过', type: 'success' },
  CLOSED: { label: '已结案', type: 'info' }
}

export const EMERGENCY_STATUS = {
  PENDING: { label: '应急待派单', type: 'danger' },
  DISPATCHED: { label: '应急已派单', type: 'warning' },
  SECURED: { label: '围护已完成', type: 'success' }
}

export const OWNER_STATUS = {
  UNCONFIRMED: { label: '权属未确认', type: 'info' },
  INVESTIGATING: { label: '权属排查中', type: 'warning' },
  CONFIRMED: { label: '权属已确认', type: 'success' },
  PENDING: { label: '责任待定', type: 'danger' }
}

export const REPAIR_STATUS = {
  ASSIGNED: { label: '待接单', type: 'warning' },
  ACCEPTED: { label: '维修中', type: 'primary' },
  FINISHED: { label: '已完成', type: 'success' },
  RETURNED: { label: '退回重修', type: 'danger' }
}

export const COMP_STATUS = {
  FILED: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已核定', type: 'primary' },
  REJECTED: { label: '已驳回', type: 'info' },
  PAID: { label: '已赔付', type: 'success' }
}

export const COST_STATUS = {
  PENDING: { label: '待复核', type: 'warning' },
  ALLOCATED: { label: '已分摊', type: 'success' }
}

export const RESPONSIBILITY = {
  OWNER: '权属单位全责',
  STREET: '街道责任',
  SHARED: '多方分摊',
  UNDETERMINED: '无法认定'
}

export const MATERIAL_TYPE = {
  DASHCAM: '行车记录仪',
  POLICE_CERT: '交警证明',
  REPAIR_INVOICE: '维修发票',
  OTHER: '其他佐证'
}

export const MATERIAL_STATUS = {
  PENDING: { label: '待核验', type: 'warning' },
  VERIFIED: { label: '已核验', type: 'success' },
  DOUBTFUL: { label: '存疑', type: 'danger' }
}

export const TIMELINE_FLAG = {
  BEFORE_REPORT: { label: '事故在首次上报前', type: 'info' },
  AFTER_REPORT_UNSECURED: { label: '上报后围护前', type: 'danger' },
  AFTER_SECURED: { label: '围护完成后', type: 'success' }
}

export const FOLLOWUP_STATUS = {
  TRACKING: { label: '追踪中', type: 'warning' },
  COMPLETED: { label: '维修彻底完成', type: 'success' },
  RECOLLAPSED: { label: '同一井盖再次塌陷', type: 'danger' }
}

export const dangerType = (level) => ({ 高: 'danger', 中: 'warning', 低: 'info' }[level] || 'info')

export const money = (v) => `¥${Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
