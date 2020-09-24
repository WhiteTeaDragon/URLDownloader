package ru.ncedu.senderovich.FileInfo;

import java.nio.charset.Charset;

public class LoadedFileInfo {
    public final String relativePath;
    public final Charset charset;
    public final boolean isHTML;

    public static final LoadedFileInfo DEFAULT = new LoadedFileInfo(null, null, false);

    public LoadedFileInfo(String relativePath, Charset charset, boolean isHTML) {
        this.relativePath = relativePath;
        this.charset = charset;
        this.isHTML = isHTML;
    }
}
