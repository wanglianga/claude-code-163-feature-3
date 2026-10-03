<template>
  <div class="page">
    <el-card class="page-card">
      <template #header><b>赔付案件管理</b></template>
      <el-tabs v-model="mainTab">
        <el-tab-pane label="赔付案件" name="cases" />
        <el-tab-pane :label="`维修追踪(${followUps.length})`" name="followUps" />
        <el-tab-pane :label="`风险线索(${riskClues.length})`" name="riskClues" />
      </el-tabs>

      <!-- 赔付案件 -->
      <template v-if="mainTab === 'cases'">
        <el-tabs v-model="tab" type="card">
          <el-tab-pane v-for="t in tabs" :key="t.label" :label="`${t.label}(${t.list.length})`" :name="t.label" />
        </el-tabs>
        <el-table :data="current" size="large">
          <el-table-column label="案件号" width="80" prop="id" />
          <el-table-column label="关联事件" width="160">
            <template #default="{ row }">
              <el-link type="primary" @click="$router.push(`/incidents/${row.incidentId}`)">
                {{ codeOf(row.incidentId) }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column prop="claimantName" label="索赔人" width="100" />
          <el-table-column prop="damageType" label="损失类型" width="100" />
          <el-table-column label="索赔金额" width="110">
            <template #default="{ row }">{{ money(row.claimAmount) }}</template>
          </el-table-column>
          <el-table-column label="时间线核验" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.timelineFlag" :type="TIMELINE_FLAG[row.timelineFlag]?.type" size="small">
                {{ TIMELINE_FLAG[row.timelineFlag]?.label }}
              </el-tag>
              <span v-else class="text-muted">未核验</span>
            </template>
          </el-table-column>
          <el-table-column label="责任认定" width="130">
            <template #default="{ row }">
              {{ row.responsibility ? RESPONSIBILITY[row.responsibility] : '-' }}
              <span v-if="row.responsibility === 'SHARED'" class="text-muted">
                {{ row.ownerRatio }}:{{ row.streetRatio }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="COMP_STATUS[row.status]?.type" size="small">{{ COMP_STATUS[row.status]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="立案时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'FILED' && role === 'COMPENSATOR'" type="primary"
                         size="small" @click="openReview(row)">审核定责</el-button>
              <el-button v-if="row.status === 'APPROVED' && role === 'COMPENSATOR'" type="success"
                         size="small" @click="pay(row)">确认赔付</el-button>
              <el-button size="small" @click="openReview(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 维修追踪 -->
      <template v-if="mainTab === 'followUps'">
        <el-alert type="info" :closable="false" class="mb16"
                  title="赔付审核通过后，平台继续追踪维修是否彻底完成；同一井盖再次塌陷时，上一轮维修单位、材料规格与验收人进入新的责任判断。" />
        <el-table :data="followUps" size="large">
          <el-table-column label="追踪号" width="90">
            <template #default="{ row }">{{ row.followUp.id }}</template>
          </el-table-column>
          <el-table-column label="关联事件" width="150">
            <template #default="{ row }">
              <el-link type="primary" @click="$router.push(`/incidents/${row.followUp.incidentId}`)">
                {{ row.incidentCode }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column prop="address" label="地址" min-width="160" />
          <el-table-column prop="claimantName" label="索赔人" width="100" />
          <el-table-column label="核定金额" width="110">
            <template #default="{ row }">{{ money(row.approvedAmount) }}</template>
          </el-table-column>
          <el-table-column label="追踪状态" width="150">
            <template #default="{ row }">
              <el-tag :type="FOLLOWUP_STATUS[row.followUp.status]?.type" size="small">
                {{ FOLLOWUP_STATUS[row.followUp.status]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="复发新事件" width="150">
            <template #default="{ row }">
              <el-link v-if="row.followUp.linkedIncidentId" type="danger"
                       @click="$router.push(`/incidents/${row.followUp.linkedIncidentId}`)">
                {{ row.linkedIncidentCode }}
              </el-link>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="追踪记录" min-width="240">
            <template #default="{ row }">
              <span style="white-space: pre-wrap">{{ row.followUp.checkNote }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.followUp.status === 'TRACKING' && role === 'COMPENSATOR'"
                         type="success" size="small" @click="completeFollowUp(row)">确认彻底完成</el-button>
              <span v-else class="text-muted">{{ fmtTime(row.followUp.updatedAt) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 风险线索 -->
      <template v-if="mainTab === 'riskClues'">
        <el-alert type="info" :closable="false" class="mb16"
                  title="事故发生于首次上报前时，平台无在案记录，仅登记风险线索备查，不构成值班处置时效与权属响应责任。" />
        <el-table :data="riskClues" size="large">
          <el-table-column label="关联事件" width="150">
            <template #default="{ row }">
              <el-link type="primary" @click="$router.push(`/incidents/${row.clue.incidentId}`)">
                {{ row.incidentCode }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column prop="address" label="地址" min-width="160" />
          <el-table-column prop="claimantName" label="索赔人" width="100" />
          <el-table-column label="事故时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.clue.accidentTime) }}</template>
          </el-table-column>
          <el-table-column label="首次上报" width="150">
            <template #default="{ row }">{{ fmtTime(row.clue.firstReportTime) }}</template>
          </el-table-column>
          <el-table-column label="线索说明" min-width="280">
            <template #default="{ row }">{{ row.clue.note }}</template>
          </el-table-column>
          <el-table-column label="登记时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.clue.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <!-- 审核定责 / 详情抽屉 -->
    <el-drawer v-model="reviewVisible" :title="rv?.case?.status === 'FILED' ? '赔付审核定责' : '赔付案件详情'"
               size="780px" @open="loadReview">
      <div v-if="rv" style="padding: 0 4px">
        <!-- 案件信息 -->
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="关联事件">
            <el-link type="primary" @click="$router.push(`/incidents/${rv.case.incidentId}`)">
              {{ rv.incident.code }}
            </el-link>
            <span class="text-muted">（{{ rv.incident.address }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="索赔人">{{ rv.case.claimantName }}（{{ rv.case.claimantPhone }}）</el-descriptions-item>
          <el-descriptions-item label="损失类型">{{ rv.case.damageType }}</el-descriptions-item>
          <el-descriptions-item label="索赔金额">{{ money(rv.case.claimAmount) }}</el-descriptions-item>
          <el-descriptions-item label="事故证明" :span="2">{{ rv.case.accidentProof || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 事故时间与时间线核验 -->
        <el-divider content-position="left"><b>事故时间与平台记录核验</b></el-divider>
        <div v-if="canEdit" class="mb16" style="display: flex; gap: 8px; align-items: center">
          <el-date-picker v-model="reviewForm.accidentTime" type="datetime" placeholder="选择事故时间"
                          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 220px" />
          <el-button type="primary" plain :loading="acting" @click="saveAccidentTime">保存事故时间并核验</el-button>
        </div>
        <el-descriptions :column="3" border size="small" class="mb16">
          <el-descriptions-item label="事故时间">{{ fmtTime(rv.case.accidentTime) }}</el-descriptions-item>
          <el-descriptions-item label="首次上报">{{ fmtTime(rv.incident.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="围护完成">{{ fmtTime(rv.emergency?.securedAt) }}</el-descriptions-item>
          <el-descriptions-item label="应急派单">{{ fmtTime(rv.emergency?.dispatchedAt) }}</el-descriptions-item>
          <el-descriptions-item label="到场时间">{{ fmtTime(rv.emergency?.arrivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="权属确认">{{ fmtTime(rv.ownerConfirmedAt) }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="rv.case.timelineFlag" class="mb16">
          <el-tag :type="TIMELINE_FLAG[rv.case.timelineFlag]?.type" effect="dark" size="small">
            {{ TIMELINE_FLAG[rv.case.timelineFlag]?.label }}
          </el-tag>
          <span class="text-muted" style="margin-left: 8px">
            处置时限：{{ rv.emergencyLimitMinutes }} 分钟（{{ rv.incident.dangerLevel }}危）
          </span>
          <el-alert :type="rv.case.timelineFlag === 'AFTER_REPORT_UNSECURED' ? 'warning' : 'info'"
                    :closable="false" class="mt16">
            <template #title>
              <span style="white-space: pre-wrap">{{ rv.case.timelineAnalysis }}</span>
            </template>
          </el-alert>
          <el-alert v-if="rv.riskClue" type="info" :closable="false" class="mt16"
                    :title="`已登记风险线索：${rv.riskClue.note}`" />
        </div>
        <el-alert v-else type="info" :closable="false" class="mb16"
                  title="尚未录入事故时间，录入后系统自动比对平台记录（上报/围护/权属确认）生成核验结论。" />

        <!-- 赔付材料 -->
        <el-divider content-position="left"><b>赔付材料核验（行车记录仪 / 交警证明 / 维修发票）</b></el-divider>
        <el-table :data="rv.materials" size="small" class="mb16">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ MATERIAL_TYPE[row.material.type] }}</template>
          </el-table-column>
          <el-table-column label="材料" min-width="180">
            <template #default="{ row }">
              <div>{{ row.material.title }}</div>
              <div class="text-muted">{{ row.material.content }}</div>
              <el-image v-if="row.material.fileUrl" :src="row.material.fileUrl"
                        :preview-src-list="[row.material.fileUrl]" fit="cover"
                        style="width: 64px; height: 64px; border-radius: 4px; margin-top: 4px" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="MATERIAL_STATUS[row.material.status]?.type" size="small">
                {{ MATERIAL_STATUS[row.material.status]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交/核验" min-width="150">
            <template #default="{ row }">
              <div>{{ row.uploaderName }} · {{ fmtTime(row.material.createdAt) }}</div>
              <div v-if="row.verifierName" class="text-muted">
                {{ row.verifierName }} 核验：{{ row.material.verifyNote || '通过' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="role === 'COMPENSATOR'" label="操作" width="130">
            <template #default="{ row }">
              <template v-if="row.material.status !== 'VERIFIED'">
                <el-button type="success" size="small" text @click="verifyMaterial(row.material, 'VERIFIED')">通过</el-button>
                <el-button type="danger" size="small" text @click="verifyMaterial(row.material, 'DOUBTFUL')">存疑</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <el-form v-if="canEdit" inline class="mb16">
          <el-form-item label="类型">
            <el-select v-model="materialForm.type" style="width: 130px">
              <el-option v-for="t in meta.materialTypes || []" :key="t" :value="t" :label="MATERIAL_TYPE[t]" />
            </el-select>
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="materialForm.title" style="width: 160px" placeholder="材料名称" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="materialForm.content" style="width: 200px" placeholder="内容摘要" />
          </el-form-item>
          <el-form-item>
            <el-upload :show-file-list="false" accept="image/*" :http-request="uploadMaterialFile">
              <el-button size="small">{{ materialForm.fileUrl ? '已传附件✓' : '上传附件' }}</el-button>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" :loading="acting" @click="addMaterial">添加材料</el-button>
          </el-form-item>
        </el-form>

        <!-- 复发关联 -->
        <template v-if="rv.recurrences?.length">
          <el-divider content-position="left"><b>复发关联（同一井盖历史维修）</b></el-divider>
          <el-alert type="error" :closable="false" class="mb16"
                    title="同一井盖再次塌陷：上一轮维修单位、材料规格与验收人进入本次责任判断。" />
          <el-table :data="rv.recurrences" size="small" class="mb16">
            <el-table-column label="历史事件" width="150">
              <template #default="{ row }">
                <el-link type="primary" @click="$router.push(`/incidents/${row.incidentId}`)">{{ row.code }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="repairTeam" label="上一轮维修单位" min-width="130" />
            <el-table-column prop="coverSpec" label="材料规格" min-width="150" />
            <el-table-column prop="inspectorName" label="验收人" width="110" />
            <el-table-column label="验收时间" width="140">
              <template #default="{ row }">{{ fmtTime(row.inspectedAt) }}</template>
            </el-table-column>
            <el-table-column label="上次赔付责任" width="120">
              <template #default="{ row }">
                {{ row.compensationResponsibility ? RESPONSIBILITY[row.compensationResponsibility] : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 维修追踪状态 -->
        <template v-if="rv.followUp">
          <el-divider content-position="left"><b>赔付后维修追踪</b></el-divider>
          <el-tag :type="FOLLOWUP_STATUS[rv.followUp.status]?.type" effect="dark" size="small">
            {{ FOLLOWUP_STATUS[rv.followUp.status]?.label }}
          </el-tag>
          <div class="text-muted mt16" style="white-space: pre-wrap">{{ rv.followUp.checkNote }}</div>
        </template>

        <!-- 审核结论 -->
        <template v-if="canEdit">
          <el-divider content-position="left"><b>审核结论</b></el-divider>
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
                        placeholder="结合时间线核验结论、材料核验情况与复发关联综合分析" />
              <el-button v-if="rv.case.timelineAnalysis" size="small" class="mt16"
                         @click="decideForm.analysis = rv.case.timelineAnalysis">
                引用时间线核验结论
              </el-button>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="acting" @click="decide">提交审核</el-button>
            </el-form-item>
          </el-form>
        </template>
        <template v-else-if="rv.case.analysis">
          <el-divider content-position="left"><b>审核结论</b></el-divider>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="责任分析">{{ rv.case.analysis }}</el-descriptions-item>
            <el-descriptions-item label="责任认定">
              {{ rv.case.responsibility ? RESPONSIBILITY[rv.case.responsibility] : '-' }}
              <span v-if="rv.case.responsibility === 'SHARED'" class="text-muted">
                （权属 {{ rv.case.ownerRatio }}% / 街道 {{ rv.case.streetRatio }}%）
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="核定金额">{{ money(rv.case.approvedAmount) }}</el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ fmtTime(rv.case.decidedAt) }}</el-descriptions-item>
            <el-descriptions-item label="赔付时间">{{ fmtTime(rv.case.paidAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import {
  apiCompensations, apiCompReview, apiCompUpdate, apiCompDecide, apiCompPay,
  apiCompAddMaterial, apiCompVerifyMaterial, apiCompFollowUps, apiCompFollowUpComplete,
  apiCompRiskClues, apiIncidents, apiMeta, apiUpload
} from '../api'
import {
  COMP_STATUS, RESPONSIBILITY, MATERIAL_TYPE, MATERIAL_STATUS, TIMELINE_FLAG, FOLLOWUP_STATUS,
  fmtTime, money
} from '../utils/format'

const auth = useAuthStore()
const role = computed(() => auth.role)
const list = ref([])
const incidents = ref([])
const followUps = ref([])
const riskClues = ref([])
const meta = ref({})
const mainTab = ref('cases')
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

// ---------- 审核抽屉 ----------
const reviewVisible = ref(false)
const rv = ref(null)
const currentCase = ref(null)
const reviewForm = reactive({ accidentTime: null })
const materialForm = reactive({ type: 'DASHCAM', title: '', content: '', fileUrl: '' })
const decideForm = reactive({
  damageType: '', claimAmount: 0, accidentProof: '', result: 'APPROVED',
  responsibility: 'OWNER', ownerRatio: 100, streetRatio: 0, approvedAmount: 0, analysis: ''
})

const canEdit = computed(() => rv.value?.case?.status === 'FILED' && role.value === 'COMPENSATOR')

function openReview(row) {
  currentCase.value = row
  rv.value = null
  reviewVisible.value = true
}

async function loadReview() {
  const data = await apiCompReview(currentCase.value.id)
  rv.value = data
  reviewForm.accidentTime = data.case.accidentTime
  Object.assign(decideForm, {
    damageType: data.case.damageType === '待补充' ? '车辆损失' : data.case.damageType,
    claimAmount: data.case.claimAmount,
    accidentProof: data.case.accidentProof,
    result: 'APPROVED',
    responsibility: 'OWNER',
    ownerRatio: 100,
    streetRatio: 0,
    approvedAmount: data.case.claimAmount,
    analysis: ''
  })
}

async function saveAccidentTime() {
  if (!reviewForm.accidentTime) return ElMessage.warning('请选择事故时间')
  acting.value = true
  try {
    await apiCompUpdate(currentCase.value.id, { accidentTime: reviewForm.accidentTime })
    ElMessage.success('已保存事故时间并完成时间线核验')
    await loadReview()
    load()
  } finally {
    acting.value = false
  }
}

async function uploadMaterialFile({ file }) {
  materialForm.fileUrl = await apiUpload(file)
  ElMessage.success('附件已上传')
}

async function addMaterial() {
  if (!materialForm.title.trim()) return ElMessage.warning('请填写材料名称')
  acting.value = true
  try {
    await apiCompAddMaterial(currentCase.value.id, { ...materialForm })
    ElMessage.success('材料已添加')
    Object.assign(materialForm, { type: 'DASHCAM', title: '', content: '', fileUrl: '' })
    await loadReview()
  } finally {
    acting.value = false
  }
}

async function verifyMaterial(material, status) {
  let verifyNote = ''
  if (status === 'DOUBTFUL') {
    const { value } = await ElMessageBox.prompt('请填写存疑原因', '材料存疑', {
      inputValidator: v => (v && v.trim() ? true : '请填写存疑原因')
    })
    verifyNote = value.trim()
  }
  await apiCompVerifyMaterial(material.id, { status, verifyNote })
  ElMessage.success('核验完成')
  await loadReview()
}

async function decide() {
  if (!decideForm.analysis.trim()) return ElMessage.warning('请填写责任分析')
  if (decideForm.responsibility === 'SHARED'
      && decideForm.ownerRatio + decideForm.streetRatio !== 100) {
    return ElMessage.warning('分摊比例之和须为 100%')
  }
  acting.value = true
  try {
    await apiCompUpdate(currentCase.value.id, {
      damageType: decideForm.damageType,
      claimAmount: decideForm.claimAmount,
      accidentProof: decideForm.accidentProof,
      accidentTime: reviewForm.accidentTime || undefined
    })
    await apiCompDecide(currentCase.value.id, decideForm)
    ElMessage.success('审核完成，已生成维修追踪')
    reviewVisible.value = false
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

async function completeFollowUp(row) {
  const { value } = await ElMessageBox.prompt(
    `确认事件 ${row.incidentCode} 维修已彻底完成？请填写追踪说明`, '维修追踪确认', {
      inputValue: '验收通过，维修彻底完成，无复发迹象。',
      inputValidator: v => (v && v.trim() ? true : '请填写追踪说明')
    })
  await apiCompFollowUpComplete(row.followUp.id, { checkNote: value.trim() })
  ElMessage.success('已确认维修彻底完成')
  load()
}

async function load() {
  list.value = await apiCompensations()
  incidents.value = await apiIncidents()
  followUps.value = await apiCompFollowUps()
  riskClues.value = await apiCompRiskClues()
}

onMounted(async () => {
  await load()
  meta.value = await apiMeta()
})
</script>
