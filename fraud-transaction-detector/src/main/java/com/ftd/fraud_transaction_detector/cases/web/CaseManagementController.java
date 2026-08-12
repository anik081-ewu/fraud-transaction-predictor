package com.ftd.fraud_transaction_detector.cases.web;

import com.ftd.fraud_transaction_detector.cases.dto.*;
import com.ftd.fraud_transaction_detector.cases.service.CaseManagementService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
public class CaseManagementController {
    private final CaseManagementService caseManagementService;

    public CaseManagementController(CaseManagementService caseManagementService) {
        this.caseManagementService = caseManagementService;
    }

    @PostMapping public CaseResponse createCase(@RequestBody CreateCaseRequest request) { return caseManagementService.createCase(request); }
    @GetMapping public List<CaseResponse> listCases() { return caseManagementService.listCases(); }
    @GetMapping("/{caseId}") public CaseResponse getCase(@PathVariable Long caseId) { return caseManagementService.getCase(caseId); }
    @PutMapping("/{caseId}/status") public CaseResponse updateStatus(@PathVariable Long caseId, @RequestBody UpdateCaseStatusRequest request) { return caseManagementService.updateStatus(caseId, request); }
    @PostMapping("/{caseId}/notes") public CaseResponse addNote(@PathVariable Long caseId, @RequestBody AddCaseNoteRequest request) { return caseManagementService.addNote(caseId, request); }
    @PostMapping("/{caseId}/false-positive") public CaseResponse markFalsePositive(@PathVariable Long caseId, @RequestBody(required = false) CaseActionRequest request) { return caseManagementService.markFalsePositive(caseId, request); }

    @PostMapping(value = "/{caseId}/str-xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> generateStrXml(@PathVariable Long caseId, @RequestBody(required = false) CaseActionRequest request) {
        GeneratedStrXml generated = caseManagementService.generateStrXml(caseId, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(generated.fileName()).build().toString())
                .contentType(MediaType.APPLICATION_XML)
                .body(generated.content());
    }

    @GetMapping("/health") public Map<String, Object> health() { return Map.of("module", "cases", "status", "UP"); }
}
