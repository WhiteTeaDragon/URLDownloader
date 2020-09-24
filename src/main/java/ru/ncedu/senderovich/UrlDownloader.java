package ru.ncedu.senderovich;

import com.beust.jcommander.JCommander;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mozilla.universalchardet.UniversalDetector;
import ru.ncedu.senderovich.FileInfo.LoadedFileInfo;
import ru.ncedu.senderovich.FileInfo.LoadedFileInfoWithStatus;
import ru.ncedu.senderovich.FileInfo.Status;
import ru.ncedu.senderovich.ImageSource.ImageSource;
import ru.ncedu.senderovich.ImageSource.LocalImageSource;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;

public class UrlDownloader {
    private final static String DEFAULT_FILENAME = "index.html";
    private final static int MAX_PATH = 200;

    private static boolean askIfShouldReplace(Scanner in) {
        String question = "Do you want to replace it? y/n: ";
        String result = "";
        while (!result.equals("y") && !result.equals("n")) {
            System.out.print("\n" + question);
            result = in.next();
        }
        return result.equals("y");
    }

    private static String getFilenameFromURI(URI uri, String defaultName) {
        String path = uri.getPath();
        String fileName = "";
        if (path.length() != 0) {
            if (path.charAt(path.length() - 1) == '/') { // for cases "...mail.ru/something/?text=..."
                path = path.substring(0, path.length() - 1);
            }
            fileName = path.substring(path.lastIndexOf('/') + 1);
        }
        if (fileName.length() == 0) {
            fileName = defaultName;
        }
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return fileName;
    }

    private static FileName getFileNameWithoutRepeats(URI uri, String defaultName, File directory) {
        File currFile = new File(directory, getFilenameFromURI(uri, defaultName));
        String path = currFile.getPath();
        String name = currFile.getName();
        if (path.length() > MAX_PATH) {
            int len = path.length() - MAX_PATH;
            if (name.length() > len) {
                name = name.substring(0, name.length() - len);
            }
        }
        currFile = new File(directory, name);
        FileName fileNameObject = new FileName(currFile.getPath());
        while (currFile.exists()) {
            fileNameObject.incrementCounter();
            currFile = new File(fileNameObject.toString());
        }
        return fileNameObject;
    }

    private static File createResultFile(String path, File parent, String fileNameFromURL) {
        File resultFile = new File(parent, path);
        return resultFile.exists() && resultFile.isDirectory() ? new File(resultFile, fileNameFromURL) : resultFile;
    }

    private static LoadedFileInfoWithStatus loadResource(URI currUri, File currLink, File directory, String isSuppl) {
        Charset charset;
        boolean isHTML;
        RequestConfig customizedRequestConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build();
        HttpClientBuilder customizedClientBuilder =
                HttpClients.custom().setDefaultRequestConfig(customizedRequestConfig);
        customizedClientBuilder.disableCookieManagement();
        try (CloseableHttpClient httpClient = customizedClientBuilder.build()) {
            HttpUriRequest request = new HttpGet(currUri);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 5.1; rv:19.0) Gecko/20100101 Firefox/19.0");
            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return LoadedFileInfoWithStatus.fail(Status.EMPTY_SITE);
            }
            ContentType contentType = ContentType.getOrDefault(entity);
            charset = contentType.getCharset();
            isHTML = contentType.getMimeType().equals("text/html");
            try (InputStream urlStream = entity.getContent()) {
                Files.copy(urlStream, currLink.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.out.println("Connection failed while copying" + isSuppl);
                System.out.println(e.getMessage());
                return LoadedFileInfoWithStatus.fail(Status.CONNECTION_FAILED);
            }
        } catch (IOException e) {
            System.out.println("Cannot connect to the" + isSuppl + " web-site");
            System.out.println(e.getMessage());
            return LoadedFileInfoWithStatus.fail(Status.CONNECTION_FAILED);
        }
        String relativePath = null;
        if (directory != null) {
            if (directory.getParentFile() == null) {
                relativePath = currLink.getPath();
            } else {
                relativePath = directory.getParentFile().toPath().relativize(currLink.toPath()).toString();
            }
        }
        return LoadedFileInfoWithStatus.ok(new LoadedFileInfo(relativePath, charset, isHTML));
    }

    private static boolean isProcessingAttributeFailed(Element imageElement, String attributeKey, File directory) {
        String absoluteUrl = imageElement.absUrl(attributeKey);
        if (absoluteUrl.equals("")) {
            return false;
        }
        URI currUri;
        try {
            currUri = new URI(absoluteUrl);
        } catch (URISyntaxException e) {
            System.out.print("Problem with parsing HTML-file, wrong URL given");
            System.out.println(e.getMessage());
            return true;
        }
        if (currUri.toString().length() == 0) {
            return false;
        }
        FileName fileNameObject = getFileNameWithoutRepeats(currUri, "img.jpg", directory);
        LoadedFileInfoWithStatus loaded = loadResource(currUri, new File(fileNameObject.toString()), directory,
                " supplementary");
        if (loaded.isOk()) {
            imageElement.attr(attributeKey, loaded.fileInfo.relativePath);
        }
        return false;
    }

    private static Charset determineCharset(Charset charSet, File resultFile) throws IOException, SecurityException {
        if (charSet != null) {
            return charSet;
        }
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(resultFile)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();
            if (encoding == null) {
                return null;
            }
            return Charset.forName(encoding);
        } catch (FileNotFoundException e) {
            System.out.print("The file was not created");
            System.out.println(e.getMessage());
            throw e;
        } catch (IOException e) {
            System.out.print("An I/O exception has occurred");
            System.out.println(e.getMessage());
            throw e;
        } catch (SecurityException e) {
            System.out.print("Problem with security manager");
            System.out.println(e.getMessage());
            throw e;
        }
    }

    private static URI getURIFromString(String string_url) {
        URI uri;
        try {
            uri = new URI(string_url);
        } catch (NullPointerException e) {
            System.out.print("Empty URL string");
            System.out.println(e.getMessage());
            return null;
        } catch (URISyntaxException e) {
            System.out.print("The URL violates RFC 2396, so it cannot be parsed");
            System.out.println(e.getMessage());
            return null;
        }
        return uri;
    }

    private static File getFileObject(CommandLineArguments commandLineArguments, URI uri) {
        File resultFile;
        String fileNameFromURL = getFilenameFromURI(uri, DEFAULT_FILENAME);
        boolean isPathGiven = commandLineArguments.outputPath != null;
        if (!isPathGiven) {
            resultFile = new File(fileNameFromURL);
        } else {
            resultFile = createResultFile(commandLineArguments.outputPath, null, fileNameFromURL);
        }
        String fileName;
        while (resultFile.exists()) {
            Scanner in = new Scanner(System.in);
            File parent = resultFile.getParentFile();
            if (!resultFile.isDirectory()) {
                if (parent == null) {
                    System.out.print("There is already a file \"" + resultFile.getPath() + "\".");
                } else {
                    System.out.print("There is already a file \"" + resultFile.getName() + "\" in \"" +
                            parent.getPath() + "\"");
                }
                boolean shouldReplace = askIfShouldReplace(in);
                if (shouldReplace) {
                    in.close();
                    break;
                }
            } else {
                if (parent == null) {
                    System.out.print("There is already a directory \"" + resultFile.getPath() + "\".");
                } else {
                    System.out.print("There is already a directory \"" + resultFile.getName() + "\" in \"" +
                            parent.getPath() + "\"");
                }
            }
            System.out.print("Enter a new directory or name for the file (without spaces, '/' and ':'): ");
            fileName = in.next();
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            resultFile = createResultFile(fileName, parent, fileNameFromURL);
        }
        return resultFile;
    }

    private static boolean prepareFolders(File resultFile) {
        if (resultFile.getParent() == null) {
            return false;
        }
        File parentDir = new File(resultFile.getParent());
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            System.out.println("Can not make new necessary directories.");
            return true;
        }
        if (parentDir.exists() && !parentDir.isDirectory()) {
            System.out.println("The given path is invalid, the given directory is a file, not a folder.");
            return true;
        }
        return false;
    }

    public static ImageSource[] parseSrcset(String srcset) {
        String[] sources = srcset.split(", ");
        ImageSource[] listOfSources = new ImageSource[sources.length];
        for (int i = 0; i < sources.length; ++i) {
            listOfSources[i] = new ImageSource(sources[i]);
        }
        return listOfSources;
    }

    public static String stringFromListOfSources(LocalImageSource[] listOfSources) {
        StringBuilder curr = new StringBuilder();
        String prefix = "";
        for (LocalImageSource imageSource : listOfSources) {
            curr.append(prefix);
            prefix = ", ";
            curr.append(imageSource.toString());
        }
        return curr.toString();
    }

    private static boolean downloadSupplementaryFiles(URI uri, File resultFile, Charset charSet) {
        boolean error = false;
        try {
            charSet = determineCharset(charSet, resultFile);
        } catch (IOException | SecurityException e) {
            return true;
        }
        String charSetName = null;
        if (charSet != null) {
            charSetName = charSet.name();
        }
        Document doc;
        try {
            doc = Jsoup.parse(resultFile, charSetName, uri.toString());
        } catch (IOException e) {
            System.out.print("Can not read html file at " + resultFile.getPath());
            System.out.println(e.getMessage());
            return true;
        }
        File directory = new File(resultFile.getParent(), resultFile.getName() + "_files");
        if (!directory.exists() && !directory.mkdir()) {
            System.out.println("Can not create new necessary directory for supplementary files.");
            return false;
        }
        Elements images = doc.select("img");
        String absoluteUrl;
        URI currUri;
        FileName fileNameObject;
        LoadedFileInfoWithStatus loaded;
        for (Element imageElement : images) {
            if (isProcessingAttributeFailed(imageElement, "src", directory)) {
                error = true;
                break;
            }
            if (isProcessingAttributeFailed(imageElement, "data-src", directory)) {
                error = true;
                break;
            }
            String srcsetString = imageElement.attr("srcset");
            if (srcsetString.equals("")) {
                continue;
            }
            ImageSource[] listOfSources = parseSrcset(srcsetString);
            LocalImageSource[] listOfLocalSources = new LocalImageSource[listOfSources.length];
            for (int i = 0; i != listOfSources.length; ++i) {
                ImageSource imageSource = listOfSources[i];
                try {
                    currUri = new URI(imageSource.getStringUrl());
                } catch (NullPointerException e) {
                    continue;
                } catch (URISyntaxException e) {
                    System.out.print("Problem with parsing HTML-file, wrong URL given");
                    System.out.println(e.getMessage());
                    error = true;
                    break;
                }
                currUri = uri.resolve(currUri);
                if (currUri.toString().length() == 0) {
                    continue;
                }
                fileNameObject = getFileNameWithoutRepeats(currUri, "img.jpg", directory);
                loaded = loadResource(currUri, new File(fileNameObject.toString()), directory,
                        "supplementary");
                if (loaded.isOk()) {
                    listOfLocalSources[i] = new LocalImageSource(imageSource, loaded.fileInfo.relativePath);
                } else {
                    listOfLocalSources[i] = new LocalImageSource(imageSource);
                }
            }
            imageElement.attr("srcset", stringFromListOfSources(listOfLocalSources));
        }
        if (!error) {
            Elements links = doc.select("link");
            for (Element linkElement : links) {
                if (linkElement.attr("href").equals("")) {
                    continue;
                }
                absoluteUrl = linkElement.absUrl("href");
                //System.out.println(linkElement.attr("href") + " " + absoluteUrl);
                try {
                    currUri = new URI(absoluteUrl);
                } catch (URISyntaxException e) {
                    System.out.print("Problem with parsing HTML-file, wrong URL given there");
                    System.out.println(e.getMessage());
                    break;
                }
                if (currUri.toString().length() == 0) {
                    continue;
                }
                fileNameObject = getFileNameWithoutRepeats(currUri, "resource", directory);
                loaded = loadResource(currUri, new File(fileNameObject.toString()), directory, "supplementary");
                if (loaded.isOk()) {
                    linkElement.attr("href", loaded.fileInfo.relativePath);
                }
            }
        }

        Elements base = doc.select("base");
        for (Element baseElement : base) {
            if (!baseElement.attr("href").equals("")) {
                baseElement.attr("href", ".");
            }
        }

        if (charSet == null) {
            charSet = doc.outputSettings().charset();
        }
        Elements meta = doc.select("meta");
        for (Element metaElement : meta) {
            if (!metaElement.attr("charset").equals("")) {
                metaElement.attr("charset", charSet.name());
            }
        }
        try {
            FileUtils.writeStringToFile(resultFile, doc.outerHtml(), charSet);
        } catch (UnsupportedEncodingException e) {
            System.out.print("Unsupported encoding");
            System.out.println(e.getMessage());
            return true;
        } catch (IOException e) {
            System.out.print("An I/O error has occurred");
            System.out.println(e.getMessage());
            return true;
        }
        return false;
    }

    private static void openDownloadedFileInNewWindow(File resultFile) {
        Desktop d = Desktop.getDesktop();
        if (!d.isSupported(Desktop.Action.OPEN)) {
            System.out.println("Unfortunately, your platform does not support opening of the file using Java.");
            return;
        }
        try {
            d.open(resultFile);
        } catch (IOException e) {
            System.out.print("the specified file has no associated application or the associated application " +
                    "fails to be launched");
            System.out.println(e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.print("Resulting file does not exist");
            System.out.println(e.getMessage());
        } catch (SecurityException e) {
            System.out.print("Problem with security manager");
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        CommandLineArguments commandLineArguments = new CommandLineArguments();
        JCommander jCommander = new JCommander(commandLineArguments);
        jCommander.setCaseSensitiveOptions(false);
        jCommander.parse(args);
        if (commandLineArguments.help) {
            jCommander.usage();
            return;
        }

        URI uri = getURIFromString(commandLineArguments.url);
        if (uri == null) {
            return;
        }
        File resultFile = getFileObject(commandLineArguments, uri);
        if (prepareFolders(resultFile)) {
            return;
        }
        LoadedFileInfoWithStatus info = loadResource(uri, resultFile, null, "");
        if (info.status == Status.EMPTY_SITE) {
            System.out.println("The given web-site is empty.");
            return;
        } else if (info.status == Status.CONNECTION_FAILED) {
            System.out.println("Connection failed");
            return;
        }
        boolean isHTML = info.fileInfo.isHTML;
        Charset charSet = info.fileInfo.charset;
        if (isHTML && downloadSupplementaryFiles(uri, resultFile, charSet)) {
            return;
        }
        if (commandLineArguments.openResult) {
            openDownloadedFileInNewWindow(resultFile);
        }
    }
}
