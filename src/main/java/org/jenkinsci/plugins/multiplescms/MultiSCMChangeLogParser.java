package org.jenkinsci.plugins.multiplescms;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang.StringEscapeUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class MultiSCMChangeLogParser extends ChangeLogParser {
    public static final String ROOT_XML_TAG = "multi-scm-log";
    public static final String SUB_LOG_TAG = "sub-log";

    private final Map<String, ChangeLogParser> scmLogParsers;
    private final Map<String, String> scmDisplayNames;
    private final Map<String, RepositoryBrowser<?>> scmRepositoryBrowsers;

    public MultiSCMChangeLogParser(List<SCM> scms) {
        scmLogParsers = new HashMap<String, ChangeLogParser>();
        scmDisplayNames = new HashMap<String, String>();
        scmRepositoryBrowsers = new HashMap<String, RepositoryBrowser<?>>();

        for(SCM scm : scms) {
            String key = scm.getKey();
            if(!scmLogParsers.containsKey(key)) {
                scmLogParsers.put(key, scm.createChangeLogParser());
                String displayName = scm.getDescriptor().getDisplayName();
                if (key != displayName) {
                    displayName = String.format("%s (%s)", displayName, key);
                }
                scmDisplayNames.put(key, displayName);
                scmRepositoryBrowsers.put(key, scm.getBrowser());
            }
        }
    }

    private class LogSplitter extends DefaultHandler {

        private final MultiSCMChangeLogSet changeLogs;
        private final Run<?,?> build;
        private RepositoryBrowser<?> browser;
        private final File tempFile;
        private String scmClass;
        private StringBuffer buffer;

        public LogSplitter(Run<?,?> build, RepositoryBrowser<?> browser, String tempFilePath) {
            changeLogs = new MultiSCMChangeLogSet(build, browser);
            this.tempFile= new File(tempFilePath);
            this.build = build;
            this.browser = browser;
        }

        @Override
        public void characters(char[] data, int startIndex, int length)
                throws SAXException {
                if(buffer != null) {
                    while(length > 0 && Character.isWhitespace(data[startIndex])) {
                        startIndex += 1;
                        length -= 1;
                    }
                    for (int i = 0; i < length; i++) {
                        buffer.append(data[startIndex + i]);
                    }
                }

        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attrs) throws SAXException {
            if(qName.compareTo(SUB_LOG_TAG) == 0) {
                scmClass = attrs.getValue("scm");
                buffer = new StringBuffer();
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {

            if(qName.compareTo(SUB_LOG_TAG) == 0) {
                try {
                    OutputStreamWriter outputStream = new OutputStreamWriter(new FileOutputStream(tempFile));
                    //un-escaping the XMl so it is written to the temp file correctly
                    String data = StringEscapeUtils.unescapeXml(buffer.toString());
                    outputStream.write(data);
                    outputStream.close();
                    buffer = null;
                    ChangeLogParser parser = scmLogParsers.get(scmClass);

                    /*
                     * Due to XSTREAM serialization scmRepositoryBrowsers may be null.
                     */
//                    RepositoryBrowser<?> browser = null;
//                    if (scmRepositoryBrowsers != null) {
//                        browser = scmRepositoryBrowsers.get(scmClass);
//                    }
                    if(parser != null) {
                        ChangeLogSet<? extends ChangeLogSet.Entry> cls;
                        if (browser != null) {
                            cls = parser.parse(build, browser, tempFile);
                        } else {
                            cls = parser.parse(build, browser, tempFile);
                        }
                        changeLogs.add(scmClass, scmDisplayNames.get(scmClass), cls);

                    }
                } catch (RuntimeException e) {
                    throw new SAXException("could not parse changelog file", e);
                } catch (FileNotFoundException e) {
                    throw new SAXException("could not create temp changelog file", e);
                } catch (IOException e) {
                    throw new SAXException("could not close temp changelog file", e);
                }


            }
        }

        public ChangeLogSet<? extends Entry> getChangeLogSets() {
            return changeLogs;
        }
    }

    @Override
    public ChangeLogSet<? extends Entry> parse(Run build, RepositoryBrowser<?> browser, File changelogFile) throws IOException, SAXException {

        if(scmLogParsers == null)
            return ChangeLogSet.createEmpty(build);

          SAXParserFactory factory = SAXParserFactory.newInstance();
          factory.setValidating(true);
          SAXParser parser;
        try {
            parser = factory.newSAXParser();
        } catch (ParserConfigurationException e) {
            throw new SAXException("Could not create parser", e);
        }

        LogSplitter splitter = new LogSplitter(build, browser, changelogFile.getPath() + ".temp2");
        parser.parse(changelogFile, splitter);
        return splitter.getChangeLogSets();
    }
}
