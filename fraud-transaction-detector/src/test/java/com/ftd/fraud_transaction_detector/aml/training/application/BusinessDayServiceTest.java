package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.infrastructure.BusinessDayRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessDayServiceTest {

    @Test
    void blocksExportWhenAnyTransactionDateIsOpen() {
        BusinessDayRepository repository = mock(BusinessDayRepository.class);
        LocalDate date = LocalDate.of(2026, 8, 4);
        when(repository.findUnclosedTransactionDates(date, date)).thenReturn(List.of(date));

        assertThrows(IllegalStateException.class, () ->
                new BusinessDayService(repository).requireClosed(date, date));
    }

    @Test
    void closesEveryTransactionDateInHistoricalRange() {
        BusinessDayRepository repository = mock(BusinessDayRepository.class);
        LocalDate fromDate = LocalDate.of(2023, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 1);
        when(repository.closeRange(fromDate, toDate, "analyst")).thenReturn(261);

        int closedDateCount = new BusinessDayService(repository)
                .closeRange(fromDate, toDate, " analyst ");

        assertEquals(261, closedDateCount);
        verify(repository).closeRange(fromDate, toDate, "analyst");
    }

    @Test
    void rejectsReversedHistoricalRange() {
        BusinessDayRepository repository = mock(BusinessDayRepository.class);
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2023, 1, 1);

        assertThrows(IllegalArgumentException.class, () ->
                new BusinessDayService(repository).closeRange(fromDate, toDate, "analyst"));
    }
}
