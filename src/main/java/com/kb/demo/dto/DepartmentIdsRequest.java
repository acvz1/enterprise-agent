package com.kb.demo.dto;

import java.util.LinkedHashSet;
import java.util.Set;

/** 请求中显式提交的数据范围。 */
public class DepartmentIdsRequest {
    private Set<Long> departmentIds = new LinkedHashSet<>();

    public Set<Long> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(Set<Long> departmentIds) {
        this.departmentIds = departmentIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(departmentIds);
    }
}
