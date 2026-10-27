package in.gov.aicte.platform.application;

import in.gov.aicte.platform.application.catalogue.ApplicationTypeCatalogue;
import in.gov.aicte.platform.application.catalogue.ApplicationTypeDefinition;
import in.gov.aicte.platform.application.dto.ApplicationTypeDetailResponse;
import in.gov.aicte.platform.application.dto.ApplicationTypeSummaryResponse;
import in.gov.aicte.platform.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/application-types")
@Tag(name = "Application Types", description = "AICTE Approval Process Handbook (APH) application categories and requirements")
public class ApplicationTypeController {

    private final ApplicationTypeCatalogue catalogue;

    public ApplicationTypeController(ApplicationTypeCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping
    @Operation(summary = "List all APH application types", description = "Retrieve all 12 verified APH application categories with statutory metadata")
    public ResponseEntity<List<ApplicationTypeSummaryResponse>> getAllTypes() {
        List<ApplicationTypeSummaryResponse> list = catalogue.getAllTypes().stream()
                .map(ApplicationTypeSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get application type requirements", description = "Retrieve form sections and document checklists for an application type")
    public ResponseEntity<ApplicationTypeDetailResponse> getTypeDetail(@PathVariable String code) {
        ApplicationTypeDefinition def = catalogue.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Application type not found: " + code));
        return ResponseEntity.ok(ApplicationTypeDetailResponse.from(def));
    }
}
