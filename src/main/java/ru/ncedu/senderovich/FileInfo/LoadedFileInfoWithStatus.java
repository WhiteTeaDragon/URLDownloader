package ru.ncedu.senderovich.FileInfo;

public class LoadedFileInfoWithStatus {
    public final LoadedFileInfo fileInfo;
    public final Status status;

    private LoadedFileInfoWithStatus(LoadedFileInfo fileInfo, Status status) {
        this.fileInfo = fileInfo;
        this.status = status;
    }

    public static LoadedFileInfoWithStatus ok(LoadedFileInfo loadedFileInfo) {
        return new LoadedFileInfoWithStatus(loadedFileInfo, Status.OK);
    }

    public static LoadedFileInfoWithStatus fail(Status status) {
       return new LoadedFileInfoWithStatus(LoadedFileInfo.DEFAULT, status);
    }

    public boolean isOk() {
        return status == Status.OK && fileInfo.relativePath != null;
    }
}
