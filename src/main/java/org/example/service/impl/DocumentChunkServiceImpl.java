package org.example.service.impl;

import org.example.config.DocumentChunkConfig;
import org.example.dto.DocumentChunk;
import org.example.service.DocumentChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分片服务实现
 */
@Service
public class DocumentChunkServiceImpl implements DocumentChunkService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkServiceImpl.class);

    @Autowired
    private DocumentChunkConfig chunkConfig;

    @Override
    public List<DocumentChunk> chunkDocument(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            logger.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        List<Section> sections = splitByHeadings(content);

        int globalChunkIndex = 0;
        for (Section section : sections) {
            List<DocumentChunk> sectionChunks = chunkSection(section, globalChunkIndex);
            chunks.addAll(sectionChunks);
            globalChunkIndex += sectionChunks.size();
        }

        logger.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());
        return chunks;
    }

    private List<Section> splitByHeadings(String content) {
        List<Section> sections = new ArrayList<>();

        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);

        int lastEnd = 0;
        String currentTitle = null;

        while (matcher.find()) {
            if (lastEnd < matcher.start()) {
                String sectionContent = content.substring(lastEnd, matcher.start()).trim();
                if (!sectionContent.isEmpty()) {
                    sections.add(new Section(currentTitle, sectionContent, lastEnd));
                }
            }

            currentTitle = matcher.group(2).trim();
            lastEnd = matcher.start();
        }

        if (lastEnd < content.length()) {
            String sectionContent = content.substring(lastEnd).trim();
            if (!sectionContent.isEmpty()) {
                sections.add(new Section(currentTitle, sectionContent, lastEnd));
            }
        }

        if (sections.isEmpty()) {
            sections.add(new Section(null, content, 0));
        }

        return sections;
    }

    private List<DocumentChunk> chunkSection(Section section, int startChunkIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = section.content;
        String title = section.title;

        if (content.length() <= chunkConfig.getMaxSize()) {
            DocumentChunk chunk = new DocumentChunk(
                content,
                section.startIndex,
                section.startIndex + content.length(),
                startChunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
            return chunks;
        }

        List<String> paragraphs = splitByParagraphs(content);

        StringBuilder currentChunk = new StringBuilder();
        int currentStartIndex = section.startIndex;
        int chunkIndex = startChunkIndex;

        for (String paragraph : paragraphs) {
            if (currentChunk.length() > 0 &&
                currentChunk.length() + paragraph.length() > chunkConfig.getMaxSize()) {

                String chunkContent = currentChunk.toString().trim();
                DocumentChunk chunk = new DocumentChunk(
                    chunkContent,
                    currentStartIndex,
                    currentStartIndex + chunkContent.length(),
                    chunkIndex++
                );
                chunk.setTitle(title);
                chunks.add(chunk);

                String overlap = getOverlapText(chunkContent);
                currentChunk = new StringBuilder(overlap);
                currentStartIndex = currentStartIndex + chunkContent.length() - overlap.length();
            }

            currentChunk.append(paragraph).append("\n\n");
        }

        if (currentChunk.length() > 0) {
            String chunkContent = currentChunk.toString().trim();
            DocumentChunk chunk = new DocumentChunk(
                chunkContent,
                currentStartIndex,
                currentStartIndex + chunkContent.length(),
                chunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
        }

        return chunks;
    }

    private List<String> splitByParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();

        String[] parts = content.split("\n\n+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        return paragraphs;
    }

    private String getOverlapText(String text) {
        int overlapSize = Math.min(chunkConfig.getOverlap(), text.length());
        if (overlapSize <= 0) {
            return "";
        }

        String overlap = text.substring(text.length() - overlapSize);

        int lastSentenceEnd = Math.max(
            overlap.lastIndexOf('。'),
            Math.max(overlap.lastIndexOf('？'), overlap.lastIndexOf('！'))
        );

        if (lastSentenceEnd > overlapSize / 2) {
            return overlap.substring(lastSentenceEnd + 1).trim();
        }

        return overlap.trim();
    }

    @Override
    public List<DocumentChunk> chunkDocumentV2(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            logger.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        try {
            TokenTextSplitter splitter = new TokenTextSplitter(
                chunkConfig.getMaxSize(),
                chunkConfig.getOverlap(),
                5,
                10000,
                true
            );

            Document document = new Document(content);
            document.getMetadata().put("source", filePath);

            List<Document> splitDocuments = splitter.apply(List.of(document));

            int chunkIndex = 0;
            for (Document splitDoc : splitDocuments) {
                String chunkContent = splitDoc.getText();
                DocumentChunk chunk = new DocumentChunk(
                    chunkContent,
                    0,
                    chunkContent.length(),
                    chunkIndex++
                );
                chunk.setTitle(filePath);
                chunks.add(chunk);
            }

            logger.info("文档分片完成(TokenTextSplitter): {} -> {} 个分片", filePath, chunks.size());
        } catch (Exception e) {
            logger.error("TokenTextSplitter 分片失败: {}, 错误: {}", filePath, e.getMessage(), e);
        }

        return chunks;
    }

    private static class Section {
        String title;
        String content;
        int startIndex;

        Section(String title, String content, int startIndex) {
            this.title = title;
            this.content = content;
            this.startIndex = startIndex;
        }
    }
}