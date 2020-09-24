package ru.ncedu.senderovich.ImageSource;

public class ImageSource {
    private final String stringUrl;
    private final String parameters;

    public ImageSource(String source) {
        int firstSpace = source.indexOf(" ");
        if (firstSpace == -1) {
            stringUrl = source;
            parameters = null;
        } else {
            stringUrl = source.substring(0, firstSpace);
            parameters = source.substring(firstSpace);
        }
    }

    public String getStringUrl() {
        return stringUrl;
    }

    public String getParameters() { return parameters; }
}
