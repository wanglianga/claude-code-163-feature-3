<template>
  <div class="page">
    <!-- 维修队：工单视图 -->
    <template v-if="role === 'REPAIR_TEAM'">
      <el-card class="page-card">
        <template #header><b>维修工单</b></template>
        <el-tabs v-model="orderTab">
          <el-tab-pane v-for="t in orderTabs" :key="t.label" :label="t.label" :name="t.label" />
        </el-tabs>
        <el-table :data="filteredOrders" size="large">
          <el-table-column label="工单号" width="90" prop="id" />
          <el-table-column label="事件" width="170">
            <template #default="{ row }">{{ incidentOf(row.incidentId)?.code }}</template>
          </el-table-column>
          <el-table-column label="地址" min-width="160">
            <template #default="{ row }">{{ incidentOf(row.incidentId)?.address }}</template>
          </el-table-column>
          <el-table-column label="派单时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.assignedAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="REPAIR_STATUS[row.status]?.type" size="small">
                {{ REPAIR_STATUS[row.status]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="超时" width="90">
            <template #default="{ row }">
              <el-tag v-if="isTimeout(row)" type="danger" size="small">已超时</el-tag>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button v-if="['ASSIGNED', 'RETURNED'].includes(row.status)" type="primary"
                         size="small" @click="accept(row)">接单</el-button>
              <el-button v-if="row.status === 'ACCEPTED'" type="success" size="small"
                         @click="openFinish(row)">完成维修</el-button>
              <el-button size="small" @click="$router.push(`/incidents/${row.incidentId}`)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 其他角色：事件列表 -->
    <template v-else>
      <el-card class="page-card">
        <template #header>
          <div class="flex-between">
            <b>{{ title }}</b>
            <el-input v-model="keyword" placeholder="搜索编号/地址" clearable style="width: 240px" />
          </div>
        </template>
        <el-tabs v-model="tab">
          <el-tab-pane v-for="t in tabs" :key="t.label" :label="`${t.label}(${t.list.length})`" :name="t.label" />
        </el-tabs>
        <el-table :data="currentList" size="large" @row-click="(r) => $router.push(`/incidents/${r.id}`)" style="cursor: pointer">
          <el-table-column label="事件编号" width="170">
            <template #default="{ row }">
              {{ row.code }}
              <el-tag v-if="row.recollapse" type="danger" size="small" effect="dark" style="margin-left: 4px">
                再塌陷
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="issueType" label="问题" width="70" />
          <el-table-column prop="address" label="地址" min-width="170" />
          <el-table-column prop="roadType" label="道路" width="80" />
          <el-table-column label="危险度" width="80">
            <template #default="{ row }">
              <el-tag :type="dangerType(row.dangerLevel)" size="small">{{ row.dangerLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="损伤" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.hasDamage" type="danger" size="small">有</el-tag>
              <span v-else class="text-muted">无</span>
            </template>
          </el-table-column>
          <el-table-column label="主状态" width="110">
            <template #default="{ row }">
              <el-tag :type="INCIDENT_STATUS[row.status]?.type" size="small">
                {{ INCIDENT_STATUS[row.status]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="应急线" width="105">
            <template #default="{ row }">
              <el-tag :type="EMERGENCY_STATUS[row.emergencyStatus]?.type" size="small" effect="plain">
                {{ EMERGENCY_STATUS[row.emergencyStatus]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权属线" width="105">
            <template #default="{ row }">
              <el-tag :type="OWNER_STATUS[row.ownerStatus]?.type" size="small" effect="plain">
                {{ OWNER_STATUS[row.ownerStatus]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ownerOrg" label="权属单位" width="130">
            <template #default="{ row }">{{ row.ownerOrg || '-' }}</template>
          </el-table-column>
          <el-table-column label="上报时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 完成维修对话框 -->
    <el-dialog v-model="finishVisible" title="完成维修" width="560px">
      <el-form :model="finishForm" label-width="120px">
        <el-form-item label="井室状况" required>
          <el-input v-model="finishForm.chamberCondition" placeholder="如：井室完好/井壁渗漏/井室坍塌已重砌" />
        </el-form-item>
        <el-form-item label="井盖规格" required>
          <el-input v-model="finishForm.coverSpec" placeholder="如：Φ700 重型球墨铸铁井盖" />
        </el-form-item>
        <el-form-item label="维修费用(元)">
          <el-input-number v-model="finishForm.repairCost" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="需要封路">
          <el-switch v-model="finishForm.needRoadClosure" />
        </el-form-item>
        <el-form-item label="专业单位协同">
          <el-switch v-model="finishForm.needCoord" active-text="涉及燃气/电力/通信协同" />
        </el-form-item>
        <el-form-item v-if="finishForm.needCoord" label="协同单位">
          <el-checkbox-group v-model="coordUnits">
            <el-checkbox v-for="u in meta.coordUnits || []" :key="u" :value="u">{{ u }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="维修说明">
          <el-input v-model="finishForm.repairNote" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="finish">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'
import { apiIncidents, apiMyOrders, apiAcceptOrder, apiFinishOrder, apiMeta } from '../api'
import { INCIDENT_STATUS, EMERGENCY_STATUS, OWNER_STATUS, REPAIR_STATUS, dangerType, fmtTime } from '../utils/format'

const auth = useAuthStore()
const role = computed(() => auth.role)
const incidents = ref([])
const orders = ref([])
const keyword = ref('')
const tab = ref('')
const orderTab = ref('全部')
const meta = ref({})
const acting = ref(false)

const title = computed(() => ({
  CITIZEN: '我的上报',
  STREET_DUTY: '应急处置台（应急线 + 权属线）',
  OWNER_UNIT: '认领与派单',
  INSPECTOR: '验收工作台',
  ADMIN: '全部事件'
}[role.value] || '事件列表'))

const tabs = computed(() => {
  const list = filtered.value
  switch (role.value) {
    case 'CITIZEN':
      return [
        { label: '全部', list },
        { label: '处理中', list: list.filter(i => i.status !== 'CLOSED') },
        { label: '已结案', list: list.filter(i => i.status === 'CLOSED') }
      ]
    case 'STREET_DUTY':
      return [
        { label: '待应急处置', list: list.filter(i => i.emergencyStatus !== 'SECURED') },
        { label: '待权属排查', list: list.filter(i => i.ownerStatus === 'INVESTIGATING') },
        { label: '责任待定', list: list.filter(i => i.ownerStatus === 'PENDING') },
        { label: '全部', list }
      ]
    case 'OWNER_UNIT':
      return [
        { label: '待认领', list: list.filter(i => i.ownerStatus !== 'CONFIRMED') },
        { label: '待派单', list: list.filter(i => i.status === 'OWNER_CONFIRMED') },
        { label: '维修中', list: list.filter(i => ['REPAIRING', 'REPAIRED'].includes(i.status)) },
        { label: '全部', list }
      ]
    case 'INSPECTOR':
      return [
        { label: '待验收', list: list.filter(i => i.status === 'REPAIRED') },
        { label: '已验收', list: list.filter(i => ['ACCEPTED', 'CLOSED'].includes(i.status)) },
        { label: '全部', list }
      ]
    default:
      return [{ label: '全部', list }]
  }
})

const filtered = computed(() => {
  if (!keyword.value.trim()) return incidents.value
  const k = keyword.value.trim()
  return incidents.value.filter(i => i.code.includes(k) || i.address.includes(k))
})

const currentList = computed(() => {
  const t = tabs.value.find(x => x.label === tab.value) || tabs.value[0]
  return t ? t.list : []
})

const orderTabs = computed(() => [
  { label: '全部' },
  { label: '待接单' },
  { label: '维修中' },
  { label: '已完成' }
])

const filteredOrders = computed(() => {
  switch (orderTab.value) {
    case '待接单': return orders.value.filter(o => ['ASSIGNED', 'RETURNED'].includes(o.status))
    case '维修中': return orders.value.filter(o => o.status === 'ACCEPTED')
    case '已完成': return orders.value.filter(o => o.status === 'FINISHED')
    default: return orders.value
  }
})

function incidentOf(id) {
  return incidents.value.find(i => i.id === id)
}

function isTimeout(o) {
  const end = o.finishedAt ? new Date(o.finishedAt) : new Date()
  return (end - new Date(o.assignedAt)) / 3600000 > 48
}

async function accept(row) {
  await apiAcceptOrder(row.id)
  ElMessage.success('已接单，请尽快到场维修')
  load()
}

const finishVisible = ref(false)
const currentOrder = ref(null)
const coordUnits = ref([])
const finishForm = reactive({
  chamberCondition: '', coverSpec: '', repairCost: 0,
  needRoadClosure: false, needCoord: false, repairNote: ''
})

function openFinish(row) {
  currentOrder.value = row
  coordUnits.value = []
  Object.assign(finishForm, {
    chamberCondition: '', coverSpec: '', repairCost: 0,
    needRoadClosure: false, needCoord: false, repairNote: ''
  })
  finishVisible.value = true
}

async function finish() {
  acting.value = true
  try {
    await apiFinishOrder(currentOrder.value.id, {
      ...finishForm,
      coordUnits: coordUnits.value.join(',')
    })
    ElMessage.success('维修完成，已通知城管巡查员验收')
    finishVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

async function load() {
  incidents.value = await apiIncidents()
  if (role.value === 'REPAIR_TEAM') orders.value = await apiMyOrders()
  if (!tab.value && tabs.value.length) tab.value = tabs.value[0].label
}

onMounted(async () => {
  await load()
  meta.value = await apiMeta()
})
</script>
