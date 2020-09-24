package ru.ncedu.senderovich;

import java.io.File;

public class FileName {
    private int cnt;
    private final String fileName;
    private final String extension;

    public FileName(String fileName) {
        cnt = 0;
        File curr = new File(fileName);
        String fileName2 = curr.getName();
        int before = fileName.lastIndexOf(fileName2);
        int point = fileName2.indexOf('.');
        if (point == -1) {
            this.fileName = fileName;
            extension = "";
        } else {
            this.fileName = fileName.substring(0, point + before);
            extension = fileName.substring(point + before);
        }
    }

    public void incrementCounter() {
        ++cnt;
    }

    @Override
    public String toString() {
        if (cnt > 0) {
            return fileName + cnt + extension;
        } else {
            return fileName + extension;
        }
    }
}
