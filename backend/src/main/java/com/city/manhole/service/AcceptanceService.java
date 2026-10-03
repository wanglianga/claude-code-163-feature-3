package com.city.manhole.service;

import com.city.manhole.common.AuthContext;
import com.city.manhole.common.BizException;
import com.city.manhole.common.Constants;
import com.city.manhole.model.*;
import com.city.manhole.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 城管巡查员验收：照片、定位、路面平整度、车辆碾压测试。 */
@Service
@RequiredArgsConstructor
public class AcceptanceService {
    private final AcceptanceRepo acceptanceRepo;
    private final RepairOrderRepo repairOrderRepo;
    private final EmergencyMeasureRepo emergencyRepo;
    private final CompensationRepo compensationRepo;
    private final CostAllocationRepo costRepo;
    private final UserRepo userRepo;
    private final IncidentService incidentService;
    private final NotificationService notificationService;
    private final CompensationService compensationService;

    @Transactional
    public Acceptance submit(Long incidentId, Boolean photoOk, Boolean locationOk,
                             Boolean flatnessOk, Boolean rollingTestOk, String result, String note) {
        User me = AuthContext.requireRole(Constants.Role.INSPECTOR);
        Incident inc = incidentService.getOrThrow(incidentId);
        if (!Constants.IncidentStatus.REPAIRED.equals(inc.getStatus())) {
            throw new BizException("该事件不在待验收状态");
        }
        if (!"PASSED".equals(result) && !"FAILED".equals(result)) {
            throw new BizException("验收结论不合法");
        }
        Acceptance a = new Acceptance();
        a.setIncidentId(incidentId);
        a.setInspectorId(me.getId());
        a.setPhotoOk(Boolean.TRUE.equals(photoOk));
        a.setLocationOk(Boolean.TRUE.equals(locationOk));
        a.setFlatnessOk(Boolean.TRUE.equals(flatnessOk));
        a.setRollingTestOk(Boolean.TRUE.equals(rollingTestOk));
        a.setResult(result);
        a.setNote(note);
        acceptanceRepo.save(a);

        RepairOrder order = repairOrderRepo.findByIncidentId(incidentId)
                .orElseThrow(() -> new BizException("维修工单不存在"));

        if ("PASSED".equals(result)) {
            order.setStatus(Constants.RepairStatus.FINISHED); // 保持完成态
            inc.setStatus(Constants.IncidentStatus.ACCEPTED);
            incidentService.save(inc);
            createOrRefreshCostAllocation(inc, order);
            compensationService.onAcceptancePassed(incidentId); // 赔付维修追踪 → 彻底完成
            notificationService.notifyRole(Constants.Role.ADMIN, inc.getId(),
                    "验收通过待费用复核 " + inc.getCode(), "请复核费用分摊并结案。");
            notificationService.notify(inc.getReporterId(), inc.getId(),
                    "维修验收通过 " + inc.getCode(), "您上报的井盖问题已修复并通过验收。");
        } else {
            // 验收不通过：退回维修队重修
            order.setStatus(Constants.RepairStatus.RETURNED);
            repairOrderRepo.save(order);
            inc.setStatus(Constants.IncidentStatus.REPAIRING);
            incidentService.save(inc);
            notificationService.notify(order.getTeamId(), inc.getId(),
                    "验收不通过退回重修 " + inc.getCode(), "原因：" + (note == null ? "未填写" : note));
        }
        repairOrderRepo.save(order);
        return a;
    }

    /** 验收通过后汇总应急费、维修费、赔付金，生成待复核的费用分摊单 */
    private void createOrRefreshCostAllocation(Incident inc, RepairOrder order) {
        BigDecimal emergencyCost = emergencyRepo.findByIncidentId(inc.getId())
                .map(EmergencyMeasure::getCost).orElse(BigDecimal.ZERO);
        BigDecimal repairCost = order.getRepairCost() == null ? BigDecimal.ZERO : order.getRepairCost();
        BigDecimal compAmount = compensationRepo.findByIncidentId(inc.getId())
                .filter(c -> Constants.CompStatus.APPROVED.equals(c.getStatus())
                        || Constants.CompStatus.PAID.equals(c.getStatus()))
                .map(CompensationCase::getApprovedAmount).orElse(BigDecimal.ZERO);

        CostAllocation ca = costRepo.findByIncidentId(inc.getId()).orElseGet(CostAllocation::new);
        ca.setIncidentId(inc.getId());
        ca.setEmergencyCost(emergencyCost);
        ca.setRepairCost(repairCost);
        ca.setCompensationAmount(compAmount);
        ca.setTotalAmount(emergencyCost.add(repairCost).add(compAmount));
        ca.setStatus(Constants.CostStatus.PENDING);

        // 默认分摊建议：权属已确认 → 权属单位承担；责任待定 → 街道先行垫付待复核
        List<Map<String, Object>> alloc = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        if (Constants.OwnerStatus.CONFIRMED.equals(inc.getOwnerStatus()) && inc.getOwnerId() != null) {
            String org = userRepo.findById(inc.getOwnerId()).map(User::getOrgName).orElse("权属单位");
            item.put("party", "权属单位-" + org);
            item.put("amount", ca.getTotalAmount());
            item.put("reason", "权属已确认，由权属单位承担全部费用");
        } else {
            item.put("party", "街道（先行垫付）");
            item.put("amount", ca.getTotalAmount());
            item.put("reason", "权属责任待定，街道先行垫付，待复核后分摊");
        }
        alloc.add(item);
        ca.setAllocations(incidentService.writeJson(alloc));
        costRepo.save(ca);
    }
}
