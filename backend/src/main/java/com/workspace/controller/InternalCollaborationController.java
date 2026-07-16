package com.workspace.controller;

import com.workspace.repository.YjsDocumentRepository;
import com.workspace.service.YjsDocumentService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hocuspocus 专用内部接口，不提供给浏览器前端调用。
 */
@RestController
@RequestMapping("/internal/collaboration")
public class InternalCollaborationController {

    public static final String SCHEMA_VERSION_HEADER = "X-Yjs-Schema-Version";
    public static final String REVISION_HEADER = "X-Yjs-Revision";

    private final YjsDocumentService service;

    public InternalCollaborationController(YjsDocumentService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        if (!service.isStorageReady()) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "unavailable",
                    "storage", "yjs_documents"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "storage", "yjs_documents"
        ));
    }

    @GetMapping(
            value = "/documents/{nodeId}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public ResponseEntity<byte[]> load(
            @PathVariable UUID nodeId,
            @RequestParam UUID spaceId
    ) {
        YjsDocumentRepository.LoadedDocument document = service.load(spaceId, nodeId);

        if (document.state() == null) {
            return ResponseEntity.noContent()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(SCHEMA_VERSION_HEADER, String.valueOf(document.schemaVersion()))
                .header(REVISION_HEADER, String.valueOf(document.revision()))
                .body(document.state());
    }

    @PutMapping(
            value = "/documents/{nodeId}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> store(
            @PathVariable UUID nodeId,
            @RequestParam UUID spaceId,
            @RequestHeader(name = SCHEMA_VERSION_HEADER, defaultValue = "1")
            int schemaVersion,
            @RequestBody byte[] state
    ) {
        YjsDocumentRepository.StoredDocument stored = service.store(
                spaceId,
                nodeId,
                state,
                schemaVersion
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId);
        result.put("revision", stored.revision());
        result.put("byteSize", stored.byteSize());
        result.put("updatedAt", stored.updatedAt());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
