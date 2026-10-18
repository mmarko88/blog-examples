package pro.professionaldev.zipstream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SizeCheckerConstructorTests {
    @Test
    void extractedFileSizeLimitInKB_whenNegativeOrZeroFileSize_thenThrowException() {
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SizeChecker(-1, 1, 1, 1));

        Assertions.assertEquals("Extracted file limit size cannot be negative number",
                illegalArgumentException.getMessage());
    }

    @Test
    void extractedSingleFileSizeLimitKB_whenNegativeOrZeroFileSize_thenThrowException() {
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SizeChecker(1, -1, 1, 1));

        Assertions.assertEquals("Extracted single file size limit cannot be zero or negative number",
                illegalArgumentException.getMessage());
    }

    @Test
    void thresholdEntries_whenNegativeOrZeroFileSize_thenThrowException() {
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SizeChecker(1, 1, -1, 1));

        Assertions.assertEquals("Threshold entries must be greater than zero.",
                illegalArgumentException.getMessage());
    }

    @Test
    void thresholdRatio_whenNegativeOrZeroFileSize_thenThrowException() {
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SizeChecker(1, 1, 1, -1));

        Assertions.assertEquals("Threshold ratio must be greater than zero.",
                illegalArgumentException.getMessage());
    }
}
