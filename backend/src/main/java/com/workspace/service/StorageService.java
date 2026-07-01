package com.workspace.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 对象存储(对齐 Monsora 思路:bucket 私有 + presigned URL):
 *   - upload: 文件存到 MinIO,key 用 uuid 防冲突
 *   - 返回 presigned GET URL(临时,24h 过期),前端用它访问
 *
 * 真合并 Monsora 时,这一层按 Monsora 的分块上传 + 处理流水线重写。
 */
@Service
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicUrlBase;
    private final long presignedTtlSeconds;

    public StorageService(S3Client s3,
                          @Value("${storage.bucket}") String bucket,
                          @Value("${storage.public-url}") String publicUrlBase,
                          @Value("${storage.endpoint}") String endpoint,
                          @Value("${storage.region}") String region,
                          @Value("${storage.access-key}") String accessKey,
                          @Value("${storage.secret-key}") String secretKey,
                          @Value("${storage.presigned-ttl-seconds:86400}") long presignedTtlSeconds) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicUrlBase = publicUrlBase;
        this.presignedTtlSeconds = presignedTtlSeconds;
        // S3Presigner 跟 S3Client 一样要 endpoint + path-style
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(software.amazon.awssdk.regions.Region.of(region))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * 启动时确保 bucket 存在(开发期简化,生产应该预创建 + 设 lifecycle)。
     */
    @PostConstruct
    public void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    /**
     * 上传文件,返回 { url, key, size, contentType }。
     * url 是 presigned GET URL,24h 内有效。
     */
    public Map<String, Object> upload(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String safe = original == null ? "file" : original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "uploads/" + UUID.randomUUID() + "-" + safe;

        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // 生成 presigned GET URL(私有 bucket 必须靠签名访问)
        PresignedGetObjectRequest presigned = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(presignedTtlSeconds))
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build())
                        .build());

        return Map.of(
            "url", presigned.url().toString(),
            "key", key,
            "size", file.getSize(),
            "contentType", file.getContentType() == null ? "application/octet-stream" : file.getContentType()
        );
    }
}