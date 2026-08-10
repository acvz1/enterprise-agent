package com.kb.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {

    @Test
    void protectsKnowledgeAndAdministrationEndpointsWithBusinessAuthorities() {
        assertPolicy(AiController.class, "ask",
                "hasAuthority('qa:ask') and hasAuthority('document:read')");
        assertPolicy(AiController.class, "askStream",
                "hasAuthority('qa:ask') and hasAuthority('document:read')");
        assertPolicy(AiController.class, "clearCache", "hasRole('ADMIN')");
        assertPolicy(AgentController.class, "ask", "hasAuthority('qa:ask')");
        assertPolicy(AnalyticsController.class, "getDashboardStats",
                "hasAuthority('dashboard:view')");
        assertPolicy(DocumentController.class, "vectorizeAllDocuments", "hasRole('ADMIN')");

        Map<Class<?>, Map<String, String>> policies = Map.of(
                DocumentCategoryController.class, Map.of(
                        "getAllCategories", "hasAuthority('document:read')",
                        "getCategoryById", "hasAuthority('document:read')",
                        "createCategory", "hasAuthority('document:write')",
                        "updateCategory", "hasAuthority('document:write')",
                        "deleteCategory", "hasAuthority('document:delete') or hasRole('ADMIN')"),
                DocumentTagController.class, Map.of(
                        "getAllTags", "hasAuthority('document:read')",
                        "getTagById", "hasAuthority('document:read')",
                        "createTag", "hasAuthority('document:write')",
                        "updateTag", "hasAuthority('document:write')",
                        "deleteTag", "hasAuthority('document:delete') or hasRole('ADMIN')"),
                DocumentVersionController.class, Map.of(
                        "createVersion", "hasAuthority('document:write')",
                        "getVersionsByDocumentId", "hasAuthority('document:read')",
                        "getLatestVersionByDocumentId", "hasAuthority('document:read')",
                        "getVersionByDocumentIdAndVersionNumber", "hasAuthority('document:read')",
                        "revertToVersion", "hasAuthority('document:write')",
                        "compareVersions", "hasAuthority('document:read')"));

        policies.forEach((controller, methods) ->
                methods.forEach((method, policy) -> assertPolicy(controller, method, policy)));
    }

    private void assertPolicy(Class<?> controller, String methodName, String expectedPolicy) {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation)
                .as("%s.%s must declare @PreAuthorize", controller.getSimpleName(), methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedPolicy);
    }
}
