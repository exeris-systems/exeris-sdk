package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelMetadata")
class ChannelMetadataTest {

    @Test
    void declaredCarriesNothingButExists() {
        // The state the facet exists to make expressible: an entity that says it has a
        // duplex channel and says nothing else about it. A boolean could not hold this
        // apart from "no channel at all".
        ChannelMetadata c = ChannelMetadata.declared();
        assertThat(c.messageType()).isNull();
        assertThat(c.subprotocol()).isNull();
        assertThat(c.hasMessageType()).isFalse();
        assertThat(c.hasSubprotocol()).isFalse();
    }

    @Test
    void ofCarriesTheMessageTypeAndNoSubprotocol() {
        ChannelMetadata c = ChannelMetadata.of("ArticleEdit");
        assertThat(c.messageType()).isEqualTo("ArticleEdit");
        assertThat(c.hasMessageType()).isTrue();
        assertThat(c.subprotocol()).isNull();
        assertThat(c.hasSubprotocol()).isFalse();
    }

    @Test
    void bothComponentsAreCarriedVerbatim() {
        ChannelMetadata c = new ChannelMetadata("ArticleEdit", "exeris.edit.v1");
        assertThat(c.messageType()).isEqualTo("ArticleEdit");
        assertThat(c.subprotocol()).isEqualTo("exeris.edit.v1");
        assertThat(c.hasMessageType()).isTrue();
        assertThat(c.hasSubprotocol()).isTrue();
    }

    @Test
    @DisplayName("blank normalises to null, so \"\" and absent are not two spellings of one state")
    void blankNormalisesToNull() {
        // The annotation's attributes default to "" because an annotation attribute
        // cannot default to absent. Left alone, every @Channel with no message type
        // would put an empty string on the wire and a reader would need its own blank
        // check to tell it from a declared one. Normalising here is what lets a
        // consumer test with != null — the treatment streamEventType already gets.
        ChannelMetadata empty = new ChannelMetadata("", "");
        assertThat(empty.messageType()).isNull();
        assertThat(empty.subprotocol()).isNull();

        ChannelMetadata whitespace = new ChannelMetadata("   ", "\t");
        assertThat(whitespace.messageType()).isNull();
        assertThat(whitespace.subprotocol()).isNull();

        ChannelMetadata nulls = new ChannelMetadata(null, null);
        assertThat(nulls.messageType()).isNull();
        assertThat(nulls.subprotocol()).isNull();

        // And the three spellings of "nothing declared" converge on one value.
        assertThat(empty).isEqualTo(whitespace).isEqualTo(nulls)
                .isEqualTo(ChannelMetadata.declared());
    }

    @Test
    void aNonBlankValueIsNotTrimmed() {
        // Normalisation collapses blank to null; it does not edit a real value. The SDK
        // stores what the author wrote and interprets nothing.
        ChannelMetadata c = new ChannelMetadata(" ArticleEdit ", null);
        assertThat(c.messageType()).isEqualTo(" ArticleEdit ");
    }

    @Test
    void differentComponentsAreNotEqual() {
        assertThat(ChannelMetadata.of("A")).isNotEqualTo(ChannelMetadata.of("B"));
        assertThat(new ChannelMetadata("A", "p1")).isNotEqualTo(new ChannelMetadata("A", "p2"));
        assertThat(ChannelMetadata.of("A")).isNotEqualTo(ChannelMetadata.declared());
    }
}
