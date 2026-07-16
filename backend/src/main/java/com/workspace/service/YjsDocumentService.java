package com.workspace.service;

import com.workspace.repository.YjsDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class YjsDocumentService {

    private final YjsDocumentRepository repository;
    private final int maxDocumentBytes;

    public YjsDocumentService(
            YjsDocumentRepository repository,
            @Value("${collaboration.max-document-bytes}") int maxDocumentBytes
    ) {
        if (maxDocumentBytes <= 0) {
            throw new IllegalStateException(
                    "collaboration.max-document-bytes must be greater than zero");
        }
        this.repository = repository;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    public YjsDocumentRepository.LoadedDocument load(UUID spaceId, UUID nodeId) {
        YjsDocumentRepository.LoadedDocument document = repository.find(spaceId, nodeId);
        if (document == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Collaboration document node not found"
            );
        }
        return document;
    }

    public YjsDocumentRepository.StoredDocument store(
            UUID spaceId,
            UUID nodeId,
            byte[] state,
            int schemaVersion
    ) {
        if (state == null || state.length == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yjs document state must not be empty"
            );
        }
        if (state.length > maxDocumentBytes) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Yjs document exceeds the configured size limit"
            );
        }
        if (schemaVersion <= 0 || schemaVersion > Short.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yjs schema version must be between 1 and " + Short.MAX_VALUE
            );
        }

        YjsDocumentRepository.StoredDocument stored = repository.upsert(
                spaceId,
                nodeId,
                state,
                (short) schemaVersion
        );
        if (stored == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Collaboration document node not found"
            );
        }
        return stored;
    }

    public boolean isStorageReady() {
        return repository.isStorageReady();
    }
}
