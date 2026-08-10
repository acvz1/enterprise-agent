package com.kb.demo.controller;

import com.kb.demo.dto.DepartmentIdsRequest;
import com.kb.demo.entity.Department;
import com.kb.demo.repository.DepartmentRepository;
import com.kb.demo.service.DepartmentAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** P0 的最小管理接口：管理员配置部门及用户的数据范围。 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentRepository departmentRepository;
    private final DepartmentAccessService departmentAccessService;

    public DepartmentController(DepartmentRepository departmentRepository, DepartmentAccessService departmentAccessService) {
        this.departmentRepository = departmentRepository;
        this.departmentAccessService = departmentAccessService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Department> list() {
        return departmentRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Department> create(@RequestBody Department department) {
        if (department.getCode() == null || department.getCode().isBlank()
                || department.getName() == null || department.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (departmentRepository.existsByCode(department.getCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentRepository.save(department));
    }

    @PutMapping("/users/{username}/access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateUserAccess(@PathVariable String username,
            @RequestBody DepartmentIdsRequest request) {
        departmentAccessService.setUserDepartments(username, request.getDepartmentIds());
        return ResponseEntity.noContent().build();
    }
}
