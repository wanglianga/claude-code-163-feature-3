package com.city.manhole.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 赔付案件：市民或车辆受损时，赔付专员依据上报时间、临时围护、权属确认、维修时长、事故证明定责。
 */
@Data
@Entity
@Table(name = "compensation_case")
public class CompensationCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long incidentId;

    /** 经办的赔付专员 */
    private Long specialistId;

    @Column(length = 64)
    private String claimantName;

    @Column(length = 32)
    private String claimantPhone;

    /** 损失类型：人身伤害/车辆损失/财产损失 */
    @Column(length = 32)
    private String damageType;

    /** 索赔金额 */
    @Column(precision = 12, scale = 2)
    private BigDecimal claimAmount = BigDecimal.ZERO;

    /** 事故证明（材料说明） */
    @Column(columnDefinition = "TEXT")
    private String accidentProof;

    /** 事故时间（索赔人主张，赔付专员核验后录入） */
    private LocalDateTime accidentTime;

    /** 时间线核验结论标记，见 Constants.TimelineFlag */
    @Column(length = 32)
    private String timelineFlag;

    /** 系统生成的时间线核验分析（事故时间 vs 上报/围护/权属确认记录） */
    @Column(columnDefinition = "TEXT")
    private String timelineAnalysis;

    /** 责任分析（上报时间、围护时效、权属确认、维修时长、事故证明综合判断） */
    @Column(columnDefinition = "TEXT")
    private String analysis;

    /** 责任认定，见 Constants.Responsibility */
    @Column(length = 32)
    private String responsibility;

    /** 权属单位承担比例 % */
    private Integer ownerRatio = 0;

    /** 街道承担比例 % */
    private Integer streetRatio = 0;

    /** 核定赔付金额 */
    @Column(precision = 12, scale = 2)
    private BigDecimal approvedAmount = BigDecimal.ZERO;

    /** 见 Constants.CompStatus */
    @Column(nullable = false, length = 16)
    private String status;

    private LocalDateTime decidedAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt = LocalDateTime.now();
}
