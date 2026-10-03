package com.city.manhole.controller;

import com.city.manhole.common.ApiResponse;
import com.city.manhole.dto.Req;
import com.city.manhole.model.ClaimMaterial;
import com.city.manhole.model.CompensationCase;
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

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(compensationService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<CompensationCase> update(@PathVariable Long id,
                                                @RequestBody Req.CompUpdateReq req) {
        return ApiResponse.ok(compensationService.updateCase(id, req.damageType(),
                req.claimAmount(), req.accidentProof()));
    }

    /** 录入事故时间：系统自动判定与首次上报的时效关系并生成平台状态快照 */
    @PostMapping("/{id}/accident-time")
    public ApiResponse<CompensationCase> accidentTime(@PathVariable Long id,
                                                      @RequestBody Req.AccidentTimeReq req) {
        return ApiResponse.ok(compensationService.setAccidentTime(id, req.accidentTime()));
    }

    /** 补充材料内容/附件 */
    @PutMapping("/{id}/materials/{mid}")
    public ApiResponse<ClaimMaterial> updateMaterial(@PathVariable Long id, @PathVariable Long mid,
                                                     @RequestBody Req.MaterialUpdateReq req) {
        return ApiResponse.ok(compensationService.updateMaterial(id, mid,
                req.content(), req.fileUrl(), req.submitted()));
    }

    /** 核验单项材料 */
    @PostMapping("/{id}/materials/{mid}/verify")
    public ApiResponse<ClaimMaterial> verifyMaterial(@PathVariable Long id, @PathVariable Long mid,
                                                     @RequestBody Req.MaterialVerifyReq req) {
        return ApiResponse.ok(compensationService.verifyMaterial(id, mid,
                req.verifyStatus(), req.verifyNote()));
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
}
