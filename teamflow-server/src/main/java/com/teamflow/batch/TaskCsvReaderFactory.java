package com.teamflow.batch;

import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.file.service.FileStorageService;
import com.teamflow.core.task.domain.TaskPriority;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.stereotype.Component;

/**
 * 创建基于 Apache Commons CSV 的 UTF-8 流式任务读取器。
 */
@Component
public class TaskCsvReaderFactory {

    static final List<String> REQUIRED_HEADERS = List.of(
            "title",
            "description",
            "priority",
            "assigneeEmail",
            "dueAt"
    );

    private final FileStorageService fileStorageService;

    public TaskCsvReaderFactory(FileStorageService fileStorageService) {
        this.fileStorageService = Objects.requireNonNull(
                fileStorageService,
                "文件存储服务不能为 null"
        );
    }

    public ItemStreamReader<TaskCsvRow> create(String storagePath) {
        return new StreamingTaskCsvReader(
                fileStorageService,
                Objects.requireNonNull(storagePath, "CSV 存储路径不能为 null")
        );
    }

    private static final class StreamingTaskCsvReader
            implements ItemStreamReader<TaskCsvRow> {

        private static final String POSITION_KEY =
                "teamflow.taskCsvReader.consumedRecords";

        private final FileStorageService storage;
        private final String storagePath;

        private CSVParser parser;
        private Iterator<CSVRecord> records;
        private long consumedRecords;

        private StreamingTaskCsvReader(
                FileStorageService storage,
                String storagePath
        ) {
            this.storage = storage;
            this.storagePath = storagePath;
        }

        @Override
        public void open(ExecutionContext executionContext) {
            close();
            long restartPosition = executionContext.getLong(POSITION_KEY, 0L);
            InputStream inputStream = null;
            Reader reader = null;
            try {
                inputStream = storage.open(storagePath);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                ));
                bufferedReader.mark(1);
                if (bufferedReader.read() != '\ufeff') {
                    bufferedReader.reset();
                }
                reader = bufferedReader;
                CSVFormat format = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setAllowDuplicateHeaderNames(false)
                        .get();
                parser = format.parse(reader);
                requireHeaders(parser.getHeaderNames());
                records = parser.iterator();
                consumedRecords = 0;
                while (consumedRecords < restartPosition) {
                    if (!records.hasNext()) {
                        throw new CsvFileFormatException(
                                "CSV 重启位置超过文件实际记录数"
                        );
                    }
                    records.next();
                    consumedRecords++;
                }
            } catch (IOException | RuntimeException exception) {
                closeQuietly(parser, reader, inputStream);
                parser = null;
                records = null;
                if (exception instanceof CsvFileFormatException formatError) {
                    throw formatError;
                }
                throw new ItemStreamException("无法打开 CSV 文件", exception);
            }
        }

        @Override
        public TaskCsvRow read() throws Exception {
            if (records == null) {
                throw new ItemStreamException("CSV Reader 尚未打开");
            }
            while (true) {
                final CSVRecord record;
                try {
                    if (!records.hasNext()) {
                        return null;
                    }
                    record = records.next();
                } catch (UncheckedIOException exception) {
                    throw exception.getCause();
                }
                consumedRecords++;
                long rowNumber = record.getRecordNumber() + 1L;
                if (!record.isConsistent()) {
                    throw new CsvRowValidationException(
                            rowNumber,
                            "列数必须与表头一致，期望 "
                                    + REQUIRED_HEADERS.size()
                                    + " 列，实际 "
                                    + record.size()
                                    + " 列"
                    );
                }
                if (isBlank(record)) {
                    continue;
                }
                return toRow(record, rowNumber);
            }
        }

        @Override
        public void update(ExecutionContext executionContext) {
            executionContext.putLong(POSITION_KEY, consumedRecords);
        }

        @Override
        public void close() {
            if (parser == null) {
                records = null;
                return;
            }
            try {
                parser.close();
            } catch (IOException exception) {
                throw new ItemStreamException("关闭 CSV 文件失败", exception);
            } finally {
                parser = null;
                records = null;
            }
        }

        private static TaskCsvRow toRow(
                CSVRecord record,
                long rowNumber
        ) {
            String priorityText = record.get("priority").strip();
            final TaskPriority priority;
            try {
                priority = TaskPriority.valueOf(
                        priorityText.toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                throw new CsvRowValidationException(
                        rowNumber,
                        "priority 必须是 LOW、MEDIUM、HIGH 或 URGENT",
                        exception
                );
            }

            String dueAt = stripToNull(record.get("dueAt"));
            if (dueAt != null) {
                try {
                    dueAt = UtcTimeText.requireValid(dueAt, "dueAt");
                } catch (IllegalArgumentException exception) {
                    throw new CsvRowValidationException(
                            rowNumber,
                            "dueAt 必须是固定毫秒精度的 UTC 时间",
                            exception
                    );
                }
            }
            return new TaskCsvRow(
                    rowNumber,
                    record.get("title"),
                    stripToNull(record.get("description")),
                    priority,
                    stripToNull(record.get("assigneeEmail")),
                    dueAt
            );
        }

        private static boolean isBlank(CSVRecord record) {
            return REQUIRED_HEADERS.stream()
                    .map(record::get)
                    .allMatch(String::isBlank);
        }

        private static String stripToNull(String value) {
            String stripped = value == null ? null : value.strip();
            return stripped == null || stripped.isEmpty() ? null : stripped;
        }

        private static void requireHeaders(List<String> actualHeaders) {
            if (!actualHeaders.equals(REQUIRED_HEADERS)
                    || new HashSet<>(actualHeaders).size()
                    != actualHeaders.size()) {
                throw new CsvFileFormatException(
                        "CSV 表头必须严格为 "
                                + String.join(",", REQUIRED_HEADERS)
                );
            }
        }

        private static void closeQuietly(
                CSVParser parser,
                Reader reader,
                InputStream inputStream
        ) {
            try {
                if (parser != null) {
                    parser.close();
                } else if (reader != null) {
                    reader.close();
                } else if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignored) {
                // 保留原始打开或格式异常。
            }
        }
    }
}
