package ru.smetrix.fgis;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.smetrix.entity.MaterialCache;
import ru.smetrix.repository.MaterialCacheRepository;
import ru.smetrix.service.FgisMaterialImportService;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FgisMaterialImportServiceTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void savesAWholeBatchAndUpdatesExistingMaterial() {
        MaterialCacheRepository repository = mock(MaterialCacheRepository.class);
        MaterialCache existingCement = new MaterialCache();
        existingCement.setId("existing");
        existingCement.setCode("CEMENT-1");
        existingCement.setName("Старое название");
        existingCement.setRegion("77");
        existingCement.setPrice(new BigDecimal("1"));
        existingCement.setLastUpdated(1L);
        when(repository.findByRegionAndCodeIn(eq("77"), anyCollection()))
                .thenReturn(List.of(existingCement));

        FgisMaterialImportService service = new FgisMaterialImportService(repository);
        FgisImportResult result = service.importRecords(List.of(
                new FgisMaterialRecord("REBAR-1", "Арматура", "т", new BigDecimal("70000"), "77", "2025-Q2"),
                new FgisMaterialRecord("CEMENT-1", "Цемент", "т", new BigDecimal("6900"), "77", "2025-Q2"),
                new FgisMaterialRecord("", "Некорректная", "т", BigDecimal.ONE, "77", "2025-Q2")
        ));

        assertThat(result).isEqualTo(new FgisImportResult(1, 1, 0, 1));
        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<MaterialCache> saved = StreamSupport
                .stream(((Iterable<MaterialCache>) captor.getValue()).spliterator(), false)
                .toList();
        assertThat(saved).extracting(MaterialCache::getCode)
                .containsExactlyInAnyOrder("REBAR-1", "CEMENT-1");
        assertThat(existingCement.getName()).isEqualTo("Цемент");
        assertThat(existingCement.getPrice()).isEqualByComparingTo("6900");
    }
}
