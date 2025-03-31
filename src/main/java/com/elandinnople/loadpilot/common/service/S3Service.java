package com.elandinnople.loadpilot.common.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class S3Service {

    private final AmazonS3 s3Client;
    private final String bucketName;

    public S3Service(
            AmazonS3 s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String uploadTestResult(Long testId, String resultJson) {
        try {
            String key = String.format("results/%d/summary.json", testId);
            s3Client.putObject(bucketName, key, resultJson);

            // S3 객체에 대한 URL 생성
            return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
        } catch (Exception e) {
            log.error("S3 업로드 중 오류: {}", e.getMessage());
            throw new RuntimeException("테스트 결과 업로드 중 오류가 발생했습니다.", e);
        }
    }

    public String getResultUrl(Long testId) {
        String key = String.format("results/%d/summary.json", testId);
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
    }

    // 파일명을 지정할 수 있는 메서드 추가
    public String uploadTestResult(Long testId, String resultJson, String fileName) {
        try {
            String key = String.format("results/%d/%s", testId, fileName);
            s3Client.putObject(bucketName, key, resultJson);

            // S3 객체에 대한 URL 생성
            return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
        } catch (Exception e) {
            log.error("S3 업로드 중 오류: {}", e.getMessage());
            throw new RuntimeException("테스트 결과 업로드 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 테스트 결과 파일을 S3에서 삭제하는 메서드
     */
    public void deleteTestResults(Long testId) {
        try {
            String prefix = String.format("results/%d/", testId);

            // 해당 테스트 ID의 모든 결과 파일 목록 조회
            ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                    .withBucketName(bucketName)
                    .withPrefix(prefix);

            ListObjectsV2Result result;
            do {
                result = s3Client.listObjectsV2(listObjectsRequest);

                // 조회된 파일들 삭제
                List<DeleteObjectsRequest.KeyVersion> keys = result.getObjectSummaries().stream()
                        .map(s3Object -> new DeleteObjectsRequest.KeyVersion(s3Object.getKey()))
                        .collect(Collectors.toList());

                if (!keys.isEmpty()) {
                    DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(bucketName)
                            .withKeys(keys);
                    s3Client.deleteObjects(deleteRequest);
                    log.info("Deleted {} objects from S3 for test ID {}", keys.size(), testId);
                }

                // 다음 페이지가 있으면 계속 조회
                listObjectsRequest.setContinuationToken(result.getNextContinuationToken());
            } while (result.isTruncated());

        } catch (Exception e) {
            log.error("Error deleting test results from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to delete test results from S3", e);
        }
    }
}
