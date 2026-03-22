package com.example.exam.service;

import com.example.exam.dto.ExamAiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class ExamAiClient {

    private final RestClient restClient;

    public ExamAiClient(@Value("${exam-ai.base-url:http://localhost:5001}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Submit PDF for async processing. Returns job info with job_id.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitPdf(MultipartFile file, String geminiApiKey) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("pdf_file", resource);

            if (geminiApiKey != null && !geminiApiKey.isBlank()) {
                body.add("gemini_api_key", geminiApiKey);
            }

            return restClient.post()
                    .uri("/process-pdf")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit PDF to exam-ai service: " + e.getMessage(), e);
        }
    }

    /**
     * Poll job status. Returns status, progress, message, and result when complete.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getJobStatus(String jobId) {
        try {
            return restClient.get()
                    .uri("/status/{jobId}", jobId)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to check job status: " + e.getMessage(), e);
        }
    }
}
