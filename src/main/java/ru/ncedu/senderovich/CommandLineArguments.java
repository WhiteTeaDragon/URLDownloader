package ru.ncedu.senderovich;

import com.beust.jcommander.Parameter;

public class CommandLineArguments {
    @Parameter(names = {"-h", "--help"}, help = true)
    public boolean help;

    @Parameter(names={"-u", "--url"}, required = true,
            description = "URL to download")
    public String url;

    @Parameter(names={"-p", "--path"}, description = "path to the output file")
    public String outputPath;

    @Parameter(names={"-o", "--open_result"}, description = "opens the output file in new window")
    public boolean openResult = false;
}
