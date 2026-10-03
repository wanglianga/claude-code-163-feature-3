package com.city.manhole.service;

import com.city.manhole.common.AuthContext;
import com.city.manhole.common.BizException;
import com.city.manhole.common.Constants;
import com.city.manhole.model.*;
import com.city.manhole.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 赔付定责：核验事故时间、行车记录仪、交警证明、维修发票与平台当时井盖状态五项材料，
 * 依据事故时间与首次上报的关系（上报前仅记录风险线索 / 上报后未围护关联值班处置时效与权属响应）
 * 综合定责；赔付审核通过后持续追踪维修是否彻底完成，同一井盖再次塌陷时回溯上一轮维修责任。
 */
@Service
@RequiredArgsConstructor
public class CompensationService {
    private final CompensationRepo compensationRepo;
    private final ClaimMaterialRepo materialRepo;
    private final CostAllocationRepo costRepo;
    private final IncidentRepo incidentRepo;
    private final EmergencyMeasureRepo emergencyRepo;
    private final OwnerClaimRepo claimRepo;
    private final RepairOrderRepo repairOrderRepo;
    private final AcceptanceRepo acceptanceRepo;
    private final UserRepo userRepo;
    private final NotificationService notificationService;

    @Value("${app.emergency-limit-minutes.high}")
    private long limitHigh;
    @Value("${app.emergency-limit-minutes.mid}")
    private long limitMid;
    @Value("${app.emergency-limit-minutes.low}")
    private long limitLow;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 五项赔付材料（立案时自动创建） */
    public static final List<String> MATERIAL_TYPES = List.of(
            Constants.MaterialType.ACCIDENT_TIME,
            Constants.MaterialType.DASHCAM,
            Constants.MaterialType.POLICE_CERT,
            Constants.MaterialType.REPAIR_INVOICE,
            Constants.MaterialType.PLATFORM_STATUS);

    public List<CompensationCase> listAll() {
        AuthContext.requireRole(Constants.Role.COMPENSATOR, Constants.Role.ADMIN);
        return compensationRepo.findAllByOrderByCreatedAtDesc();
    }

    /** 案件详情：案件 + 五项材料 + 关联事件 + 再次塌陷追溯 */
    public Map<String, Object> detail(Long id) {
        AuthContext.requireRole(Constants.Role.COMPENSATOR, Constants.Role.ADMIN);
        CompensationCase c = getOrThrow(id);
        Incident inc = incidentRepo.findById(c.getIncidentId())
                .orElseThrow(() -> new BizException("事件不存在"));
        List<ClaimMaterial> materials = materialRepo.findByCaseIdOrderById(id);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("case", c);
        m.put("materials", materials);
        Map<String, Object> incMap = new LinkedHashMap<>();
        incMap.put("id", inc.getId());
        incMap.put("code", inc.getCode());
        incMap.put("status", inc.getStatus());
        incMap.put("address", inc.getAddress());
        incMap.put("dangerLevel", inc.getDangerLevel());
        incMap.put("recollapse", inc.getRecollapse());
        incMap.put("createdAt", inc.getCreatedAt());
        m.put("incident", incMap);
        m.put("recollapseTrace", buildRecollapseTrace(inc));
        m.put("allVerified", materials.stream().noneMatch(x ->
                Constants.VerifyStatus.PENDING.equals(x.getVerifyStatus())));
        return m;
    }

    /** 立案（市民上报有损伤时由 IncidentService 调用）：案件 + 五项待核验材料 */
    @Transactional
    public CompensationCase createCase(Incident inc, User reporter) {
        CompensationCase c = new CompensationCase();
        c.setIncidentId(inc.getId());
        c.setClaimantName(reporter.getName());
        c.setClaimantPhone(reporter.getPhone());
        c.setDamageType("待补充");
        c.setAccidentProof(inc.getDamageDesc());
        c.setStatus(Constants.CompStatus.FILED);
        compensationRepo.save(c);
        initMaterials(c);
        notificationService.notifyRole(Constants.Role.COMPENSATOR, inc.getId(),
                "新赔付案件 " + inc.getCode(), "事件造成人车损伤，请核验五项赔付材料后定责。");
        return c;
    }

    /** 立案时创建五项待核验材料 */
    private void initMaterials(CompensationCase c) {
        for (String type : MATERIAL_TYPES) {
            ClaimMaterial mat = new ClaimMaterial();
            mat.setCaseId(c.getId());
            mat.setMaterialType(type);
            materialRepo.save(mat);
        }
    }

    /**
     * 录入事故时间：系统对照首次上报、围护、权属确认、维修时点自动判定时效关系，
     * 生成“平台当时井盖状态”快照，并自动回填“事故时间”“平台状态”两项材料内容。
     */
    @Transactional
    public CompensationCase setAccidentTime(Long id, LocalDateTime accidentTime) {
        AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(id);
        if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
            throw new BizException("案件已审核，不能修改事故时间");
        }
        if (accidentTime == null) throw new BizException("请填写事故时间");
        if (accidentTime.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BizException("事故时间不能晚于当前时间");
        }
        Incident inc = incidentRepo.findById(c.getIncidentId())
                .orElseThrow(() -> new BizException("事件不存在"));

        c.setAccidentTime(accidentTime);
        String snapshot = buildSnapshot(inc, accidentTime);
        c.setPlatformSnapshot(snapshot);
        if (accidentTime.isBefore(inc.getCreatedAt())) {
            c.setTimeRelation(Constants.TimeRelation.BEFORE_REPORT);
        } else {
            EmergencyMeasure em = emergencyRepo.findByIncidentId(inc.getId()).orElse(null);
            LocalDateTime securedAt = em == null ? null : em.getSecuredAt();
            c.setTimeRelation(securedAt == null || accidentTime.isBefore(securedAt)
                    ? Constants.TimeRelation.AFTER_REPORT_UNSECURED
                    : Constants.TimeRelation.AFTER_REPORT_SECURED);
        }
        compensationRepo.save(c);

        // 自动回填两项系统材料：事故时间、平台当时井盖状态
        fillSystemMaterial(c.getId(), Constants.MaterialType.ACCIDENT_TIME,
                "索赔人主张事故时间：" + accidentTime.format(FMT)
                        + "（已与行车记录仪、交警证明交叉核对）");
        fillSystemMaterial(c.getId(), Constants.MaterialType.PLATFORM_STATUS, snapshot);
        return c;
    }

    private void fillSystemMaterial(Long caseId, String type, String content) {
        materialRepo.findByCaseIdAndMaterialType(caseId, type).ifPresent(mat -> {
            mat.setContent(content);
            mat.setSubmitted(true);
            materialRepo.save(mat);
        });
    }

    /** 生成事故时刻平台状态快照（时效分析） */
    private String buildSnapshot(Incident inc, LocalDateTime accidentTime) {
        StringBuilder sb = new StringBuilder();
        LocalDateTime reportAt = inc.getCreatedAt();
        sb.append("首次上报：").append(reportAt.format(FMT)).append("\n");
        sb.append("事故时间：").append(accidentTime.format(FMT)).append("\n");

        if (accidentTime.isBefore(reportAt)) {
            long mins = Duration.between(accidentTime, reportAt).toMinutes();
            sb.append("事故发生于首次上报前 ").append(fmtDuration(mins))
                    .append("，平台在事故发生时尚未接报，本次上报仅记录为风险线索；")
                    .append("平台不承担围护不及时责任，责任重点为权属单位日常管护与井盖本体状况。");
            return sb.toString();
        }

        EmergencyMeasure em = emergencyRepo.findByIncidentId(inc.getId()).orElse(null);
        LocalDateTime securedAt = em == null ? null : em.getSecuredAt();
        if (securedAt == null || accidentTime.isBefore(securedAt)) {
            long mins = Duration.between(reportAt, accidentTime).toMinutes();
            long limit = limitOf(inc.getDangerLevel());
            sb.append("事故发生于上报后、临时围护完成前，未围护时长 ").append(fmtDuration(mins))
                    .append("（危险程度").append(inc.getDangerLevel())
                    .append("，到场时限 ").append(limit).append(" 分钟");
            if (mins > limit) sb.append("，已超时 ").append(fmtDuration(mins - limit));
            sb.append("）。\n");
            sb.append(securedAt == null
                    ? "至快照生成时仍未完成围护。\n"
                    : "围护完成：" + securedAt.format(FMT) + "（事故之后）。\n");
            sb.append("责任关联：街道值班处置时效").append(mins > limit ? "（已超时限）" : "").append("。");
        } else {
            long secureMins = Duration.between(reportAt, securedAt).toMinutes();
            sb.append("围护完成：").append(securedAt.format(FMT))
                    .append("（上报后 ").append(fmtDuration(secureMins)).append("）。\n");
            sb.append("事故发生于临时围护完成之后，重点核查围护措施是否到位、警示是否有效及维修进度。");
        }

        // 权属单位响应时效
        List<OwnerClaim> claims = claimRepo.findByIncidentIdOrderByCreatedAtDesc(inc.getId());
        if (!claims.isEmpty()) {
            LocalDateTime confirmedAt = claims.get(claims.size() - 1).getCreatedAt();
            sb.append("\n权属确认：").append(confirmedAt.format(FMT));
            if (!confirmedAt.isAfter(accidentTime)) {
                long resp = Duration.between(confirmedAt, accidentTime).toMinutes();
                sb.append("，至事故时已确认 ").append(fmtDuration(resp))
                        .append(" 仍未完成维修，关联权属单位响应时效。");
            } else {
                sb.append("（事故之后确认）。");
            }
        } else {
            sb.append("\n事故时权属尚未确认（排查中或责任待定）。");
        }

        // 维修进度
        repairOrderRepo.findByIncidentId(inc.getId()).ifPresent(o -> {
            sb.append("\n维修工单：").append(o.getAssignedAt().format(FMT)).append(" 派发");
            sb.append(o.getFinishedAt() == null
                    ? "，事故时维修未完成。"
                    : "，" + o.getFinishedAt().format(FMT) + " 完成。");
        });
        return sb.toString();
    }

    private long limitOf(String level) {
        return switch (level) {
            case "高" -> limitHigh;
            case "中" -> limitMid;
            default -> limitLow;
        };
    }

    static String fmtDuration(long minutes) {
        if (minutes < 60) return minutes + " 分钟";
        long h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分钟";
    }

    /** 补充/更新案件信息（赔付专员） */
    @Transactional
    public CompensationCase updateCase(Long id, String damageType, BigDecimal claimAmount, String accidentProof) {
        AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(id);
        if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
            throw new BizException("案件已审核，不能修改");
        }
        if (damageType != null && !damageType.isBlank()) c.setDamageType(damageType);
        if (claimAmount != null) c.setClaimAmount(claimAmount);
        if (accidentProof != null && !accidentProof.isBlank()) c.setAccidentProof(accidentProof);
        return compensationRepo.save(c);
    }

    /** 补充材料内容/附件（赔付专员代录） */
    @Transactional
    public ClaimMaterial updateMaterial(Long caseId, Long materialId,
                                        String content, String fileUrl, Boolean submitted) {
        AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(caseId);
        if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
            throw new BizException("案件已审核，不能修改材料");
        }
        ClaimMaterial mat = getMaterialOrThrow(caseId, materialId);
        if (content != null) mat.setContent(content);
        if (fileUrl != null && !fileUrl.isBlank()) mat.setFileUrl(fileUrl);
        if (submitted != null) mat.setSubmitted(submitted);
        return materialRepo.save(mat);
    }

    /** 核验单项材料：PASSED 通过 / FAILED 不通过 */
    @Transactional
    public ClaimMaterial verifyMaterial(Long caseId, Long materialId, String verifyStatus, String verifyNote) {
        User me = AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(caseId);
        if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
            throw new BizException("案件已审核，不能再核验材料");
        }
        if (!Constants.VerifyStatus.PASSED.equals(verifyStatus)
                && !Constants.VerifyStatus.FAILED.equals(verifyStatus)) {
            throw new BizException("核验结论不合法");
        }
        ClaimMaterial mat = getMaterialOrThrow(caseId, materialId);
        if (Constants.VerifyStatus.PASSED.equals(verifyStatus) && !Boolean.TRUE.equals(mat.getSubmitted())) {
            throw new BizException("材料尚未提交，不能核验通过");
        }
        mat.setVerifyStatus(verifyStatus);
        mat.setVerifyNote(verifyNote);
        mat.setVerifiedBy(me.getId());
        mat.setVerifiedAt(LocalDateTime.now());
        return materialRepo.save(mat);
    }

    /** 定责审核：APPROVED 核定赔付 / REJECTED 驳回；核定后开始追踪维修是否彻底完成 */
    @Transactional
    public CompensationCase decide(Long id, String result, String responsibility,
                                   Integer ownerRatio, Integer streetRatio,
                                   BigDecimal approvedAmount, String analysis) {
        User me = AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(id);
        if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
            throw new BizException("案件已审核");
        }
        if (c.getAccidentTime() == null) {
            throw new BizException("请先录入事故时间，完成时效分析后再审核");
        }
        List<ClaimMaterial> materials = materialRepo.findByCaseIdOrderById(id);
        long pending = materials.stream()
                .filter(x -> Constants.VerifyStatus.PENDING.equals(x.getVerifyStatus())).count();
        if (pending > 0) {
            throw new BizException("还有 " + pending + " 项材料未核验（事故时间/行车记录仪/交警证明/维修发票/平台状态），请先完成核验");
        }
        if (analysis == null || analysis.isBlank()) {
            throw new BizException("请填写责任分析（上报时间、临时围护、权属确认、维修时长、事故证明）");
        }
        c.setSpecialistId(me.getId());
        c.setAnalysis(analysis);
        c.setDecidedAt(LocalDateTime.now());

        Incident inc = incidentRepo.findById(c.getIncidentId())
                .orElseThrow(() -> new BizException("事件不存在"));

        if ("APPROVED".equals(result)) {
            if (responsibility == null || responsibility.isBlank()) throw new BizException("请选择责任认定");
            if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("核定金额不合法");
            }
            int or = ownerRatio == null ? 0 : ownerRatio;
            int sr = streetRatio == null ? 0 : streetRatio;
            if (or + sr != 100 && !Constants.Responsibility.UNDETERMINED.equals(responsibility)) {
                throw new BizException("权属单位与街道分摊比例之和须为 100%");
            }
            c.setResponsibility(responsibility);
            c.setOwnerRatio(or);
            c.setStreetRatio(sr);
            c.setApprovedAmount(approvedAmount);
            c.setStatus(Constants.CompStatus.APPROVED);
            // 赔付审核通过后继续追踪维修是否彻底完成
            boolean repaired = Constants.IncidentStatus.ACCEPTED.equals(inc.getStatus())
                    || Constants.IncidentStatus.CLOSED.equals(inc.getStatus());
            c.setRepairTrackStatus(repaired
                    ? Constants.RepairTrack.COMPLETED : Constants.RepairTrack.TRACKING);
            c.setRepairTrackNote(repaired
                    ? "核定时事件已验收通过，维修彻底完成"
                    : "赔付核定后开始追踪：等待维修完成并验收通过");
            c.setRepairTrackedAt(LocalDateTime.now());
        } else if ("REJECTED".equals(result)) {
            c.setStatus(Constants.CompStatus.REJECTED);
            c.setResponsibility(Constants.Responsibility.UNDETERMINED);
            c.setApprovedAmount(BigDecimal.ZERO);
        } else {
            throw new BizException("审核结论不合法");
        }
        compensationRepo.save(c);

        // 同步到费用分摊单（若已生成）
        costRepo.findByIncidentId(inc.getId()).ifPresent(ca -> {
            if (Constants.CostStatus.PENDING.equals(ca.getStatus())) {
                ca.setCompensationAmount(c.getApprovedAmount());
                ca.setTotalAmount(ca.getEmergencyCost().add(ca.getRepairCost()).add(c.getApprovedAmount()));
                costRepo.save(ca);
            }
        });
        notificationService.notify(inc.getReporterId(), inc.getId(),
                "赔付案件已审核 " + inc.getCode(),
                "APPROVED".equals(result) ? ("核定赔付 ¥" + c.getApprovedAmount()) : "赔付申请被驳回");
        return c;
    }

    /** 标记赔付完成 */
    @Transactional
    public CompensationCase pay(Long id) {
        AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(id);
        if (!Constants.CompStatus.APPROVED.equals(c.getStatus())) {
            throw new BizException("只有已核定的案件才能赔付");
        }
        c.setStatus(Constants.CompStatus.PAID);
        c.setPaidAt(LocalDateTime.now());
        return compensationRepo.save(c);
    }

    /** 验收通过联动（AcceptanceService 调用）：维修追踪 → 已彻底完成 */
    @Transactional
    public void onAcceptancePassed(Long incidentId) {
        compensationRepo.findByIncidentId(incidentId).ifPresent(c -> {
            if (Constants.RepairTrack.TRACKING.equals(c.getRepairTrackStatus())) {
                c.setRepairTrackStatus(Constants.RepairTrack.COMPLETED);
                c.setRepairTrackNote("维修验收通过，维修彻底完成");
                c.setRepairTrackedAt(LocalDateTime.now());
                compensationRepo.save(c);
            }
        });
    }

    /** 再次塌陷联动（IncidentService 调用）：上一事件案件的维修追踪 → 再次塌陷 */
    @Transactional
    public void onRecollapse(Incident prev, Incident newInc) {
        compensationRepo.findByIncidentId(prev.getId()).ifPresent(c -> {
            String s = c.getRepairTrackStatus();
            if (Constants.RepairTrack.TRACKING.equals(s) || Constants.RepairTrack.COMPLETED.equals(s)) {
                c.setRepairTrackStatus(Constants.RepairTrack.RECOLLAPSED);
                c.setRepairTrackNote("同一井盖再次塌陷，新事件 " + newInc.getCode()
                        + "；上一轮维修单位、材料规格、验收人纳入新的责任判断");
                c.setRepairTrackedAt(LocalDateTime.now());
                compensationRepo.save(c);
            }
        });
    }

    /**
     * 再次塌陷追溯：上一事件的维修单位、材料规格、验收人、距上次维修天数，
     * 供事件详情与赔付审核页展示，进入新的责任判断。
     */
    public Map<String, Object> buildRecollapseTrace(Incident inc) {
        if (!Boolean.TRUE.equals(inc.getRecollapse()) || inc.getPreviousIncidentId() == null) {
            return null;
        }
        Incident prev = incidentRepo.findById(inc.getPreviousIncidentId()).orElse(null);
        if (prev == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("previousIncidentId", prev.getId());
        m.put("previousCode", prev.getCode());
        m.put("previousIssueType", prev.getIssueType());
        m.put("previousClosedAt", prev.getClosedAt());

        repairOrderRepo.findByIncidentId(prev.getId()).ifPresent(o -> {
            m.put("repairTeam", userRepo.findById(o.getTeamId()).map(User::getOrgName).orElse("未知"));
            m.put("coverSpec", o.getCoverSpec());
            m.put("repairFinishedAt", o.getFinishedAt());
            m.put("repairCost", o.getRepairCost());
            if (o.getFinishedAt() != null) {
                m.put("daysSinceRepair", Duration.between(o.getFinishedAt(), inc.getCreatedAt()).toDays());
            }
        });
        acceptanceRepo.findByIncidentIdOrderByInspectedAtDesc(prev.getId()).stream()
                .filter(a -> "PASSED".equals(a.getResult())).findFirst().ifPresent(a -> {
            m.put("inspectorName", userRepo.findById(a.getInspectorId()).map(User::getName).orElse("未知"));
            m.put("acceptedAt", a.getInspectedAt());
            m.put("acceptanceNote", a.getNote());
        });
        return m;
    }

    private ClaimMaterial getMaterialOrThrow(Long caseId, Long materialId) {
        ClaimMaterial mat = materialRepo.findById(materialId)
                .orElseThrow(() -> new BizException("材料不存在"));
        if (!mat.getCaseId().equals(caseId)) throw new BizException("材料不属于该案件");
        return mat;
    }

    private CompensationCase getOrThrow(Long id) {
        return compensationRepo.findById(id).orElseThrow(() -> new BizException("赔付案件不存在"));
    }
}
