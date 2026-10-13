package com.retention.platform.customer_retention_platform.controller;

import com.retention.platform.customer_retention_platform.dto.UploadResponse;
import com.retention.platform.customer_retention_platform.service.CustomerImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerUploadController {

    private final CustomerImportService customerImportService;

    public CustomerUploadController(
            CustomerImportService customerImportService) {
        this.customerImportService = customerImportService;
    }

    @PostMapping(
            value = "/upload",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<UploadResponse> uploadCustomers(
            @RequestParam("file") MultipartFile file) {

        try {

            int recordsLoaded =
                    customerImportService.importFile(file);

            UploadResponse response = new UploadResponse(
                    "Customer data uploaded successfully",
                    recordsLoaded
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            UploadResponse response = new UploadResponse(
                    e.getMessage(),
                    0
            );

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            UploadResponse response = new UploadResponse(
                    "Failed to process uploaded file",
                    0
            );

            return ResponseEntity.internalServerError()
                    .body(response);
        }
    }
}