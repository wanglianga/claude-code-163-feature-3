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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 赔付定责：依据上报时间、临时围护时效、权属确认、维修时长、事故证明综合判断责任。
 * 扩展：事故时间时间线核验（上报前→风险线索；上报后未围护→值班时效与权属响应）、
 * 赔付材料核验、赔付后维修追踪（同一井盖再次塌陷时关联上一轮维修单位/材料规格/验收人）。
 */
@Service
@RequiredArgsConstructor
public class CompensationService {
    private final CompensationRepo compensationRepo;
    private final CostAllocationRepo costRepo;
    private final ClaimMaterialRepo materialRepo;
    private final RiskClueRepo riskClueRepo;
    private final RepairFollowUpRepo followUpRepo;
    private final EmergencyMeasureRepo emergencyRepo;
    private final InvestigationRepo investigationRepo;
    private final OwnerClaimRepo claimRepo;
    private final IncidentRepo incidentRepo;
    private final UserRepo userRepo;
    private final IncidentService incidentService;
    private final NotificationService notificationService;

    @Value("${app.emergency-limit-minutes.high}")
    private long limitHigh;
    @Value("${app.emergency-limit-minutes.mid}")
    private long limitMid;
    @Value("${app.emergency-limit-minutes.low}")
    private long limitLow;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<CompensationCase> listAll() {
        AuthContext.requireRole(Constants.Role.COMPENSATOR, Constants.Role.ADMIN);
        return compensationRepo.findAllByOrderByCreatedAtDesc();
    }

    /** 补充/更新案件信息（赔付专员）；事故时间变化时重新做时间线核验 */
    @Transactional
    public CompensationCase updateCase(Long id, String damageType, BigDecimal claimAmount,
                                       String accidentProof, LocalDateTime accidentTime) {
        AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(id);
        if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
            throw new BizException("案件已审核，不能修改");
        }
        if (damageType != null && !damageType.isBlank()) c.setDamageType(damageType);
        if (claimAmount != null) c.setClaimAmount(claimAmount);
        if (accidentProof != null && !accidentProof.isBlank()) c.setAccidentProof(accidentProof);
        if (accidentTime != null) {
            c.setAccidentTime(accidentTime);
            computeTimeline(c);
        }
        return compensationRepo.save(c);
    }

    /**
     * 时间线核验：比对事故时间与平台记录（首次上报、围护完成、权属确认）。
     * - 事故在首次上报前：平台只记录风险线索；
     * - 事故在上报后围护前：责任关联值班处置时效与权属单位响应；
     * - 事故在围护完成后：平台处置时效不构成责任。
     */
    private void computeTimeline(CompensationCase c) {
        Incident inc = incidentService.getOrThrow(c.getIncidentId());
        LocalDateTime accident = c.getAccidentTime();
        LocalDateTime reported = inc.getCreatedAt();
        EmergencyMeasure em = emergencyRepo.findByIncidentId(inc.getId()).orElse(null);
        LocalDateTime securedAt = em == null ? null : em.getSecuredAt();
        LocalDateTime ownerConfirmedAt = ownerConfirmedAtOf(inc.getId());

        List<String> lines = new ArrayList<>();
        lines.add("事故时间：" + fmt(accident) + "；首次上报：" + fmt(reported)
                + "；围护完成：" + (securedAt == null ? "未完成" : fmt(securedAt)));

        if (accident.isBefore(reported)) {
            c.setTimelineFlag(Constants.TimelineFlag.BEFORE_REPORT);
            lines.add("■ 事故发生于首次上报前 " + humanize(Duration.between(accident, reported))
                    + "，平台当时无该井盖在案记录。");
            lines.add("■ 平台仅登记风险线索备查，不构成值班处置时效与权属单位响应责任；"
                    + "建议核查权属单位日常养护与巡检责任。");
            createRiskClue(c, inc);
        } else {
            riskClueRepo.findByCaseId(c.getId()).ifPresent(riskClueRepo::delete);
            if (securedAt == null || accident.isBefore(securedAt)) {
                c.setTimelineFlag(Constants.TimelineFlag.AFTER_REPORT_UNSECURED);
                long gapMin = Duration.between(reported, accident).toMinutes();
                lines.add("■ 事故发生于首次上报后、临时围护完成前（上报后 " + humanize(Duration.ofMinutes(gapMin)) + "）。");
                long limit = limitOf(inc.getDangerLevel());
                if (gapMin >= limit) {
                    lines.add("■ 值班处置时效：" + inc.getDangerLevel() + "危事件到场围护时限 " + limit
                            + " 分钟，事故发生时已超出时限仍未围护，值班处置时效责任成立。");
                } else {
                    lines.add("■ 值班处置时效：" + inc.getDangerLevel() + "危事件到场围护时限 " + limit
                            + " 分钟，事故发生在时限内（上报后 " + gapMin + " 分钟），值班处置时效责任不成立。");
                }
                if (em != null && em.getArrivedAt() != null) {
                    long arriveMin = Duration.between(em.getDispatchedAt(), em.getArrivedAt()).toMinutes();
                    lines.add("■ 实际到场耗时 " + arriveMin + " 分钟（派单 " + fmt(em.getDispatchedAt())
                            + " → 到场 " + fmt(em.getArrivedAt()) + "）"
                            + (arriveMin > limit ? "，超出时限 " + (arriveMin - limit) + " 分钟。" : "，未超时限。"));
                }
                if (ownerConfirmedAt != null) {
                    lines.add("■ 权属单位响应：上报后 " + humanize(Duration.between(reported, ownerConfirmedAt))
                            + " 确认权属（" + fmt(ownerConfirmedAt) + "）"
                            + (accident.isBefore(ownerConfirmedAt) ? "，事故发生时权属尚未确认。" : "，事故发生时权属已确认。"));
                } else {
                    lines.add("■ 权属单位响应：截至核验时权属仍未确认，无权属单位响应记录。");
                }
            } else {
                c.setTimelineFlag(Constants.TimelineFlag.AFTER_SECURED);
                lines.add("■ 事故发生于临时围护完成后 "
                        + humanize(Duration.between(securedAt, accident))
                        + "，平台已完成应急围护，值班处置时效不构成责任。");
                lines.add("■ 建议核查围护措施有效性、维修进度与权属单位响应。");
            }
        }
        c.setTimelineAnalysis(String.join("\n", lines));
    }

    /** 事故在首次上报前 → 平台只记录风险线索（一案一条，重复核验不重复登记） */
    private void createRiskClue(CompensationCase c, Incident inc) {
        if (riskClueRepo.findByCaseId(c.getId()).isPresent()) return;
        RiskClue clue = new RiskClue();
        clue.setIncidentId(inc.getId());
        clue.setCaseId(c.getId());
        clue.setAccidentTime(c.getAccidentTime());
        clue.setFirstReportTime(inc.getCreatedAt());
        clue.setNote("事故发生于首次上报前，平台无在案记录，仅登记风险线索；"
                + "线索已同步权属排查与巡检路线参考（" + inc.getAddress() + "）。");
        clue.setCreatedBy(AuthContext.get() == null ? 0L : AuthContext.get().getId());
        riskClueRepo.save(clue);
    }

    private LocalDateTime ownerConfirmedAtOf(Long incidentId) {
        LocalDateTime t = null;
        for (OwnerClaim cl : claimRepo.findByIncidentIdOrderByCreatedAtDesc(incidentId)) {
            if (t == null || cl.getCreatedAt().isBefore(t)) t = cl.getCreatedAt();
        }
        for (OwnershipInvestigation inv : investigationRepo.findByIncidentIdOrderByCreatedAtDesc(incidentId)) {
            if ("CONFIRMED".equals(inv.getConclusion())
                    && (t == null || inv.getCreatedAt().isBefore(t))) t = inv.getCreatedAt();
        }
        return t;
    }

    /** 定责审核：APPROVED 核定赔付 / REJECTED 驳回；核定后自动生成维修追踪 */
    @Transactional
    public CompensationCase decide(Long id, String result, String responsibility,
                                   Integer ownerRatio, Integer streetRatio,
                                   BigDecimal approvedAmount, String analysis) {
        User me = AuthContext.requireRole(Constants.Role.COMPENSATOR);
        CompensationCase c = getOrThrow(id);
        if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
            throw new BizException("案件已审核");
        }
        if (analysis == null || analysis.isBlank()) {
            throw new BizException("请填写责任分析（上报时间、临时围护、权属确认、维修时长、事故证明）");
        }
        c.setSpecialistId(me.getId());
        c.setAnalysis(analysis);
        c.setDecidedAt(LocalDateTime.now());

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
        } else if ("REJECTED".equals(result)) {
            c.setStatus(Constants.CompStatus.REJECTED);
            c.setResponsibility(Constants.Responsibility.UNDETERMINED);
            c.setApprovedAmount(BigDecimal.ZERO);
        } else {
            throw new BizException("审核结论不合法");
        }
        compensationRepo.save(c);

        // 赔付审核通过后，平台继续追踪维修是否彻底完成
        if (Constants.CompStatus.APPROVED.equals(c.getStatus())) {
            followUpRepo.findByCaseId(c.getId()).orElseGet(() -> {
                RepairFollowUp f = new RepairFollowUp();
                f.setCaseId(c.getId());
                f.setIncidentId(c.getIncidentId());
                f.setCheckNote("赔付已核定，平台继续追踪该井盖维修是否彻底完成。");
                return followUpRepo.save(f);
            });
        }

        // 同步到费用分摊单（若已生成）
        Incident inc = incidentService.getOrThrow(c.getIncidentId());
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

    // ------------------------------------------------------------------
    // 赔付材料核验
    // ------------------------------------------------------------------

    /** 提交赔付材料：赔付专员/管理员随时可录；索赔人（事件上报市民）在案件待审核时可补充 */
    @Transactional
    public ClaimMaterial addMaterial(Long caseId, String type, String title, String fileUrl, String content) {
        CompensationCase c = getOrThrow(caseId);
        User me = AuthContext.requireLogin();
        boolean staff = Constants.Role.COMPENSATOR.equals(me.getRole()) || Constants.Role.ADMIN.equals(me.getRole());
        if (!staff) {
            Incident inc = incidentService.getOrThrow(c.getIncidentId());
            if (!Constants.Role.CITIZEN.equals(me.getRole()) || !inc.getReporterId().equals(me.getId())) {
                throw new BizException("只有赔付专员或索赔人本人可以提交材料");
            }
            if (!Constants.CompStatus.FILED.equals(c.getStatus())) {
                throw new BizException("案件已审核，不能再补充材料");
            }
        }
        if (!List.of(Constants.MaterialType.DASHCAM, Constants.MaterialType.POLICE_CERT,
                Constants.MaterialType.REPAIR_INVOICE, Constants.MaterialType.OTHER).contains(type)) {
            throw new BizException("材料类型不合法");
        }
        if (title == null || title.isBlank()) throw new BizException("请填写材料名称");

        ClaimMaterial m = new ClaimMaterial();
        m.setCaseId(caseId);
        m.setType(type);
        m.setTitle(title.trim());
        m.setFileUrl(fileUrl);
        m.setContent(content);
        m.setUploadedBy(me.getId());
        return materialRepo.save(m);
    }

    /** 核验材料：通过 / 存疑（赔付专员） */
    @Transactional
    public ClaimMaterial verifyMaterial(Long materialId, String status, String verifyNote) {
        User me = AuthContext.requireRole(Constants.Role.COMPENSATOR);
        ClaimMaterial m = materialRepo.findById(materialId)
                .orElseThrow(() -> new BizException("材料不存在"));
        if (!Constants.MaterialStatus.VERIFIED.equals(status) && !Constants.MaterialStatus.DOUBTFUL.equals(status)) {
            throw new BizException("核验结论不合法");
        }
        if (Constants.MaterialStatus.DOUBTFUL.equals(status) && (verifyNote == null || verifyNote.isBlank())) {
            throw new BizException("存疑材料请填写存疑原因");
        }
        m.setStatus(status);
        m.setVerifyNote(verifyNote);
        m.setVerifiedBy(me.getId());
        m.setVerifiedAt(LocalDateTime.now());
        return materialRepo.save(m);
    }

    /** 审核工作台聚合：案件 + 平台时间线记录 + 材料 + 风险线索 + 维修追踪 + 复发关联 */
    public Map<String, Object> review(Long caseId) {
        CompensationCase c = getOrThrow(caseId);
        User me = AuthContext.requireLogin();
        Incident inc = incidentService.getOrThrow(c.getIncidentId());
        boolean staff = Constants.Role.COMPENSATOR.equals(me.getRole()) || Constants.Role.ADMIN.equals(me.getRole());
        if (!staff && !inc.getReporterId().equals(me.getId())) {
            throw new BizException("无权查看该案件");
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("case", c);
        m.put("incident", inc);
        m.put("reporterName", userRepo.findById(inc.getReporterId()).map(User::getName).orElse("未知"));
        emergencyRepo.findByIncidentId(inc.getId()).ifPresent(em -> m.put("emergency", em));
        m.put("ownerConfirmedAt", ownerConfirmedAtOf(inc.getId()));
        m.put("emergencyLimitMinutes", limitOf(inc.getDangerLevel()));

        List<Map<String, Object>> materials = new ArrayList<>();
        for (ClaimMaterial mat : materialRepo.findByCaseIdOrderByCreatedAtAsc(caseId)) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("material", mat);
            mm.put("uploaderName", userRepo.findById(mat.getUploadedBy()).map(User::getName).orElse("未知"));
            if (mat.getVerifiedBy() != null) {
                mm.put("verifierName", userRepo.findById(mat.getVerifiedBy()).map(User::getName).orElse("未知"));
            }
            materials.add(mm);
        }
        m.put("materials", materials);
        riskClueRepo.findByCaseId(caseId).ifPresent(clue -> m.put("riskClue", clue));
        followUpRepo.findByCaseId(caseId).ifPresent(f -> m.put("followUp", f));
        // 同一井盖历史维修（上一轮维修单位、材料规格、验收人），供新的责任判断参考
        m.put("recurrences", incidentService.recurrencesOf(inc));
        return m;
    }

    // ------------------------------------------------------------------
    // 赔付后维修追踪
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listFollowUps() {
        AuthContext.requireRole(Constants.Role.COMPENSATOR, Constants.Role.ADMIN);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RepairFollowUp f : followUpRepo.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("followUp", f);
            incidentRepo.findById(f.getIncidentId()).ifPresent(inc -> {
                m.put("incidentCode", inc.getCode());
                m.put("address", inc.getAddress());
                m.put("incidentStatus", inc.getStatus());
            });
            compensationRepo.findById(f.getCaseId()).ifPresent(c -> {
                m.put("claimantName", c.getClaimantName());
                m.put("approvedAmount", c.getApprovedAmount());
            });
            if (f.getLinkedIncidentId() != null) {
                incidentRepo.findById(f.getLinkedIncidentId())
                        .ifPresent(linked -> m.put("linkedIncidentCode", linked.getCode()));
            }
            list.add(m);
        }
        return list;
    }

    /** 确认维修彻底完成：要求事件已验收通过（ACCEPTED/CLOSED） */
    @Transactional
    public RepairFollowUp completeFollowUp(Long id, String checkNote) {
        User me = AuthContext.requireRole(Constants.Role.COMPENSATOR);
        RepairFollowUp f = followUpRepo.findById(id).orElseThrow(() -> new BizException("追踪记录不存在"));
        if (!Constants.FollowUpStatus.TRACKING.equals(f.getStatus())) {
            throw new BizException("该追踪已办结");
        }
        Incident inc = incidentService.getOrThrow(f.getIncidentId());
        if (!Constants.IncidentStatus.ACCEPTED.equals(inc.getStatus())
                && !Constants.IncidentStatus.CLOSED.equals(inc.getStatus())) {
            throw new BizException("该事件维修尚未验收通过，不能确认彻底完成");
        }
        f.setStatus(Constants.FollowUpStatus.COMPLETED);
        f.setCheckNote((f.getCheckNote() == null ? "" : f.getCheckNote() + "\n")
                + "确认彻底完成：" + (checkNote == null || checkNote.isBlank() ? "验收通过，维修彻底完成。" : checkNote));
        f.setConfirmedBy(me.getId());
        f.setCompletedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        return followUpRepo.save(f);
    }

    // ------------------------------------------------------------------
    // 风险线索
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listRiskClues() {
        AuthContext.requireRole(Constants.Role.COMPENSATOR, Constants.Role.ADMIN);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RiskClue clue : riskClueRepo.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clue", clue);
            incidentRepo.findById(clue.getIncidentId()).ifPresent(inc -> {
                m.put("incidentCode", inc.getCode());
                m.put("address", inc.getAddress());
            });
            compensationRepo.findById(clue.getCaseId())
                    .ifPresent(c -> m.put("claimantName", c.getClaimantName()));
            list.add(m);
        }
        return list;
    }

    private CompensationCase getOrThrow(Long id) {
        return compensationRepo.findById(id).orElseThrow(() -> new BizException("赔付案件不存在"));
    }

    private long limitOf(String level) {
        return switch (level) {
            case "高" -> limitHigh;
            case "中" -> limitMid;
            default -> limitLow;
        };
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(FMT);
    }

    /** 时长人性化：X 天 Y 小时 / Y 小时 Z 分钟 / Z 分钟 */
    private String humanize(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 0) minutes = 0;
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        if (days > 0) return days + " 天 " + hours + " 小时";
        if (hours > 0) return hours + " 小时 " + mins + " 分钟";
        return mins + " 分钟";
    }
}
