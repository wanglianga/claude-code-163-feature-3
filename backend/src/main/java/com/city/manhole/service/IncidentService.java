package com.city.manhole.service;

import com.city.manhole.common.AuthContext;
import com.city.manhole.common.BizException;
import com.city.manhole.common.Constants;
import com.city.manhole.model.*;
import com.city.manhole.repo.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentService {
    private final IncidentRepo incidentRepo;
    private final UserRepo userRepo;
    private final EmergencyMeasureRepo emergencyRepo;
    private final InvestigationRepo investigationRepo;
    private final OwnerClaimRepo claimRepo;
    private final RepairOrderRepo repairOrderRepo;
    private final AcceptanceRepo acceptanceRepo;
    private final CompensationRepo compensationRepo;
    private final CostAllocationRepo costRepo;
    private final RepairFollowUpRepo followUpRepo;
    private final NotificationService notificationService;
    private final ObjectMapper mapper = new ObjectMapper();

    public static final List<String> ISSUE_TYPES = List.of("缺失", "松动", "异响", "塌陷");
    public static final List<String> ROAD_TYPES = List.of("主干道", "次干道", "支路", "人行道", "街巷");
    public static final List<String> DANGER_LEVELS = List.of("高", "中", "低");

    /** 市民上报 */
    @Transactional
    public Incident create(String issueType, Double longitude, Double latitude, String address,
                           String roadType, List<String> photos, Boolean hasDamage,
                           String damageDesc, String dangerLevel) {
        User me = AuthContext.requireRole(Constants.Role.CITIZEN);
        if (!ISSUE_TYPES.contains(issueType)) throw new BizException("问题类型不合法");
        if (!ROAD_TYPES.contains(roadType)) throw new BizException("道路类型不合法");
        if (!DANGER_LEVELS.contains(dangerLevel)) throw new BizException("危险程度不合法");
        if (longitude == null || latitude == null) throw new BizException("请填写经纬度定位");
        if (address == null || address.isBlank()) throw new BizException("请填写详细地址");

        Incident inc = new Incident();
        inc.setCode(genCode());
        inc.setReporterId(me.getId());
        inc.setIssueType(issueType);
        inc.setLongitude(longitude);
        inc.setLatitude(latitude);
        inc.setAddress(address.trim());
        inc.setRoadType(roadType);
        inc.setPhotos(writeJson(photos == null ? List.of() : photos));
        inc.setHasDamage(Boolean.TRUE.equals(hasDamage));
        inc.setDamageDesc(damageDesc);
        inc.setDangerLevel(dangerLevel);
        inc.setStatus(Constants.IncidentStatus.REPORTED);
        inc.setEmergencyStatus(Constants.EmergencyStatus.PENDING);
        inc.setOwnerStatus(Constants.OwnerStatus.UNCONFIRMED);
        incidentRepo.save(inc);

        // 已造成人车损伤的，自动立赔付案件
        if (inc.getHasDamage()) {
            CompensationCase c = new CompensationCase();
            c.setIncidentId(inc.getId());
            c.setClaimantName(me.getName());
            c.setClaimantPhone(me.getPhone());
            c.setDamageType("待补充");
            c.setAccidentProof(damageDesc);
            c.setStatus(Constants.CompStatus.FILED);
            compensationRepo.save(c);
            notificationService.notifyRole(Constants.Role.COMPENSATOR, inc.getId(),
                    "新赔付案件 " + inc.getCode(), "事件造成人车损伤，请关注赔付定责。");
        }
        // 推送街道值班员做临时围护
        notificationService.notifyRole(Constants.Role.STREET_DUTY, inc.getId(),
                "新上报井盖事件 " + inc.getCode(),
                address + " 发生井盖" + issueType + "，危险程度：" + dangerLevel + "，请立即到场临时围护。");
        // 同一井盖再次塌陷：历史赔付维修追踪标记复发，上一轮维修单位/材料规格/验收人进入新的责任判断
        markRecollapse(inc);
        return inc;
    }

    /** 同一井盖出现新事件时，将历史维修追踪标记为“再次塌陷”并提示赔付专员 */
    private void markRecollapse(Incident inc) {
        List<Incident> prev = sameManholeHistory(inc);
        if (prev.isEmpty()) return;
        List<Long> prevIds = prev.stream().map(Incident::getId).toList();
        List<RepairFollowUp> fus = followUpRepo.findByIncidentIdInAndStatusIn(prevIds,
                List.of(Constants.FollowUpStatus.TRACKING, Constants.FollowUpStatus.COMPLETED));
        for (RepairFollowUp f : fus) {
            f.setStatus(Constants.FollowUpStatus.RECOLLAPSED);
            f.setLinkedIncidentId(inc.getId());
            f.setCheckNote((f.getCheckNote() == null ? "" : f.getCheckNote() + "\n")
                    + "同一井盖再次塌陷，关联新事件 " + inc.getCode()
                    + "；上一轮维修单位、材料规格与验收人进入新的责任判断。");
            f.setUpdatedAt(LocalDateTime.now());
            followUpRepo.save(f);
        }
        boolean hasRepair = prev.stream().anyMatch(p -> repairOrderRepo.findByIncidentId(p.getId()).isPresent());
        if (hasRepair || !fus.isEmpty()) {
            notificationService.notifyRole(Constants.Role.COMPENSATOR, inc.getId(),
                    "同一井盖再次塌陷 " + inc.getCode(),
                    inc.getAddress() + " 存在历史维修/赔付记录，上一轮维修单位、材料规格与验收人已关联至本事件详情。");
        }
    }

    /** 同一井盖判定：地址一致，或坐标相距约 20 米以内 */
    private boolean sameManhole(Incident a, Incident b) {
        if (a.getAddress() != null && b.getAddress() != null
                && a.getAddress().trim().equals(b.getAddress().trim())) {
            return true;
        }
        return Math.abs(a.getLatitude() - b.getLatitude()) <= 0.0002
                && Math.abs(a.getLongitude() - b.getLongitude()) <= 0.0002;
    }

    /** 同一井盖的历史事件（早于当前事件，最多 5 条） */
    public List<Incident> sameManholeHistory(Incident inc) {
        return incidentRepo.findAll().stream()
                .filter(o -> !o.getId().equals(inc.getId()))
                .filter(o -> o.getCreatedAt().isBefore(inc.getCreatedAt()))
                .filter(o -> sameManhole(o, inc))
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }

    /** 同一井盖历史维修信息：上一轮维修单位、材料规格（井盖规格）、验收人、上次赔付责任 */
    public List<Map<String, Object>> recurrencesOf(Incident inc) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Incident prev : sameManholeHistory(inc)) {
            RepairOrder o = repairOrderRepo.findByIncidentId(prev.getId()).orElse(null);
            if (o == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("incidentId", prev.getId());
            m.put("code", prev.getCode());
            m.put("issueType", prev.getIssueType());
            m.put("status", prev.getStatus());
            m.put("repairTeam", userRepo.findById(o.getTeamId()).map(User::getOrgName).orElse("-"));
            m.put("coverSpec", o.getCoverSpec());
            m.put("finishedAt", o.getFinishedAt());
            Acceptance acc = acceptanceRepo.findByIncidentIdOrderByInspectedAtDesc(prev.getId()).stream()
                    .filter(a -> "PASSED".equals(a.getResult())).findFirst().orElse(null);
            if (acc != null) {
                m.put("inspectorName", userRepo.findById(acc.getInspectorId()).map(User::getName).orElse("-"));
                m.put("inspectedAt", acc.getInspectedAt());
            }
            compensationRepo.findByIncidentId(prev.getId()).ifPresent(c -> {
                m.put("compensationResponsibility", c.getResponsibility());
                m.put("compensationStatus", c.getStatus());
            });
            list.add(m);
        }
        return list;
    }

    private String genCode() {
        String prefix = "MH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = incidentRepo.countByCodeStartingWith(prefix) + 1;
        return prefix + "-" + String.format("%04d", seq);
    }

    /** 按角色可见范围列出事件 */
    public List<Map<String, Object>> listForMe() {
        User me = AuthContext.requireLogin();
        List<Incident> list;
        switch (me.getRole()) {
            case Constants.Role.CITIZEN -> list = incidentRepo.findByReporterIdOrderByCreatedAtDesc(me.getId());
            case Constants.Role.OWNER_UNIT -> {
                // 可认领的（未确认/排查中/责任待定）+ 已归我的
                List<Incident> claimable = incidentRepo.findByOwnerStatusInOrderByCreatedAtDesc(
                        List.of(Constants.OwnerStatus.UNCONFIRMED, Constants.OwnerStatus.INVESTIGATING,
                                Constants.OwnerStatus.PENDING));
                List<Incident> mine = incidentRepo.findByOwnerIdOrderByCreatedAtDesc(me.getId());
                Set<Long> ids = new HashSet<>();
                list = new ArrayList<>();
                for (Incident i : mine) { if (ids.add(i.getId())) list.add(i); }
                for (Incident i : claimable) { if (ids.add(i.getId())) list.add(i); }
                list.sort(Comparator.comparing(Incident::getCreatedAt).reversed());
            }
            case Constants.Role.REPAIR_TEAM -> {
                List<RepairOrder> orders = repairOrderRepo.findByTeamIdOrderByAssignedAtDesc(me.getId());
                list = orders.stream()
                        .map(o -> incidentRepo.findById(o.getIncidentId()).orElse(null))
                        .filter(Objects::nonNull).collect(Collectors.toList());
            }
            default -> list = incidentRepo.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::toListItem).collect(Collectors.toList());
    }

    private Map<String, Object> toListItem(Incident inc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inc.getId());
        m.put("code", inc.getCode());
        m.put("issueType", inc.getIssueType());
        m.put("address", inc.getAddress());
        m.put("roadType", inc.getRoadType());
        m.put("dangerLevel", inc.getDangerLevel());
        m.put("hasDamage", inc.getHasDamage());
        m.put("status", inc.getStatus());
        m.put("emergencyStatus", inc.getEmergencyStatus());
        m.put("ownerStatus", inc.getOwnerStatus());
        m.put("responsibilityPending", inc.getResponsibilityPending());
        m.put("createdAt", inc.getCreatedAt());
        m.put("ownerOrg", inc.getOwnerId() == null ? null :
                userRepo.findById(inc.getOwnerId()).map(User::getOrgName).orElse(null));
        return m;
    }

    /** 事件详情（聚合两条线的全部记录） */
    public Map<String, Object> detail(Long id) {
        User me = AuthContext.requireLogin();
        Incident inc = incidentRepo.findById(id).orElseThrow(() -> new BizException("事件不存在"));
        checkViewPermission(me, inc);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("incident", inc);
        m.put("photoList", readJson(inc.getPhotos()));
        m.put("reporterName", userRepo.findById(inc.getReporterId()).map(User::getName).orElse("未知"));
        if (inc.getOwnerId() != null) {
            userRepo.findById(inc.getOwnerId()).ifPresent(o -> {
                m.put("ownerName", o.getName());
                m.put("ownerOrg", o.getOrgName());
            });
        }

        emergencyRepo.findByIncidentId(id).ifPresent(em -> {
            m.put("emergency", em);
            if (em.getOfficerId() != null) {
                m.put("emergencyOfficer", userRepo.findById(em.getOfficerId()).map(User::getName).orElse(null));
            }
        });

        List<Map<String, Object>> invs = new ArrayList<>();
        for (OwnershipInvestigation inv : investigationRepo.findByIncidentIdOrderByCreatedAtDesc(id)) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("investigation", inv);
            im.put("investigatorName", userRepo.findById(inv.getInvestigatorId()).map(User::getName).orElse("未知"));
            if (inv.getConfirmedOwnerId() != null) {
                im.put("confirmedOwnerOrg", userRepo.findById(inv.getConfirmedOwnerId()).map(User::getOrgName).orElse("未知"));
            }
            invs.add(im);
        }
        m.put("investigations", invs);

        List<Map<String, Object>> claims = new ArrayList<>();
        for (OwnerClaim c : claimRepo.findByIncidentIdOrderByCreatedAtDesc(id)) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("claim", c);
            cm.put("ownerOrg", userRepo.findById(c.getOwnerId()).map(User::getOrgName).orElse("未知"));
            claims.add(cm);
        }
        m.put("claims", claims);

        repairOrderRepo.findByIncidentId(id).ifPresent(o -> {
            m.put("repairOrder", o);
            m.put("repairTeamName", userRepo.findById(o.getTeamId()).map(User::getOrgName).orElse("未知"));
        });

        List<Map<String, Object>> accs = new ArrayList<>();
        for (Acceptance a : acceptanceRepo.findByIncidentIdOrderByInspectedAtDesc(id)) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("acceptance", a);
            am.put("inspectorName", userRepo.findById(a.getInspectorId()).map(User::getName).orElse("未知"));
            accs.add(am);
        }
        m.put("acceptances", accs);

        compensationRepo.findByIncidentId(id).ifPresent(c -> m.put("compensation", c));
        costRepo.findByIncidentId(id).ifPresent(c -> m.put("costAllocation", c));
        // 同一井盖历史维修（上一轮维修单位、材料规格、验收人）
        m.put("recurrences", recurrencesOf(inc));
        return m;
    }

    private void checkViewPermission(User me, Incident inc) {
        switch (me.getRole()) {
            case Constants.Role.CITIZEN -> {
                if (!inc.getReporterId().equals(me.getId())) throw new BizException("无权查看该事件");
            }
            case Constants.Role.OWNER_UNIT -> {
                boolean claimable = !Constants.OwnerStatus.CONFIRMED.equals(inc.getOwnerStatus());
                if (!claimable && !me.getId().equals(inc.getOwnerId())) throw new BizException("无权查看该事件");
            }
            case Constants.Role.REPAIR_TEAM -> {
                RepairOrder o = repairOrderRepo.findByIncidentId(inc.getId()).orElse(null);
                if (o == null || !o.getTeamId().equals(me.getId())) throw new BizException("无权查看该事件");
            }
            default -> { /* 街道/巡查/赔付/管理员可查看全部 */ }
        }
    }

    public Incident getOrThrow(Long id) {
        return incidentRepo.findById(id).orElseThrow(() -> new BizException("事件不存在"));
    }

    @Transactional
    public Incident save(Incident inc) {
        inc.setUpdatedAt(LocalDateTime.now());
        return incidentRepo.save(inc);
    }

    public String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败");
        }
    }

    public List<String> readJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
