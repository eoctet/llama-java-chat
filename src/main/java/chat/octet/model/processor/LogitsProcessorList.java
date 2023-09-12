package chat.octet.model.processor;


import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;

public final class LogitsProcessorList extends ArrayList<LogitsProcessor> implements LogitsProcessor {

    @Override
    public float[] processor(@Nullable int[] inputTokenIds, @Nonnull float[] scores, Object... args) {
        Preconditions.checkNotNull(scores, "Scores cannot be null");

        float[] result = null;
        for (LogitsProcessor pro : this) {
            result = pro.processor(inputTokenIds, scores, args);
        }
        return result;
    }
}
