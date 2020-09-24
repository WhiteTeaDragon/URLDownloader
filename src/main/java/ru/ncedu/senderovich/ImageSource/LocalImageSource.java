package ru.ncedu.senderovich.ImageSource;

public class LocalImageSource {
    private final String stringUrl;
    private final String parameters;

    public LocalImageSource(ImageSource prev, String source) {
        stringUrl = source;
        parameters = prev.getParameters();
    }

    public LocalImageSource(ImageSource prev) {
        stringUrl = prev.getStringUrl();
        parameters = prev.getParameters();
    }

    @Override
    public String toString() {
        return stringUrl + parameters;
    }
}
