package com.example.movierecommendation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.MovieReportRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class MovieReportServiceTest {

    @Mock
    private MovieReportRepository reportRepository;

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieReportService reportService;

    @Test
    public void testSubmitReport_Success() {
        User user = new User();
        user.setUserId(1);
        user.setUsername("testuser");

        Movie movie = new Movie();
        movie.setMovieId(10);
        movie.setTitle("Test Movie");

        when(movieRepository.findById(10)).thenReturn(Optional.of(movie));
        when(reportRepository.save(any(MovieReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MovieReport result = reportService.submitReport(user, 10, MovieReportType.BROKEN_TRAILER, "Trailer is broken");

        assertNotNull(result);
        assertEquals(user, result.getUser());
        assertEquals(movie, result.getMovie());
        assertEquals(MovieReportType.BROKEN_TRAILER, result.getReportType());
        assertEquals("Trailer is broken", result.getMessage());
        assertEquals(MovieReportStatus.NEW, result.getStatus());
    }

    @Test
    public void testSubmitReport_OtherRequiresMessage() {
        User user = new User();

        assertThrows(IllegalArgumentException.class, () -> {
            reportService.submitReport(user, 10, MovieReportType.OTHER, "   ");
        });
    }

    @Test
    public void testUpdateReportStatus() {
        MovieReport report = new MovieReport();
        report.setReportId(100);
        report.setStatus(MovieReportStatus.NEW);

        when(reportRepository.findById(100)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(MovieReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MovieReport result = reportService.updateReportStatus(100, MovieReportStatus.RESOLVED, "Fixed trailer URL");

        assertNotNull(result);
        assertEquals(MovieReportStatus.RESOLVED, result.getStatus());
        assertEquals("Fixed trailer URL", result.getAdminNote());
        assertNotNull(result.getResolvedAt());
    }
}
