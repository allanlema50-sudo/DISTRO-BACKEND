package com.example.distrobackend.controller;

import com.example.distrobackend.service.SettlementProofDocumentService.ProofDownload;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Payment proofs are private: always an attachment, never sniffed or cached. */
public final class ProofDownloadResponses {

    private ProofDownloadResponses() { }

    public static ResponseEntity<byte[]> of(ProofDownload proof) {
        String filename = proof.filename().replaceAll("[^A-Za-z0-9._-]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .contentLength(proof.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(proof.content());
    }
}