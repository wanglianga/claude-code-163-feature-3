package com.city.manhole.controller;

import com.city.manhole.common.ApiResponse;
import com.city.manhole.dto.Req;
import com.city.manhole.model.ClaimMaterial;
import com.city.manhole.model.CompensationCase;
import com.city.manhole.model.RepairFollowUp;
import com.city.manhole.service.CompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compensations")
@RequiredArgsConstructor
public class CompensationController {
    private final CompensationService compensationService;

    @GetMapping
    public ApiResponse<List<CompensationCase>> list() {
        return ApiResponse.ok(compensationService.listAll());
    }

    /** 审核工作台聚合数据（案件/平台时间线/材料/风险线索/维修追踪/复发关联） */
    @GetMapping("/{id}/review")
    public ApiResponse<Map<String, Object>> review(@PathVariable Long id) {
        return ApiResponse.ok(compensationService.review(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<CompensationCase> update(@PathVariable Long id,
                                                @RequestBody Req.CompUpdateReq req) {
        return ApiResponse.ok(compensationService.updateCase(id, req.damageType(),
                req.claimAmount(), req.accidentProof(), req.accidentTime()));
    }

    @PostMapping("/{id}/decide")
    public ApiResponse<CompensationCase> decide(@PathVariable Long id,
                                                @RequestBody Req.CompDecideReq req) {
        return ApiResponse.ok(compensationService.decide(id, req.result(), req.responsibility(),
                req.ownerRatio(), req.streetRatio(), req.approvedAmount(), req.analysis()));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<CompensationCase> pay(@PathVariable Long id) {
        return ApiResponse.ok(compensationService.pay(id));
    }

    // ---------- 赔付材料 ----------

    @PostMapping("/{id}/materials")
    public ApiResponse<ClaimMaterial> addMaterial(@PathVariable Long id,
                                                  @RequestBody Req.MaterialReq req) {
        return ApiResponse.ok(compensationService.addMaterial(id, req.type(), req.title(),
                req.fileUrl(), req.content()));
    }

    @PostMapping("/materials/{materialId}/verify")
    public ApiResponse<ClaimMaterial> verifyMaterial(@PathVariable Long materialId,
                                                     @RequestBody Req.MaterialVerifyReq req) {
        return ApiResponse.ok(compensationService.verifyMaterial(materialId, req.status(), req.verifyNote()));
    }

    // ---------- 赔付后维修追踪 ----------

    @GetMapping("/follow-ups")
    public ApiResponse<List<Map<String, Object>>> followUps() {
        return ApiResponse.ok(compensationService.listFollowUps());
    }

    @PostMapping("/follow-ups/{id}/complete")
    public ApiResponse<RepairFollowUp> completeFollowUp(@PathVariable Long id,
                                                        @RequestBody Req.FollowUpCompleteReq req) {
        return ApiResponse.ok(compensationService.completeFollowUp(id, req.checkNote()));
    }

    // ---------- 风险线索 ----------

    @GetMapping("/risk-clues")
    public ApiResponse<List<Map<String, Object>>> riskClues() {
        return ApiResponse.ok(compensationService.listRiskClues());
    }
}
