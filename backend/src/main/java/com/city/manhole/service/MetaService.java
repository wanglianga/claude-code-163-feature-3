package com.city.manhole.service;

import com.city.manhole.common.Constants;
import com.city.manhole.model.User;
import com.city.manhole.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 下拉选项等元数据 */
@Service
@RequiredArgsConstructor
public class MetaService {
    private final UserRepo userRepo;

    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issueTypes", IncidentService.ISSUE_TYPES);
        m.put("roadTypes", IncidentService.ROAD_TYPES);
        m.put("dangerLevels", IncidentService.DANGER_LEVELS);
        m.put("measureTypes", EmergencyService.MEASURE_TYPES);
        m.put("coordUnits", List.of("燃气", "电力", "通信"));
        m.put("damageTypes", List.of("人身伤害", "车辆损失", "财产损失"));
        m.put("materialTypes", List.of(Constants.MaterialType.DASHCAM, Constants.MaterialType.POLICE_CERT,
                Constants.MaterialType.REPAIR_INVOICE, Constants.MaterialType.OTHER));
        m.put("ownerUnits", userRepo.findByRole(Constants.Role.OWNER_UNIT).stream()
                .map(this::toOption).toList());
        m.put("repairTeams", userRepo.findByRole(Constants.Role.REPAIR_TEAM).stream()
                .map(this::toOption).toList());
        return m;
    }

    private Map<String, Object> toOption(User u) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", u.getId());
        o.put("name", u.getName());
        o.put("orgName", u.getOrgName());
        return o;
    }
}
