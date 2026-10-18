package dev.professional.blog.fastinsert.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TestResultService {
    private final TestResultRepository testResultRepository;

    public void save(TestResult testResult) {
        testResultRepository.save(testResult);
    }
}
