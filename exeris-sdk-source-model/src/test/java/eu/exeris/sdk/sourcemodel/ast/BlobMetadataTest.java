package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BlobMetadata")
class BlobMetadataTest {

    @Test
    void unrestrictedHasDerivedContainerAndNoTypes() {
        BlobMetadata b = BlobMetadata.unrestricted();
        assertThat(b.container()).isNull();
        assertThat(b.hasContainer()).isFalse();
        assertThat(b.contentTypes()).isEmpty();
        assertThat(b.hasContentTypes()).isFalse();
    }

    @Test
    void ofContentTypesNarrowsTypesButNotTheContainer() {
        BlobMetadata b = BlobMetadata.ofContentTypes(List.of("application/pdf"));
        assertThat(b.container()).isNull();
        assertThat(b.contentTypes()).containsExactly("application/pdf");
        assertThat(b.hasContentTypes()).isTrue();
    }

    @Test
    void blankContainerNormalizesToNull() {
        // @Blob.container defaults to "" (annotation attributes cannot default to
        // null), and "" means "derive it" — the same state as absent. Normalizing
        // keeps the empty-string default off the wire and out of hasContainer().
        assertThat(new BlobMetadata("   ", null).container()).isNull();
        assertThat(new BlobMetadata("", null).hasContainer()).isFalse();
        assertThat(new BlobMetadata("statements", null).container()).isEqualTo("statements");
    }

    @Test
    void nullContentTypesBecomeEmptyList() {
        assertThat(new BlobMetadata(null, null).contentTypes()).isNotNull().isEmpty();
    }

    @Test
    void contentTypesCopiedDefensively() {
        List<String> src = new ArrayList<>();
        src.add("application/pdf");
        BlobMetadata b = new BlobMetadata(null, src);
        src.add("image/png");
        assertThat(b.contentTypes()).containsExactly("application/pdf");
    }
}
