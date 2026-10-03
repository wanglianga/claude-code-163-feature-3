package com.city.manhole.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险线索：事故发生于首次上报前时，平台无在案记录，仅登记风险线索备查，
 * 不构成值班处置时效与权属响应责任。
 */
@Data
@Entity
@Table(name = "risk_clue")
public class RiskClue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    /** 关联的赔付案件 */
    @Column(nullable = false, unique = true)
    private Long caseId;

    /** 索赔人主张的事故时间 */
    @Column(nullable = false)
    private LocalDateTime accidentTime;

    /** 平台首次上报时间（快照） */
    @Column(nullable = false)
    private LocalDateTime firstReportTime;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** 登记人（赔付专员） */
    @Column(nullable = false)
    private Long createdBy;

    private LocalDateTime createdAt = LocalDateTime.now();
}
