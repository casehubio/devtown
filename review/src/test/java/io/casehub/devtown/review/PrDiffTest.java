package io.casehub.devtown.review;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PrDiffTest {

    @Test
    void fileDiffHasReviewablePatch_nonNullNonBlank() {
        var fd = new PrDiff.FileDiff("src/A.java", "modified", "+ new line", 1, 0);
        assertTrue(fd.hasReviewablePatch());
    }

    @Test
    void fileDiffHasReviewablePatch_nullPatch() {
        var fd = new PrDiff.FileDiff("image.png", "modified", null, 0, 0);
        assertFalse(fd.hasReviewablePatch());
    }

    @Test
    void fileDiffHasReviewablePatch_blankPatch() {
        var fd = new PrDiff.FileDiff("renamed.java", "renamed", "  ", 0, 0);
        assertFalse(fd.hasReviewablePatch());
    }
}
