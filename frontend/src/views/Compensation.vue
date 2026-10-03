<template>
  <div class="page">
    <el-card class="page-card">
      <template #header><b>赔付案件管理</b></template>
      <el-tabs v-model="tab">
        <el-tab-pane v-for="t in tabs" :key="t.label" :label="`${t.label}(${t.list.length})`" :name="t.label" />
      </el-tabs>
      <el-table :data="current" size="large">
        <el-table-column label="案件号" width="70" prop="id" />
        <el-table-column label="关联事件" width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/incidents/${row.incidentId}`)">
              {{ codeOf(row.incidentId) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="claimantName" label="索赔人" width="90" />
        <el-table-column prop="damageType" label="损失类型" width="90" />
        <el-table-column label="索赔金额" width="100">
          <template #default="{ row }">{{ money(row.claimAmount) }}</template>
        </el-table-column>
        <el-table-column label="事故时间" width="145">
          <template #default="{ row }">{{ row.accidentTime ? fmtTime(row.accidentTime) : '未录入' }}</template>
        </el-table-column>
        <el-table-column label="时效关系" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.timeRelation" :type="TIME_RELATION[row.timeRelation]?.type" size="small">
              {{ TIME_RELATION[row.timeRelation]?.label }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="维修追踪" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.repairTrackStatus" :type="REPAIR_TRACK[row.repairTrackStatus]?.type" size="small">
              {{ REPAIR_TRACK[row.repairTrackStatus]?.label }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="COMP_STATUS[row.status]?.type" size="small">{{ COMP_STATUS[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'FILED' && role === 'COMPENSATOR'">
              <el-button type="primary" size="small" @click="openVerify(row)">材料核验</el-button>
              <el-button type="success" size="small" @click="openDecide(row)">审核定责</el-button>
            </template>
            <el-button v-if="row.status === 'APPROVED' && role === 'COMPENSATOR'" type="success"
                       size="small" @click="pay(row)">确认赔付</el-button>
            <el-button size="small" @click="view(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 材料核验抽屉 -->
    <el-drawer v-model="verifyVisible" title="赔付材料核验（五项）" size="640px">
      <template v-if="detail">
        <el-alert v-if="detail.recollapseTrace" type="error" :closable="false" class="mb16"
                  :title="`同一井盖再次塌陷：上一事件 ${detail.recollapseTrace.previousCode}`"
                  :description="`上一轮维修单位 ${detail.recollapseTrace.repairTeam || '-'} · 材料规格 ${detail.recollapseTrace.coverSpec || '-'} · 验收人 ${detail.recollapseTrace.inspectorName || '-'}，均纳入本次责任判断。`" />
        <el-descriptions :column="2" border size="small" class="mb16">
          <el-descriptions-item label="关联事件">{{ detail.incident.code }}</el-descriptions-item>
          <el-descriptions-item label="首次上报">{{ fmtTime(detail.incident.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ detail.incident.address }}</el-descriptions-item>
        </el-descriptions>

        <!-- 事故时间录入与时效分析 -->
        <el-divider content-position="left">事故时间（系统自动时效分析）</el-divider>
        <div style="display: flex; gap: 8px; align-items: center" class="mb16">
          <el-date-picker v-model="accidentTimeInput" type="datetime" placeholder="选择事故时间"
                          value-format="YYYY-MM-DDTHH:mm:ss" style="width: 220px" />
          <el-button type="primary" :loading="acting" @click="saveAccidentTime">保存并分析</el-button>
          <el-tag v-if="detail.case.timeRelation" :type="TIME_RELATION[detail.case.timeRelation]?.type">
            {{ TIME_RELATION[detail.case.timeRelation]?.label }}
          </el-tag>
        </div>
        <el-alert v-if="detail.case.platformSnapshot" :closable="false" class="mb16"
                  :type="detail.case.timeRelation === 'BEFORE_REPORT' ? 'info' : 'warning'"
                  title="平台当时井盖状态快照">
          <template #default>
            <div class="snapshot">{{ detail.case.platformSnapshot }}</div>
          </template>
        </el-alert>

        <!-- 五项材料 -->
        <el-divider content-position="left">材料清单（全部核验后方可定责）</el-divider>
        <div v-for="m in detail.materials" :key="m.id" class="material-card">
          <div class="flex-between">
            <b>{{ MATERIAL_TYPE[m.materialType] }}</b>
            <div>
              <el-tag :type="m.submitted ? 'primary' : 'info'" size="small" effect="plain">
                {{ m.submitted ? '已提交' : '未提交' }}
              </el-tag>
              <el-tag :type="VERIFY_STATUS[m.verifyStatus]?.type" size="small" style="margin-left: 6px">
                {{ VERIFY_STATUS[m.verifyStatus]?.label }}
              </el-tag>
            </div>
          </div>
          <el-input v-model="m.content" type="textarea" :rows="2" class="mt16"
                    :placeholder="materialPlaceholder(m.materialType)" />
          <div style="display: flex; gap: 8px; align-items: center; margin-top: 8px; flex-wrap: wrap">
            <el-checkbox v-model="m.submitted">材料已提交</el-checkbox>
            <el-upload :http-request="(o) => uploadMaterial(o, m)" :show-file-list="false" accept="image/*">
              <el-button size="small">上传附件</el-button>
            </el-upload>
            <el-link v-if="m.fileUrl" :href="m.fileUrl" target="_blank" type="primary">查看附件</el-link>
            <el-button size="small" type="primary" plain @click="saveMaterial(m)">保存材料</el-button>
          </div>
          <div v-if="m.verifyStatus === 'PENDING'"
               style="display: flex; gap: 8px; align-items: center; margin-top: 8px">
            <el-radio-group v-model="m._verify" size="small">
              <el-radio-button value="PASSED">核验通过</el-radio-button>
              <el-radio-button value="FAILED">核验不通过</el-radio-button>
            </el-radio-group>
            <el-input v-model="m._note" placeholder="核验意见" size="small" style="flex: 1" />
            <el-button size="small" type="warning" @click="verifyMaterial(m)">提交核验</el-button>
          </div>
          <div v-else class="text-muted" style="margin-top: 8px; font-size: 12px">
            核验意见：{{ m.verifyNote || '-' }} · {{ fmtTime(m.verifiedAt) }}
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 审核定责对话框 -->
    <el-dialog v-model="decideVisible" title="赔付审核定责" width="680px">
      <template v-if="detail">
        <el-alert v-if="!canDecide" type="error" :closable="false" class="mb16"
                  title="暂不能定责：请先录入事故时间并完成全部五项材料核验（在「材料核验」中操作）" />
        <template v-else>
          <el-alert :closable="false" class="mb16"
                    :type="detail.case.timeRelation === 'BEFORE_REPORT' ? 'info' : 'warning'"
                    :title="`时效关系：${TIME_RELATION[detail.case.timeRelation]?.label}`">
            <template #default>
              <div class="snapshot">{{ detail.case.platformSnapshot }}</div>
            </template>
          </el-alert>
          <el-alert v-if="detail.recollapseTrace" type="error" :closable="false" class="mb16"
                    title="再次塌陷追溯：上一轮维修单位、材料规格、验收人已纳入责任判断"
                    :description="`上一事件 ${detail.recollapseTrace.previousCode} · 维修单位 ${detail.recollapseTrace.repairTeam || '-'} · 材料规格 ${detail.recollapseTrace.coverSpec || '-'} · 验收人 ${detail.recollapseTrace.inspectorName || '-'}`" />
        </template>
        <el-form :model="decideForm" label-width="110px">
          <el-form-item label="损失类型">
            <el-select v-model="decideForm.damageType" style="width: 200px">
              <el-option v-for="t in meta.damageTypes || []" :key="t" :value="t" :label="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="索赔金额">
            <el-input-number v-model="decideForm.claimAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="事故证明">
            <el-input v-model="decideForm.accidentProof" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="审核结论">
            <el-radio-group v-model="decideForm.result">
              <el-radio value="APPROVED">核定赔付</el-radio>
              <el-radio value="REJECTED">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="decideForm.result === 'APPROVED'">
            <el-form-item label="责任认定">
              <el-select v-model="decideForm.responsibility" style="width: 200px">
                <el-option value="OWNER" label="权属单位全责" />
                <el-option value="STREET" label="街道责任（围护不及时）" />
                <el-option value="SHARED" label="多方分摊" />
                <el-option value="UNDETERMINED" label="无法认定" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="decideForm.responsibility === 'SHARED'" label="分摊比例">
              <el-input-number v-model="decideForm.ownerRatio" :min="0" :max="100" />
              <span style="margin: 0 6px">% 权属单位 / 街道</span>
              <el-input-number v-model="decideForm.streetRatio" :min="0" :max="100" />
              <span style="margin-left: 6px">%</span>
            </el-form-item>
            <el-form-item label="核定金额">
              <el-input-number v-model="decideForm.approvedAmount" :min="0" :precision="2" />
            </el-form-item>
          </template>
          <el-form-item label="责任分析" required>
            <el-input v-model="decideForm.analysis" type="textarea" :rows="4"
                      placeholder="结合时效分析与五项材料核验结论，从上报时间、临时围护、权属确认、维修时长、事故证明五方面分析" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="decideVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!canDecide" :loading="acting" @click="decide">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="viewVisible" title="赔付案件详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="索赔人">
            {{ detail.case.claimantName }}（{{ detail.case.claimantPhone }}）
          </el-descriptions-item>
          <el-descriptions-item label="损失类型">{{ detail.case.damageType }}</el-descriptions-item>
          <el-descriptions-item label="索赔金额">{{ money(detail.case.claimAmount) }}</el-descriptions-item>
          <el-descriptions-item label="事故证明">{{ detail.case.accidentProof || '-' }}</el-descriptions-item>
          <el-descriptions-item label="事故时间">
            {{ detail.case.accidentTime ? fmtTime(detail.case.accidentTime) : '未录入' }}
            <el-tag v-if="detail.case.timeRelation" :type="TIME_RELATION[detail.case.timeRelation]?.type"
                    size="small" style="margin-left: 8px">
              {{ TIME_RELATION[detail.case.timeRelation]?.label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.case.platformSnapshot" label="平台状态快照">
            <div class="snapshot">{{ detail.case.platformSnapshot }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="材料核验">
            <div v-for="m in detail.materials" :key="m.id" style="margin-bottom: 4px">
              <el-tag :type="VERIFY_STATUS[m.verifyStatus]?.type" size="small" effect="plain">
                {{ MATERIAL_TYPE[m.materialType] }}：{{ VERIFY_STATUS[m.verifyStatus]?.label }}
              </el-tag>
              <span v-if="m.verifyNote" class="text-muted" style="margin-left: 6px">{{ m.verifyNote }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="责任分析">{{ detail.case.analysis || '-' }}</el-descriptions-item>
          <el-descriptions-item label="责任认定">
            {{ detail.case.responsibility ? RESPONSIBILITY[detail.case.responsibility] : '-' }}
            <span v-if="detail.case.responsibility === 'SHARED'" class="text-muted">
              （权属 {{ detail.case.ownerRatio }}% / 街道 {{ detail.case.streetRatio }}%）
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="核定金额">{{ money(detail.case.approvedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ fmtTime(detail.case.decidedAt) }}</el-descriptions-item>
          <el-descriptions-item label="赔付时间">{{ fmtTime(detail.case.paidAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.case.repairTrackStatus" label="维修追踪">
            <el-tag :type="REPAIR_TRACK[detail.case.repairTrackStatus]?.type" size="small">
              {{ REPAIR_TRACK[detail.case.repairTrackStatus]?.label }}
            </el-tag>
            <div class="text-muted" style="margin-top: 4px">
              {{ detail.case.repairTrackNote }} · {{ fmtTime(detail.case.repairTrackedAt) }}
            </div>
          </el-descriptions-item>
        </el-descriptions>
        <template v-if="detail.recollapseTrace">
          <el-divider content-position="left">再次塌陷追溯（进入新的责任判断）</el-divider>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="上一事件">
              <el-link type="primary" @click="$router.push(`/incidents/${detail.recollapseTrace.previousIncidentId}`)">
                {{ detail.recollapseTrace.previousCode }}
              </el-link>
              （{{ detail.recollapseTrace.previousIssueType }}）
            </el-descriptions-item>
            <el-descriptions-item label="上一轮维修单位">{{ detail.recollapseTrace.repairTeam || '-' }}</el-descriptions-item>
            <el-descriptions-item label="材料规格">{{ detail.recollapseTrace.coverSpec || '-' }}</el-descriptions-item>
            <el-descriptions-item label="验收人">{{ detail.recollapseTrace.inspectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="验收时间">{{ fmtTime(detail.recollapseTrace.acceptedAt) }}</el-descriptions-item>
            <el-descriptions-item label="距上次维修">
              {{ detail.recollapseTrace.daysSinceRepair != null ? detail.recollapseTrace.daysSinceRepair + ' 天' : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import {
  apiCompensations, apiCompDetail, apiCompUpdate, apiCompDecide, apiCompPay,
  apiCompAccidentTime, apiCompMaterialUpdate, apiCompMaterialVerify,
  apiIncidents, apiMeta, apiUpload
} from '../api'
import {
  COMP_STATUS, RESPONSIBILITY, MATERIAL_TYPE, VERIFY_STATUS, TIME_RELATION, REPAIR_TRACK,
  fmtTime, money
} from '../utils/format'

const auth = useAuthStore()
const role = computed(() => auth.role)
const list = ref([])
const incidents = ref([])
const meta = ref({})
const tab = ref('全部')
const acting = ref(false)

const tabs = computed(() => [
  { label: '全部', list: list.value },
  { label: '待审核', list: list.value.filter(c => c.status === 'FILED') },
  { label: '待赔付', list: list.value.filter(c => c.status === 'APPROVED') },
  { label: '已赔付', list: list.value.filter(c => c.status === 'PAID') },
  { label: '已驳回', list: list.value.filter(c => c.status === 'REJECTED') }
])
const current = computed(() => tabs.value.find(t => t.label === tab.value)?.list || [])

function codeOf(incidentId) {
  return incidents.value.find(i => i.id === incidentId)?.code || `#${incidentId}`
}

const verifyVisible = ref(false)
const decideVisible = ref(false)
const viewVisible = ref(false)
const detail = ref(null)
const accidentTimeInput = ref(null)

const canDecide = computed(() =>
  !!detail.value?.case?.accidentTime && !!detail.value?.allVerified)

const decideForm = reactive({
  damageType: '', claimAmount: 0, accidentProof: '', result: 'APPROVED',
  responsibility: 'OWNER', ownerRatio: 100, streetRatio: 0, approvedAmount: 0, analysis: ''
})

async function openDetail(row) {
  detail.value = await apiCompDetail(row.id)
  // 为每项材料补充本地编辑态
  detail.value.materials.forEach(m => { m._verify = 'PASSED'; m._note = '' })
  accidentTimeInput.value = detail.value.case.accidentTime || null
}

function openVerify(row) { openDetail(row).then(() => { verifyVisible.value = true }) }

function openDecide(row) {
  openDetail(row).then(() => {
    const c = detail.value.case
    Object.assign(decideForm, {
      damageType: c.damageType === '待补充' ? '车辆损失' : c.damageType,
      claimAmount: c.claimAmount,
      accidentProof: c.accidentProof,
      result: 'APPROVED',
      responsibility: 'OWNER',
      ownerRatio: 100,
      streetRatio: 0,
      approvedAmount: c.claimAmount,
      analysis: ''
    })
    decideVisible.value = true
  })
}

function view(row) { openDetail(row).then(() => { viewVisible.value = true }) }

async function reload() {
  if (detail.value) await openDetail(detail.value.case)
  await load()
}

async function saveAccidentTime() {
  if (!accidentTimeInput.value) return ElMessage.warning('请选择事故时间')
  acting.value = true
  try {
    await apiCompAccidentTime(detail.value.case.id, { accidentTime: accidentTimeInput.value })
    ElMessage.success('已生成时效分析')
    await reload()
  } finally {
    acting.value = false
  }
}

function materialPlaceholder(type) {
  return {
    ACCIDENT_TIME: '由系统按录入的事故时间自动回填，可补充说明',
    DASHCAM: '行车记录仪视频/截图要点：时间、车牌、事发画面',
    POLICE_CERT: '交警事故认定书编号与结论',
    REPAIR_INVOICE: '维修发票号码、金额、维修项目',
    PLATFORM_STATUS: '由系统按事故时间自动生成平台状态快照'
  }[type] || ''
}

async function saveMaterial(m) {
  await apiCompMaterialUpdate(detail.value.case.id, m.id, {
    content: m.content, submitted: m.submitted
  })
  ElMessage.success(`「${MATERIAL_TYPE[m.materialType]}」已保存`)
  await reload()
}

async function uploadMaterial({ file }, m) {
  const url = await apiUpload(file)
  await apiCompMaterialUpdate(detail.value.case.id, m.id, { fileUrl: url, submitted: true })
  ElMessage.success('附件已上传')
  await reload()
}

async function verifyMaterial(m) {
  if (m._verify === 'PASSED' && !m.submitted) return ElMessage.warning('材料尚未提交，不能核验通过')
  await apiCompMaterialVerify(detail.value.case.id, m.id, {
    verifyStatus: m._verify, verifyNote: m._note
  })
  ElMessage.success('核验已提交')
  await reload()
}

async function decide() {
  if (!decideForm.analysis.trim()) return ElMessage.warning('请填写责任分析')
  if (decideForm.responsibility === 'SHARED'
      && decideForm.ownerRatio + decideForm.streetRatio !== 100) {
    return ElMessage.warning('分摊比例之和须为 100%')
  }
  acting.value = true
  try {
    await apiCompUpdate(detail.value.case.id, {
      damageType: decideForm.damageType,
      claimAmount: decideForm.claimAmount,
      accidentProof: decideForm.accidentProof
    })
    await apiCompDecide(detail.value.case.id, decideForm)
    ElMessage.success('审核完成')
    decideVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

async function pay(row) {
  await ElMessageBox.confirm(`确认向 ${row.claimantName} 赔付 ${money(row.approvedAmount)}？`, '赔付确认')
  await apiCompPay(row.id)
  ElMessage.success('已标记赔付完成')
  load()
}

async function load() {
  list.value = await apiCompensations()
  incidents.value = await apiIncidents()
}

onMounted(async () => {
  await load()
  meta.value = await apiMeta()
})
</script>

<style scoped>
.snapshot {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 13px;
}
.material-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}
</style>
