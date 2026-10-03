package com.city.manhole.service;

import com.city.manhole.common.AuthContext;
import com.city.manhole.common.Constants;
import com.city.manhole.model.*;
import com.city.manhole.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计分析：高发路段、权属争议单位、维修超时、赔付金额、应急到场时效。
 * 结果用于调整日常巡检路线与施工移交规则。
 */
@Service
@RequiredArgsConstructor
public class StatsService {
    private final IncidentRepo incidentRepo;
    private final EmergencyMeasureRepo emergencyRepo;
    private final RepairOrderRepo repairOrderRepo;
    private final CompensationRepo compensationRepo;
    private final CostAllocationRepo costRepo;
    private final UserRepo userRepo;

    @Value("${app.repair-timeout-hours}")
    private long repairTimeoutHours;

    @Value("${app.emergency-limit-minutes.high}")
    private long limitHigh;
    @Value("${app.emergency-limit-minutes.mid}")
    private long limitMid;
    @Value("${app.emergency-limit-minutes.low}")
    private long limitLow;

    private void checkRole() {
        AuthContext.requireRole(Constants.Role.ADMIN, Constants.Role.STREET_DUTY,
                Constants.Role.INSPECTOR, Constants.Role.COMPENSATOR);
    }

    public Map<String, Object> overview() {
        checkRole();
        List<Incident> all = incidentRepo.findAll();
        Map<String, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(Incident::getStatus, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byDanger = all.stream()
                .collect(Collectors.groupingBy(Incident::getDangerLevel, Collectors.counting()));

        long pendingEmergency = all.stream()
                .filter(i -> !Constants.EmergencyStatus.SECURED.equals(i.getEmergencyStatus())).count();
        long pendingOwner = all.stream()
                .filter(i -> Constants.OwnerStatus.PENDING.equals(i.getOwnerStatus())).count();
        long recollapse = all.stream()
                .filter(i -> Boolean.TRUE.equals(i.getRecollapse())).count();

        List<RepairOrder> orders = repairOrderRepo.findAll();
        long timeoutOrders = orders.stream().filter(this::isTimeout).count();

        BigDecimal totalComp = compensationRepo.findAll().stream()
                .filter(c -> Constants.CompStatus.APPROVED.equals(c.getStatus())
                        || Constants.CompStatus.PAID.equals(c.getStatus()))
                .map(CompensationCase::getApprovedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEmergencyCost = emergencyRepo.findAll().stream()
                .map(EmergencyMeasure::getCost).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalIncidents", all.size());
        m.put("byStatus", byStatus);
        m.put("byDanger", byDanger);
        m.put("pendingEmergency", pendingEmergency);
        m.put("pendingOwner", pendingOwner);
        m.put("recollapseCount", recollapse);
        m.put("timeoutOrders", timeoutOrders);
        m.put("totalOrders", orders.size());
        m.put("totalCompensation", totalComp);
        m.put("totalEmergencyCost", totalEmergencyCost);
        return m;
    }

    /** 高发路段 TOP（按路段名归集，如“人民路与建设大街交口”→“人民路”） */
    public List<Map<String, Object>> hotspots() {
        checkRole();
        Map<String, List<Incident>> bySection = incidentRepo.findAll().stream()
                .collect(Collectors.groupingBy(i -> sectionOf(i.getAddress())));
        return bySection.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("address", e.getKey());
            m.put("count", (long) e.getValue().size());
            m.put("highDanger", e.getValue().stream().filter(i -> "高".equals(i.getDangerLevel())).count());
            m.put("damage", e.getValue().stream().filter(Incident::getHasDamage).count());
            return m;
        }).sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
          .limit(10).collect(Collectors.toList());
    }

    /** 从地址提取路段名：截取到第一个 路/街/道/巷 */
    private String sectionOf(String address) {
        if (address == null || address.isBlank()) return "未知路段";
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (c == '路' || c == '街' || c == '道' || c == '巷') {
                return address.substring(0, i + 1);
            }
        }
        return address;
    }

    /** 权属争议单位：经历过责任待定的事件按最终权属单位归集 */
    public Map<String, Object> disputes() {
        checkRole();
        List<Incident> all = incidentRepo.findAll();
        List<User> owners = userRepo.findByRole(Constants.Role.OWNER_UNIT);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User o : owners) {
            List<Incident> mine = all.stream().filter(i -> o.getId().equals(i.getOwnerId())).toList();
            long disputed = mine.stream().filter(Incident::getEverPending).count();
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("ownerId", o.getId());
            m.put("orgName", o.getOrgName());
            m.put("total", (long) mine.size());
            m.put("disputed", disputed);
            rows.add(m);
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("disputed"), (Long) a.get("disputed")));
        long stillPending = all.stream()
                .filter(i -> Constants.OwnerStatus.PENDING.equals(i.getOwnerStatus())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byOwner", rows);
        result.put("stillPending", stillPending);
        result.put("totalEverPending", all.stream().filter(Incident::getEverPending).count());
        return result;
    }

    /** 维修超时工单（阈值默认 48 小时） */
    public Map<String, Object> repairTimeouts() {
        checkRole();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RepairOrder o : repairOrderRepo.findAll()) {
            if (!isTimeout(o)) continue;
            Incident inc = incidentRepo.findById(o.getIncidentId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("orderId", o.getId());
            m.put("incidentCode", inc == null ? "-" : inc.getCode());
            m.put("address", inc == null ? "-" : inc.getAddress());
            m.put("team", userRepo.findById(o.getTeamId()).map(User::getOrgName).orElse("-"));
            m.put("owner", userRepo.findById(o.getOwnerId()).map(User::getOrgName).orElse("-"));
            m.put("assignedAt", o.getAssignedAt());
            m.put("finishedAt", o.getFinishedAt());
            m.put("done", o.getFinishedAt() != null);
            m.put("hours", hoursOf(o));
            rows.add(m);
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("hours"), (Long) a.get("hours")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeoutHours", repairTimeoutHours);
        result.put("list", rows);
        return result;
    }

    /** 赔付金额：按月与按责任认定 */
    public Map<String, Object> compensation() {
        checkRole();
        List<CompensationCase> cases = compensationRepo.findAll();
        Map<String, BigDecimal> byMonth = new TreeMap<>();
        Map<String, Long> countByMonth = new TreeMap<>();
        Map<String, BigDecimal> byResp = new LinkedHashMap<>();
        Map<String, Long> countByResp = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (CompensationCase c : cases) {
            if (c.getDecidedAt() == null || !Constants.CompStatus.APPROVED.equals(c.getStatus())
                    && !Constants.CompStatus.PAID.equals(c.getStatus())) continue;
            String month = c.getDecidedAt().format(fmt);
            byMonth.merge(month, c.getApprovedAmount(), BigDecimal::add);
            countByMonth.merge(month, 1L, Long::sum);
            String resp = respName(c.getResponsibility());
            byResp.merge(resp, c.getApprovedAmount(), BigDecimal::add);
            countByResp.merge(resp, 1L, Long::sum);
        }
        List<Map<String, Object>> monthRows = byMonth.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("month", e.getKey());
            m.put("amount", e.getValue());
            m.put("count", countByMonth.get(e.getKey()));
            return m;
        }).collect(Collectors.toList());
        List<Map<String, Object>> respRows = byResp.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("responsibility", e.getKey());
            m.put("amount", e.getValue());
            m.put("count", countByResp.get(e.getKey()));
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byMonth", monthRows);
        result.put("byResponsibility", respRows);
        return result;
    }

    /** 应急处置时效：按危险等级统计平均到场时长与超时数 */
    public List<Map<String, Object>> emergency() {
        checkRole();
        List<EmergencyMeasure> all = emergencyRepo.findAll();
        Map<String, List<EmergencyMeasure>> byLevel = new LinkedHashMap<>();
        for (EmergencyMeasure em : all) {
            Incident inc = incidentRepo.findById(em.getIncidentId()).orElse(null);
            if (inc == null) continue;
            byLevel.computeIfAbsent(inc.getDangerLevel(), k -> new ArrayList<>()).add(em);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String level : List.of("高", "中", "低")) {
            List<EmergencyMeasure> list = byLevel.getOrDefault(level, List.of());
            long limit = limitOf(level);
            long arrived = 0, over = 0, totalMinutes = 0;
            for (EmergencyMeasure em : list) {
                if (em.getArrivedAt() != null && em.getDispatchedAt() != null) {
                    arrived++;
                    long minutes = Duration.between(em.getDispatchedAt(), em.getArrivedAt()).toMinutes();
                    totalMinutes += minutes;
                    if (minutes > limit) over++;
                }
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("dangerLevel", level);
            m.put("total", list.size());
            m.put("arrived", arrived);
            m.put("avgMinutes", arrived == 0 ? 0 : totalMinutes / arrived);
            m.put("limitMinutes", limit);
            m.put("overLimit", over);
            rows.add(m);
        }
        return rows;
    }

    private boolean isTimeout(RepairOrder o) {
        return hoursOf(o) > repairTimeoutHours;
    }

    private long hoursOf(RepairOrder o) {
        java.time.LocalDateTime end = o.getFinishedAt() == null ? java.time.LocalDateTime.now() : o.getFinishedAt();
        return Duration.between(o.getAssignedAt(), end).toHours();
    }

    private long limitOf(String level) {
        return switch (level) {
            case "高" -> limitHigh;
            case "中" -> limitMid;
            default -> limitLow;
        };
    }

    private String respName(String resp) {
        if (resp == null) return "未认定";
        return switch (resp) {
            case Constants.Responsibility.OWNER -> "权属单位";
            case Constants.Responsibility.STREET -> "街道";
            case Constants.Responsibility.SHARED -> "多方分摊";
            default -> "无法认定";
        };
    }
}
