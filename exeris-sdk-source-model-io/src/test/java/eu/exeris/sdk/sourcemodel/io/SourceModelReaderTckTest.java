package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.tck.AbstractMetadataReaderTck;
import org.junit.jupiter.api.DisplayName;

/**
 * Binds {@link SourceModelReader} to the SDK's own reader TCK, so the kit's cases run against a
 * real reader on every build of this repository rather than only against the hand-built reference
 * binding in the kit's self-tests.
 *
 * <p>A kit case that nothing runs catches nothing. This binding is what makes a reader regression
 * — a dropped {@code @Saga.version} read, say, which leaves every saga at the default {@code 1}
 * while the processor carries the declared value — fail here, on the case written for it, rather
 * than pass every build in both repositories.
 *
 * <p>No facet is declared unsupported. The reader reads every facet the suite asks about, and
 * declaring one would turn a future regression in it into a skip.
 */
@DisplayName("SourceModelReader passes the exeris-sdk-tck reader suite")
class SourceModelReaderTckTest extends AbstractMetadataReaderTck {

    private final SourceModelReader reader = new SourceModelReader();

    @Override
    protected DomainMetadata read(String entitySource) {
        return reader.read(entitySource).orElseThrow(() -> new AssertionError(
                "SourceModelReader found no @ExerisDomain type in a TCK corpus source"));
    }
}
