package ru.smetrix.service;

import org.springframework.stereotype.Service;
import ru.smetrix.fgis.FgisFileImportResult;
import ru.smetrix.fgis.FgisImportResult;
import ru.smetrix.fgis.FgisMaterialRecord;
import ru.smetrix.fgis.FgisXlsxParseResult;
import ru.smetrix.fgis.FgisXlsxParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class FgisFileImportService {

    private static final int BATCH_SIZE = 500;

    private final FgisXlsxParser parser;
    private final FgisMaterialImportService importService;

    public FgisFileImportService(FgisXlsxParser parser, FgisMaterialImportService importService) {
        this.parser = parser;
        this.importService = importService;
    }

    public FgisFileImportResult importXlsx(
            InputStream inputStream,
            String regionCode,
            String quarter
    ) throws IOException {
        List<FgisMaterialRecord> batch = new ArrayList<>(BATCH_SIZE);
        ResultAccumulator accumulator = new ResultAccumulator();

        FgisXlsxParseResult parsing = parser.parse(inputStream, regionCode, quarter, record -> {
            batch.add(record);
            if (batch.size() >= BATCH_SIZE) {
                accumulator.add(importService.importRecords(batch));
                batch.clear();
            }
        });

        if (!batch.isEmpty()) {
            accumulator.add(importService.importRecords(batch));
        }

        FgisImportResult database = accumulator.result
                .plus(new FgisImportResult(0, 0, 0, parsing.rejectedRows()));
        return new FgisFileImportResult(parsing, database);
    }

    private static final class ResultAccumulator {
        private FgisImportResult result = new FgisImportResult(0, 0, 0, 0);

        private void add(FgisImportResult batchResult) {
            result = result.plus(batchResult);
        }
    }
}
