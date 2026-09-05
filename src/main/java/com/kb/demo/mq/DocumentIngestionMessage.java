package com.kb.demo.mq;

import java.io.Serializable;

public class DocumentIngestionMessage implements Serializable {

    private String uploadId;

    public DocumentIngestionMessage() {}

    public DocumentIngestionMessage(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
}
