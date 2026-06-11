package com.medkernel.compliance.personnel;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 人员、任职、账号和身份来源统一管理入口。
 */
@RestController
@RequestMapping("/api/v1/compliance/personnel")
@DataScope(requireTenant = true)
public class PersonnelController {

    private static final String READ_GUARD =
        "@perm.has('org.read') and hasAnyRole('SYSTEM_SUPERADMIN','PLATFORM_GOVERNANCE_ADMIN',"
            + "'ORGANIZATION_ADMIN','IDENTITY_ACCESS_ADMIN')";
    private static final String WRITE_GUARD =
        "@perm.has('org.write') and hasAnyRole('SYSTEM_SUPERADMIN','PLATFORM_GOVERNANCE_ADMIN',"
            + "'ORGANIZATION_ADMIN','IDENTITY_ACCESS_ADMIN')";

    private final PersonnelService service;
    private final PersonnelImportService imports;

    public PersonnelController(PersonnelService service, PersonnelImportService imports) {
        this.service = service;
        this.imports = imports;
    }

    @GetMapping
    @PreAuthorize(READ_GUARD)
    public ApiResult<PageResponse<PersonnelSummary>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.page(new PageRequest(page, size, "displayName,asc"), keyword));
    }

    @GetMapping("/{personId}")
    @PreAuthorize(READ_GUARD)
    public ApiResult<PersonnelDetail> detail(@PathVariable String personId) {
        return ApiResult.ok(service.detail(personId));
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<PersonnelDetail> create(
            @Valid @RequestBody PersonCreateRequest request,
            Authentication authentication) {
        return ApiResult.ok(service.create(request, authentication));
    }

    @PostMapping(
        path = "/imports:preview",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<PersonnelImportResponse> preview(
            @RequestPart("file") MultipartFile file) {
        return ApiResult.ok(imports.preview(file));
    }

    @PostMapping("/imports/{jobId}:commit")
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<PersonnelImportResponse> commit(
            @PathVariable String jobId,
            Authentication authentication) {
        return ApiResult.ok(imports.commit(jobId, authentication));
    }

    @GetMapping("/imports/{jobId}")
    @PreAuthorize(READ_GUARD)
    public ApiResult<PersonnelImportResponse> importResult(@PathVariable String jobId) {
        return ApiResult.ok(imports.get(jobId));
    }

    @GetMapping(value = "/import-template", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(READ_GUARD)
    public ResponseEntity<String> importTemplate() {
        String body = """
            人员编号,姓名,机构编码,科室编码,病区编码,人员类型,岗位,登录名,角色,身份来源,院内身份标识
            EMP-001,王医生,HOSP-A,CARDIO,CARDIO-W1,院内人员,主治医师,wang.doctor,临床决策使用者,工号,EMP-001
            """;
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''%E4%BA%BA%E5%91%98%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body("\uFEFF" + body);
    }
}
