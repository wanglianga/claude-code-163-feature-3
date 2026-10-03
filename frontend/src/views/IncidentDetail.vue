<template>
  <div class="page" v-if="d">
    <!-- 头部状态 -->
    <el-card class="page-card">
      <div class="flex-between">
        <div>
          <span style="font-size: 20px; font-weight: 700">{{ d.incident.code }}</span>
          <el-tag style="margin-left: 10px" :type="INCIDENT_STATUS[d.incident.status]?.type">
            {{ INCIDENT_STATUS[d.incident.status]?.label }}
          </el-tag>
          <el-tag v-if="d.incident.responsibilityPending" type="danger" effect="dark" style="margin-left: 6px">
            责任待定
          </el-tag>
        </div>
        <div>
          <span class="text-muted">应急线：</span>
          <el-tag :type="EMERGENCY_STATUS[d.incident.emergencyStatus]?.type" effect="plain">
            {{ EMERGENCY_STATUS[d.incident.emergencyStatus]?.label }}
          </el-tag>
          <span class="text-muted" style="margin-left: 12px">权属线：</span>
          <el-tag :type="OWNER_STATUS[d.incident.ownerStatus]?.type" effect="plain">
            {{ OWNER_STATUS[d.incident.ownerStatus]?.label }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :md="15">
        <!-- 上报信息 -->
        <el-card class="page-card">
          <template #header><b>上报信息</b></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="问题类型">{{ d.incident.issueType }}</el-descriptions-item>
            <el-descriptions-item label="危险程度">
              <el-tag :type="dangerType(d.incident.dangerLevel)" size="small">{{ d.incident.dangerLevel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="详细地址" :span="2">{{ d.incident.address }}</el-descriptions-item>
            <el-descriptions-item label="道路类型">{{ d.incident.roadType }}</el-descriptions-item>
            <el-descriptions-item label="经纬度">
              {{ d.incident.longitude }}, {{ d.incident.latitude }}
            </el-descriptions-item>
            <el-descriptions-item label="上报人">{{ d.reporterName }}</el-descriptions-item>
            <el-descriptions-item label="上报时间">{{ fmtTime(d.incident.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="人车损伤" :span="2">
              <el-tag v-if="d.incident.hasDamage" type="danger" size="small">有损伤</el-tag>
              <span v-else>无</span>
              <span v-if="d.incident.damageDesc" style="margin-left: 8px">{{ d.incident.damageDesc }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="d.ownerOrg" label="权属单位" :span="2">
              <el-tag type="success">{{ d.ownerOrg }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="d.photoList?.length" class="mt16">
            <el-image v-for="(p, i) in d.photoList" :key="i" :src="p" :preview-src-list="d.photoList"
                      fit="cover" style="width: 110px; height: 110px; margin-right: 8px; border-radius: 4px" />
          </div>
        </el-card>

        <!-- 复发关联：同一井盖历史维修 -->
        <el-card v-if="d.recurrences?.length" class="page-card">
          <template #header>
            <div class="flex-between">
              <b>复发关联（同一井盖历史维修）</b>
              <el-tag type="danger" effect="dark" size="small">同一井盖再次塌陷</el-tag>
            </div>
          </template>
          <el-alert type="warning" :closable="false" class="mb16"
                    title="该井盖存在历史维修记录，上一轮维修单位、材料规格与验收人进入本次责任判断。" />
          <el-table :data="d.recurrences" size="small">
            <el-table-column label="历史事件" width="150">
              <template #default="{ row }">
                <el-link type="primary" @click="$router.push(`/incidents/${row.incidentId}`)">{{ row.code }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="issueType" label="问题" width="70" />
            <el-table-column prop="repairTeam" label="上一轮维修单位" min-width="130" />
            <el-table-column prop="coverSpec" label="材料规格" min-width="150" />
            <el-table-column prop="inspectorName" label="验收人" width="110" />
            <el-table-column label="验收时间" width="140">
              <template #default="{ row }">{{ fmtTime(row.inspectedAt) }}</template>
            </el-table-column>
            <el-table-column label="上次赔付责任" width="110">
              <template #default="{ row }">
                {{ row.compensationResponsibility ? RESPONSIBILITY[row.compensationResponsibility] : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 应急处置记录 -->
        <el-card v-if="d.emergency" class="page-card">
          <template #header><b>应急处置记录（应急线）</b></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="处置人">{{ d.emergencyOfficer }}</el-descriptions-item>
            <el-descriptions-item label="围护措施">
              <el-tag v-for="m in (d.emergency.measures || '').split(',')" :key="m" size="small"
                      style="margin-right: 4px">{{ m }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="派单时间">{{ fmtTime(d.emergency.dispatchedAt) }}</el-descriptions-item>
            <el-descriptions-item label="到场时间">{{ fmtTime(d.emergency.arrivedAt) }}</el-descriptions-item>
            <el-descriptions-item label="围护完成">{{ fmtTime(d.emergency.securedAt) }}</el-descriptions-item>
            <el-descriptions-item label="应急费用（街道垫付）">{{ money(d.emergency.cost) }}</el-descriptions-item>
            <el-descriptions-item v-if="d.emergency.note" label="备注" :span="2">{{ d.emergency.note }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 权属排查记录 -->
        <el-card v-if="d.investigations?.length" class="page-card">
          <template #header><b>权属排查记录（权属线）</b></template>
          <div v-for="(item, i) in d.investigations" :key="i" style="margin-bottom: 12px">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="排查人">
                {{ item.investigatorName }} · {{ fmtTime(item.investigation.createdAt) }}
                <el-tag :type="item.investigation.conclusion === 'CONFIRMED' ? 'success' : 'danger'"
                        size="small" style="margin-left: 8px">
                  {{ item.investigation.conclusion === 'CONFIRMED' ? '确认权属' : '无法确认（责任待定）' }}
                </el-tag>
                <el-tag v-if="item.confirmedOwnerOrg" type="success" size="small" style="margin-left: 4px">
                  {{ item.confirmedOwnerOrg }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="井盖标识">{{ item.investigation.manholeMarking || '-' }}</el-descriptions-item>
              <el-descriptions-item label="地下管线">{{ item.investigation.pipelineInfo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="历史施工">{{ item.investigation.constructionHistory || '-' }}</el-descriptions-item>
              <el-descriptions-item label="周边商户">{{ item.investigation.merchantInfo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="城管台账">{{ item.investigation.cityLedger || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>

        <!-- 认领记录 -->
        <el-card v-if="d.claims?.length" class="page-card">
          <template #header><b>权属认领记录</b></template>
          <el-timeline>
            <el-timeline-item v-for="(c, i) in d.claims" :key="i" :timestamp="fmtTime(c.claim.createdAt)" type="success">
              <b>{{ c.ownerOrg }}</b> {{ c.claim.claimType }}
              <span v-if="c.claim.note" class="text-muted">（{{ c.claim.note }}）</span>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 维修工单 -->
        <el-card v-if="d.repairOrder" class="page-card">
          <template #header><b>维修工单</b></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="维修队">{{ d.repairTeamName }}</el-descriptions-item>
            <el-descriptions-item label="工单状态">
              <el-tag :type="REPAIR_STATUS[d.repairOrder.status]?.type" size="small">
                {{ REPAIR_STATUS[d.repairOrder.status]?.label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="派单时间">{{ fmtTime(d.repairOrder.assignedAt) }}</el-descriptions-item>
            <el-descriptions-item label="到场时间">{{ fmtTime(d.repairOrder.arrivedAt) }}</el-descriptions-item>
            <el-descriptions-item label="井室状况">{{ d.repairOrder.chamberCondition || '-' }}</el-descriptions-item>
            <el-descriptions-item label="井盖规格">{{ d.repairOrder.coverSpec || '-' }}</el-descriptions-item>
            <el-descriptions-item label="需要封路">{{ d.repairOrder.needRoadClosure ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="专业协同">
              {{ d.repairOrder.needCoord ? `涉及：${d.repairOrder.coordUnits}` : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="维修费用">{{ money(d.repairOrder.repairCost) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ fmtTime(d.repairOrder.finishedAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="d.repairOrder.repairNote" label="维修说明" :span="2">
              {{ d.repairOrder.repairNote }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 验收记录 -->
        <el-card v-if="d.acceptances?.length" class="page-card">
          <template #header><b>验收记录</b></template>
          <div v-for="(a, i) in d.acceptances" :key="i" style="margin-bottom: 10px">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="巡查员">{{ a.inspectorName }}</el-descriptions-item>
              <el-descriptions-item label="结论">
                <el-tag :type="a.acceptance.result === 'PASSED' ? 'success' : 'danger'" size="small">
                  {{ a.acceptance.result === 'PASSED' ? '验收通过' : '验收不通过' }}
                </el-tag>
                <span class="text-muted" style="margin-left: 8px">{{ fmtTime(a.acceptance.inspectedAt) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="验收项" :span="2">
                <el-tag :type="a.acceptance.photoOk ? 'success' : 'danger'" size="small" effect="plain">照片比对</el-tag>
                <el-tag :type="a.acceptance.locationOk ? 'success' : 'danger'" size="small" effect="plain" style="margin-left: 4px">定位一致</el-tag>
                <el-tag :type="a.acceptance.flatnessOk ? 'success' : 'danger'" size="small" effect="plain" style="margin-left: 4px">路面平整度</el-tag>
                <el-tag :type="a.acceptance.rollingTestOk ? 'success' : 'danger'" size="small" effect="plain" style="margin-left: 4px">车辆碾压测试</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="a.acceptance.note" label="备注" :span="2">{{ a.acceptance.note }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>

        <!-- 赔付案件 -->
        <el-card v-if="d.compensation" class="page-card">
          <template #header>
            <div class="flex-between">
              <b>赔付案件</b>
              <el-tag :type="COMP_STATUS[d.compensation.status]?.type" size="small">
                {{ COMP_STATUS[d.compensation.status]?.label }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="索赔人">{{ d.compensation.claimantName }}</el-descriptions-item>
            <el-descriptions-item label="损失类型">{{ d.compensation.damageType }}</el-descriptions-item>
            <el-descriptions-item label="索赔金额">{{ money(d.compensation.claimAmount) }}</el-descriptions-item>
            <el-descriptions-item label="核定金额">{{ money(d.compensation.approvedAmount) }}</el-descriptions-item>
            <el-descriptions-item label="事故证明" :span="2">{{ d.compensation.accidentProof || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="d.compensation.analysis" label="责任分析" :span="2">
              {{ d.compensation.analysis }}
            </el-descriptions-item>
            <el-descriptions-item v-if="d.compensation.responsibility" label="责任认定" :span="2">
              {{ RESPONSIBILITY[d.compensation.responsibility] }}
              <span v-if="d.compensation.responsibility === 'SHARED'" class="text-muted">
                （权属 {{ d.compensation.ownerRatio }}% / 街道 {{ d.compensation.streetRatio }}%）
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 费用分摊 -->
        <el-card v-if="d.costAllocation" class="page-card">
          <template #header>
            <div class="flex-between">
              <b>费用分摊</b>
              <el-tag :type="COST_STATUS[d.costAllocation.status]?.type" size="small">
                {{ COST_STATUS[d.costAllocation.status]?.label }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="4" border>
            <el-descriptions-item label="应急费">{{ money(d.costAllocation.emergencyCost) }}</el-descriptions-item>
            <el-descriptions-item label="维修费">{{ money(d.costAllocation.repairCost) }}</el-descriptions-item>
            <el-descriptions-item label="赔付金">{{ money(d.costAllocation.compensationAmount) }}</el-descriptions-item>
            <el-descriptions-item label="合计"><b>{{ money(d.costAllocation.totalAmount) }}</b></el-descriptions-item>
          </el-descriptions>
          <el-table :data="allocList" size="small" class="mt16">
            <el-table-column prop="party" label="分摊方" min-width="180" />
            <el-table-column label="金额" width="130">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="依据" min-width="220" />
          </el-table>
          <div v-if="d.costAllocation.reviewNote" class="text-muted mt16">
            复核意见：{{ d.costAllocation.reviewNote }}
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：操作面板 + 时间线 -->
      <el-col :xs="24" :md="9">
        <el-card v-if="actions.length" class="page-card">
          <template #header><b>可执行操作</b></template>

          <!-- 街道：应急派单 -->
          <div v-if="actions.includes('dispatch')" class="mb16">
            <el-alert type="error" :closable="false" title="事件待应急处置，请立即派单到场围护" class="mb16" />
            <el-button type="danger" style="width: 100%" :loading="acting" @click="doDispatch">
              立即派单（街道值班员）
            </el-button>
          </div>

          <!-- 街道：完成围护 -->
          <div v-if="actions.includes('secure')">
            <el-divider content-position="left">完成临时围护</el-divider>
            <el-form label-width="90px">
              <el-form-item label="围护措施">
                <el-checkbox-group v-model="secureForm.measures">
                  <el-checkbox v-for="m in meta.measureTypes || []" :key="m" :value="m">{{ m }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item label="应急费用">
                <el-input-number v-model="secureForm.cost" :min="0" :precision="2" />
                <span class="text-muted" style="margin-left: 6px">元（街道先行垫付）</span>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="secureForm.note" type="textarea" :rows="2" />
              </el-form-item>
              <el-button type="warning" style="width: 100%" :loading="acting" @click="doSecure">
                确认到场并完成围护
              </el-button>
            </el-form>
          </div>

          <!-- 街道：权属排查 -->
          <div v-if="actions.includes('investigate')">
            <el-divider content-position="left">权属排查（五类证据）</el-divider>
            <el-form label-width="90px">
              <el-form-item label="井盖标识">
                <el-input v-model="invForm.manholeMarking" placeholder="铸字、编号、权属标记" />
              </el-form-item>
              <el-form-item label="地下管线">
                <el-input v-model="invForm.pipelineInfo" placeholder="管线资料比对" />
              </el-form-item>
              <el-form-item label="历史施工">
                <el-input v-model="invForm.constructionHistory" placeholder="施工记录查询" />
              </el-form-item>
              <el-form-item label="周边商户">
                <el-input v-model="invForm.merchantInfo" placeholder="走访情况" />
              </el-form-item>
              <el-form-item label="城管台账">
                <el-input v-model="invForm.cityLedger" placeholder="台账登记信息" />
              </el-form-item>
              <el-form-item label="排查结论">
                <el-radio-group v-model="invForm.conclusion">
                  <el-radio value="CONFIRMED">确认权属</el-radio>
                  <el-radio value="PENDING">无法确认（责任待定）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="invForm.conclusion === 'CONFIRMED'" label="权属单位">
                <el-select v-model="invForm.confirmedOwnerId" style="width: 100%">
                  <el-option v-for="o in meta.ownerUnits || []" :key="o.id" :value="o.id" :label="o.orgName" />
                </el-select>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="invForm.note" type="textarea" :rows="2" />
              </el-form-item>
              <el-button type="primary" style="width: 100%" :loading="acting" @click="doInvestigate">
                提交排查结论
              </el-button>
            </el-form>
          </div>

          <!-- 权属单位：认领 -->
          <div v-if="actions.includes('claim')">
            <el-alert type="warning" :closable="false" title="经核实该井盖属我单位权属的，请主动认领" class="mb16" />
            <el-input v-model="claimNote" placeholder="认领说明（可选）" class="mb16" />
            <el-button type="primary" style="width: 100%" :loading="acting" @click="doClaim">
              主动认领该井盖
            </el-button>
          </div>

          <!-- 权属单位：派单 -->
          <div v-if="actions.includes('assign')">
            <el-divider content-position="left">派发维修工单</el-divider>
            <el-select v-model="assignTeamId" placeholder="选择维修队" style="width: 100%" class="mb16">
              <el-option v-for="t in meta.repairTeams || []" :key="t.id" :value="t.id" :label="t.orgName" />
            </el-select>
            <el-button type="primary" style="width: 100%" :loading="acting" @click="doAssign">
              派发工单
            </el-button>
          </div>

          <!-- 维修队：接单 -->
          <div v-if="actions.includes('accept')">
            <el-button type="primary" style="width: 100%" :loading="acting" @click="doAccept">
              接单（维修队）
            </el-button>
          </div>

          <!-- 巡查员：验收 -->
          <div v-if="actions.includes('inspect')">
            <el-divider content-position="left">验收（四项检查）</el-divider>
            <el-form label-width="110px">
              <el-form-item label="照片比对">
                <el-switch v-model="accForm.photoOk" active-text="一致" />
              </el-form-item>
              <el-form-item label="定位一致">
                <el-switch v-model="accForm.locationOk" active-text="一致" />
              </el-form-item>
              <el-form-item label="路面平整度">
                <el-switch v-model="accForm.flatnessOk" active-text="合格" />
              </el-form-item>
              <el-form-item label="车辆碾压测试">
                <el-switch v-model="accForm.rollingTestOk" active-text="通过" />
              </el-form-item>
              <el-form-item label="验收结论">
                <el-radio-group v-model="accForm.result">
                  <el-radio value="PASSED">通过</el-radio>
                  <el-radio value="FAILED">不通过（退回重修）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="accForm.note" type="textarea" :rows="2" />
              </el-form-item>
              <el-button type="success" style="width: 100%" :loading="acting" @click="doInspect">
                提交验收结论
              </el-button>
            </el-form>
          </div>

          <!-- 管理员：费用复核 -->
          <div v-if="actions.includes('costReview')">
            <el-divider content-position="left">费用分摊复核</el-divider>
            <div v-for="(a, i) in costForm.allocations" :key="i" style="display: flex; gap: 6px; margin-bottom: 8px">
              <el-input v-model="a.party" placeholder="分摊方" style="flex: 2" />
              <el-input-number v-model="a.amount" :min="0" :precision="2" style="flex: 1" />
              <el-button text type="danger" @click="costForm.allocations.splice(i, 1)">删</el-button>
            </div>
            <el-button size="small" @click="costForm.allocations.push({ party: '', amount: 0, reason: '' })">
              + 添加分摊方
            </el-button>
            <el-input v-model="costForm.reviewNote" placeholder="复核意见" type="textarea" :rows="2"
                      class="mt16 mb16" />
            <el-button type="primary" style="width: 100%" :loading="acting" @click="doCostReview">
              确认分摊并结案（合计须等于 {{ money(d.costAllocation?.totalAmount) }}）
            </el-button>
          </div>
        </el-card>

        <!-- 流程时间线 -->
        <el-card class="page-card">
          <template #header><b>全流程时间线</b></template>
          <el-timeline>
            <el-timeline-item v-for="(t, i) in timeline" :key="i" :timestamp="t.time"
                              :type="t.type" :hollow="!t.done">
              {{ t.label }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import {
  apiIncidentDetail, apiMeta, apiDispatch, apiSecure, apiInvestigate, apiClaim,
  apiCreateOrder, apiAcceptOrder, apiAcceptance, apiCostReview
} from '../api'
import {
  INCIDENT_STATUS, EMERGENCY_STATUS, OWNER_STATUS, REPAIR_STATUS, COMP_STATUS,
  COST_STATUS, RESPONSIBILITY, dangerType, fmtTime, money
} from '../utils/format'

const route = useRoute()
const auth = useAuthStore()
const id = route.params.id
const d = ref(null)
const meta = ref({})
const acting = ref(false)
const claimNote = ref('')
const assignTeamId = ref(null)

const secureForm = reactive({ measures: ['警示锥'], cost: 0, note: '' })
const invForm = reactive({
  manholeMarking: '', pipelineInfo: '', constructionHistory: '',
  merchantInfo: '', cityLedger: '', conclusion: 'CONFIRMED', confirmedOwnerId: null, note: ''
})
const accForm = reactive({
  photoOk: true, locationOk: true, flatnessOk: true, rollingTestOk: true, result: 'PASSED', note: ''
})
const costForm = reactive({ allocations: [], reviewNote: '' })

const role = computed(() => auth.role)
const inc = computed(() => d.value?.incident || {})

const actions = computed(() => {
  if (!d.value) return []
  const i = inc.value
  const list = []
  if (['STREET_DUTY', 'ADMIN'].includes(role.value)) {
    if (i.emergencyStatus === 'PENDING') list.push('dispatch')
    if (i.emergencyStatus !== 'SECURED') list.push('secure')
    if (['INVESTIGATING', 'PENDING'].includes(i.ownerStatus)) list.push('investigate')
  }
  if (role.value === 'OWNER_UNIT') {
    if (i.ownerStatus !== 'CONFIRMED') list.push('claim')
    if (i.ownerStatus === 'CONFIRMED' && i.ownerId === auth.user.id && !d.value.repairOrder
        && i.status === 'OWNER_CONFIRMED') list.push('assign')
  }
  if (role.value === 'REPAIR_TEAM' && d.value.repairOrder
      && ['ASSIGNED', 'RETURNED'].includes(d.value.repairOrder.status)
      && d.value.repairOrder.teamId === auth.user.id) list.push('accept')
  if (role.value === 'INSPECTOR' && i.status === 'REPAIRED') list.push('inspect')
  if (role.value === 'ADMIN' && d.value.costAllocation?.status === 'PENDING') list.push('costReview')
  return list
})

const allocList = computed(() => {
  try {
    return JSON.parse(d.value?.costAllocation?.allocations || '[]')
  } catch {
    return []
  }
})

const timeline = computed(() => {
  if (!d.value) return []
  const i = inc.value
  const em = d.value.emergency
  const o = d.value.repairOrder
  const t = []
  t.push({ label: `市民上报（${i.issueType} · 危险${i.dangerLevel}）`, time: fmtTime(i.createdAt), done: true, type: 'primary' })
  t.push({ label: '街道派单应急处置', time: em ? fmtTime(em.dispatchedAt) : '待派单', done: !!em, type: em ? 'warning' : 'info' })
  t.push({ label: `到场临时围护${em?.measures ? '（' + em.measures + '）' : ''}`, time: em?.securedAt ? fmtTime(em.securedAt) : '待围护', done: !!em?.securedAt, type: em?.securedAt ? 'warning' : 'info' })
  const inv = d.value.investigations?.[0]
  t.push({ label: inv ? `权属排查：${inv.investigation.conclusion === 'CONFIRMED' ? '确认权属' : '责任待定'}` : '权属排查', time: inv ? fmtTime(inv.investigation.createdAt) : '待排查', done: !!inv, type: inv ? 'primary' : 'info' })
  const cl = d.value.claims?.[0]
  t.push({ label: cl ? `权属确认：${cl.ownerOrg}（${cl.claim.claimType}）` : '权属确认', time: cl ? fmtTime(cl.claim.createdAt) : '待确认', done: !!cl, type: cl ? 'success' : 'info' })
  t.push({ label: o ? `派发维修工单（${d.value.repairTeamName}）` : '派发维修工单', time: o ? fmtTime(o.assignedAt) : '待派单', done: !!o, type: o ? 'primary' : 'info' })
  t.push({ label: '维修完成', time: o?.finishedAt ? fmtTime(o.finishedAt) : '待维修', done: !!o?.finishedAt, type: o?.finishedAt ? 'primary' : 'info' })
  const acc = d.value.acceptances?.find(a => a.acceptance.result === 'PASSED')
  t.push({ label: '验收通过', time: acc ? fmtTime(acc.acceptance.inspectedAt) : '待验收', done: !!acc, type: acc ? 'success' : 'info' })
  if (d.value.compensation) {
    t.push({ label: `赔付：${COMP_STATUS[d.value.compensation.status]?.label}`, time: d.value.compensation.paidAt ? fmtTime(d.value.compensation.paidAt) : (d.value.compensation.decidedAt ? fmtTime(d.value.compensation.decidedAt) : '审核中'), done: d.value.compensation.status === 'PAID', type: 'warning' })
  }
  t.push({ label: '费用分摊复核结案', time: i.closedAt ? fmtTime(i.closedAt) : '待复核', done: i.status === 'CLOSED', type: i.status === 'CLOSED' ? 'success' : 'info' })
  return t
})

async function load() {
  d.value = await apiIncidentDetail(id)
  if (d.value.costAllocation?.allocations) {
    costForm.allocations = allocList.value.map(a => ({ ...a }))
  }
}

async function run(fn, successMsg) {
  acting.value = true
  try {
    await fn()
    ElMessage.success(successMsg)
    await load()
  } finally {
    acting.value = false
  }
}

const doDispatch = () => run(() => apiDispatch(id), '已派单，请尽快到场')
const doSecure = () => {
  if (!secureForm.measures.length) return ElMessage.warning('请选择围护措施')
  run(() => apiSecure(id, secureForm), '临时围护已完成')
}
const doInvestigate = () => {
  if (invForm.conclusion === 'CONFIRMED' && !invForm.confirmedOwnerId) {
    return ElMessage.warning('请选择权属单位')
  }
  run(() => apiInvestigate(id, invForm), '排查结论已提交')
}
const doClaim = () => run(() => apiClaim(id, { note: claimNote.value }), '认领成功，请尽快派单维修')
const doAssign = () => {
  if (!assignTeamId.value) return ElMessage.warning('请选择维修队')
  run(() => apiCreateOrder(id, { teamId: assignTeamId.value }), '工单已派发')
}
const doAccept = () => run(() => apiAcceptOrder(d.value.repairOrder.id), '已接单')
const doInspect = () => run(() => apiAcceptance(id, accForm), '验收结论已提交')
const doCostReview = async () => {
  const total = costForm.allocations.reduce((s, a) => s + Number(a.amount || 0), 0)
  if (Math.abs(total - Number(d.value.costAllocation.totalAmount)) > 0.001) {
    return ElMessage.warning(`分摊合计 ${total} 与总费用 ${d.value.costAllocation.totalAmount} 不一致`)
  }
  await ElMessageBox.confirm('分摊复核后事件将结案，确认提交？', '费用复核', { type: 'warning' })
  run(() => apiCostReview(d.value.costAllocation.id, costForm), '费用分摊完成，事件已结案')
}

onMounted(async () => {
  await load()
  meta.value = await apiMeta()
})
</script>
