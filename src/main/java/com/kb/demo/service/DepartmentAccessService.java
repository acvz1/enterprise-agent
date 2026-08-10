package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.entity.Department;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.User;
import com.kb.demo.repository.DepartmentRepository;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限的唯一入口：ADMIN 全局可见；其他用户只能访问与其部门有交集的文档。
 */
@Service
public class DepartmentAccessService {
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DocumentRepository documentRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public DepartmentAccessService(UserRepository userRepository, DepartmentRepository departmentRepository,
            DocumentRepository documentRepository, RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.documentRepository = documentRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public AccessScope currentScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("未登录，无法确定数据访问范围");
        }
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (administrator) {
            return AccessScope.globalScope();
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("当前用户不存在"));
        Set<Long> departmentIds = user.getAccessibleDepartments().stream()
                .map(Department::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            return AccessScope.limitedScope(departmentIds);
    }

    public String currentScopeCacheKey() {
        AccessScope scope = currentScope();
        if (scope.global()) {
            return "global";
        }
        return scope.departmentIds().stream().sorted().map(String::valueOf).collect(Collectors.joining("-", "dept-", ""));
    }

    public boolean canRead(Document document) {
        return canRead(document, currentScope());
    }

    public boolean canRead(Document document, AccessScope scope) {
        if (scope.global()) {
            return true;
        }
        return document.getVisibleDepartments().stream()
                .map(Department::getId)
                .anyMatch(scope.departmentIds()::contains);
    }

    public List<Document> filterReadableDocuments(Collection<Document> documents) {
        AccessScope scope = currentScope();
        if (scope.global()) {
            return List.copyOf(documents);
        }
        return documents.stream().filter(document -> canRead(document, scope)).toList();
    }

    public List<RetrievalCandidate> filterCandidates(List<RetrievalCandidate> candidates, AccessScope scope) {
        if (scope.global()) {
            return candidates;
        }
        Set<Long> allowedDocumentIds = readableDocumentIds(scope);
        return candidates.stream()
                .filter(candidate -> allowedDocumentIds.contains(candidate.getDocumentId()))
                .toList();
    }

    public List<FusedRetrievalCandidate> filterFusedCandidates(List<FusedRetrievalCandidate> candidates, AccessScope scope) {
        if (scope.global()) {
            return candidates;
        }
        Set<Long> allowedDocumentIds = readableDocumentIds(scope);
        return candidates.stream()
                .filter(candidate -> allowedDocumentIds.contains(candidate.getDocumentId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> readableDocumentIds(AccessScope scope) {
        if (scope.global()) {
            return Set.of();
        }
        if (scope.departmentIds().isEmpty()) {
            return Set.of();
        }
        return documentRepository.findReadableDocumentIds(scope.departmentIds());
    }

    /** Ensure document visibility is within the writer's own data scope unless the writer is an administrator. */
    @Transactional
    public void setDocumentDepartments(Document document, Set<Long> requestedDepartmentIds) {
        AccessScope scope = currentScope();
        Set<Long> normalizedIds = requestedDepartmentIds == null ? Set.of() : new LinkedHashSet<>(requestedDepartmentIds);
        if (!scope.global() && !scope.departmentIds().containsAll(normalizedIds)) {
            throw new AccessDeniedException("不能将文档授权给自己无权访问的部门");
        }
        List<Department> departments = departmentRepository.findAllById(normalizedIds);
        if (departments.size() != normalizedIds.size()) {
            throw new IllegalArgumentException("存在不存在的部门 ID");
        }
        document.setVisibleDepartments(new LinkedHashSet<>(departments));
        clearAccessSensitiveAnswerCache();
    }

    /** 新建文档时，普通用户省略范围则默认归入自己的部门；管理员必须显式配置范围。 */
    @Transactional
    public void applyNewDocumentDepartments(Document document, Set<Long> requestedDepartmentIds) {
        setDocumentDepartments(document, resolveNewDocumentDepartmentIds(requestedDepartmentIds));
    }

    @Transactional(readOnly = true)
    public Set<Long> resolveNewDocumentDepartmentIds(Set<Long> requestedDepartmentIds) {
        AccessScope scope = currentScope();
        Set<Long> effectiveDepartmentIds = requestedDepartmentIds == null || requestedDepartmentIds.isEmpty()
                ? (scope.global() ? Set.of() : scope.departmentIds())
                : new LinkedHashSet<>(requestedDepartmentIds);
        if (!scope.global() && !scope.departmentIds().containsAll(effectiveDepartmentIds)) {
            throw new AccessDeniedException("不能将文档授权给自己无权访问的部门");
        }
        if (departmentRepository.findAllById(effectiveDepartmentIds).size() != effectiveDepartmentIds.size()) {
            throw new IllegalArgumentException("存在不存在的部门 ID");
        }
        return effectiveDepartmentIds;
    }

    /** 异步任务使用提交请求时已经验证过的部门 ID，不依赖丢失的 SecurityContext。 */
    @Transactional
    public void applyBackgroundDocumentDepartments(Document document, Set<Long> departmentIds) {
        Set<Long> normalizedIds = departmentIds == null ? Set.of() : new LinkedHashSet<>(departmentIds);
        List<Department> departments = departmentRepository.findAllById(normalizedIds);
        if (departments.size() != normalizedIds.size()) {
            throw new IllegalArgumentException("存在不存在的部门 ID");
        }
        document.setVisibleDepartments(new LinkedHashSet<>(departments));
        clearAccessSensitiveAnswerCache();
    }

    @Transactional(readOnly = true)
    public boolean canReadDocumentId(Long documentId) {
        return documentRepository.findById(documentId)
                .map(this::canRead)
                .orElse(false);
    }

    @Transactional
    public void setUserDepartments(String username, Set<Long> requestedDepartmentIds) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        Set<Long> normalizedIds = requestedDepartmentIds == null ? Set.of() : new LinkedHashSet<>(requestedDepartmentIds);
        List<Department> departments = departmentRepository.findAllById(normalizedIds);
        if (departments.size() != normalizedIds.size()) {
            throw new IllegalArgumentException("存在不存在的部门 ID");
        }
        user.setAccessibleDepartments(new LinkedHashSet<>(departments));
        userRepository.save(user);
        clearAccessSensitiveAnswerCache();
    }

    /** 数据范围变化时，清空可能包含旧授权信息的答案缓存。 */
    private void clearAccessSensitiveAnswerCache() {
        Set<String> keys = redisTemplate.keys("ai:answer:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public record AccessScope(boolean global, Set<Long> departmentIds) {
        static AccessScope globalScope() {
            return new AccessScope(true, Set.of());
        }

        static AccessScope limitedScope(Set<Long> departmentIds) {
            return new AccessScope(false, Set.copyOf(departmentIds));
        }
    }
}
