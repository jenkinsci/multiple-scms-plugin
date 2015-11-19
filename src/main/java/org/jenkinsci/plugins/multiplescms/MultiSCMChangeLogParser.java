package org.jenkinsci.plugins.multiplescms;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
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

import hudson.scm.SCMDescriptor;
import org.apache.commons.lang.StringEscapeUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class MultiSCMChangeLogParser extends ChangeLogParser {
    public static final String ROOT_XML_TAG = "multi-scm-log";
    public static final String SUB_LOG_TAG = "sub-log";

    private final Map<String, ChangeLogParser> scmLogParsers;
    private final Map<String, String> scmDisplayNames;

    public MultiSCMChangeLogParser(List<SCM> scms) {
        scmLogParsers = new HashMap<String, ChangeLogParser>();
        scmDisplayNames = new HashMap<String, String>();
        for(SCM scm : scms) {
            String key = scm.getKey();
            if(!scmLogParsers.containsKey(key)) {
                scmLogParsers.put(key, scm.createChangeLogParser());
                String displayedKey;
                { //manipulate the key to be more presentable, GIT centric
                    int postLastSlash = key.lastIndexOf('/')+1;
                    int lastDot = key.substring(postLastSlash).lastIndexOf('.');
                    lastDot=((lastDot==-1)?-1:lastDot+postLastSlash);
                    int endIndex = lastDot != -1 ? lastDot : key.length();
                    displayedKey = key.substring(postLastSlash , endIndex);
                }
                SCMDescriptor<?> descriptor = scm.getDescriptor();
                String descriptiveName = descriptor.getDisplayName() + " ID: " + displayedKey;
                scmDisplayNames.put(key,descriptiveName);
            }
        }
    }

    private class LogSplitter extends DefaultHandler {

        private final MultiSCMChangeLogSet changeLogs;
        private final AbstractBuild build;
        private final File tempFile;
        private String scmClass;
        private StringBuffer buffer;

        public LogSplitter(AbstractBuild build, String tempFilePath) {
            changeLogs = new MultiSCMChangeLogSet(build);
            this.tempFile= new File(tempFilePath);
            this.build = build;
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
                    if(parser != null) {
                        ChangeLogSet<? extends ChangeLogSet.Entry> cls = parser.parse(build, tempFile);
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
    public ChangeLogSet<? extends Entry> parse(AbstractBuild build, File changelogFile)
        throws IOException, SAXException {

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

        LogSplitter splitter = new LogSplitter(build, changelogFile.getPath() + ".temp2");
        parser.parse(changelogFile, splitter);
        return splitter.getChangeLogSets();
    }
}
