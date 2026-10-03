package com.city.manhole.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 全部请求体定义 */
public final class Req {
    private Req() {}

    public record LoginReq(String username, String password) {}
    public record RegisterReq(String username, String password, String name, String phone) {}

    public record IncidentCreateReq(String issueType, Double longitude, Double latitude,
                                    String address, String roadType, List<String> photos,
                                    Boolean hasDamage, String damageDesc, String dangerLevel) {}

    public record SecureReq(List<String> measures, BigDecimal cost, String note) {}

    public record InvestigateReq(String manholeMarking, String pipelineInfo, String constructionHistory,
                                 String merchantInfo, String cityLedger, String conclusion,
                                 Long confirmedOwnerId, String note) {}

    public record ClaimReq(String note) {}

    public record RepairOrderReq(Long teamId) {}
    public record RepairFinishReq(String chamberCondition, String coverSpec, Boolean needRoadClosure,
                                  Boolean needCoord, String coordUnits, String repairNote,
                                  BigDecimal repairCost) {}

    public record AcceptanceReq(Boolean photoOk, Boolean locationOk, Boolean flatnessOk,
                                Boolean rollingTestOk, String result, String note) {}

    public record CompUpdateReq(String damageType, BigDecimal claimAmount, String accidentProof,
                                java.time.LocalDateTime accidentTime) {}
    public record CompDecideReq(String result, String responsibility, Integer ownerRatio,
                                Integer streetRatio, BigDecimal approvedAmount, String analysis) {}

    public record MaterialReq(String type, String title, String fileUrl, String content) {}
    public record MaterialVerifyReq(String status, String verifyNote) {}
    public record FollowUpCompleteReq(String checkNote) {}

    public record CostReviewReq(List<Map<String, Object>> allocations, String reviewNote) {}

    public record UserCreateReq(String username, String password, String name,
                                String role, String orgName, String phone) {}
    public record RouteReq(Long id, String name, String sections, String frequency, String note) {}
    public record RuleReq(Long id, String title, String content) {}
}
