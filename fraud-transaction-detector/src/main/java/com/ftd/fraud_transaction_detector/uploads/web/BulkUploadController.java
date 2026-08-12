package com.ftd.fraud_transaction_detector.uploads.web;

import com.ftd.fraud_transaction_detector.uploads.service.ExcelBulkUploadService;
import com.ftd.fraud_transaction_detector.uploads.web.dto.BulkUploadStartResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/uploads")
public class BulkUploadController {

    private final ExcelBulkUploadService excelBulkUploadService;

    public BulkUploadController(ExcelBulkUploadService excelBulkUploadService) {
        this.excelBulkUploadService = excelBulkUploadService;
    }

    @PostMapping(value = "/transactions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkUploadStartResponse uploadTransactions(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy
    ) throws IOException {
        return excelBulkUploadService.startUpload(file, uploadedBy);
    }

}
