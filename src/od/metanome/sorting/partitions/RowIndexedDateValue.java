package od.metanome.sorting.partitions;

/**
 * Created by Mehdi on 7/1/2016.
 */
import java.util.Date;

public class RowIndexedDateValue extends RowIndexedValue {
    public final Date value;

    public RowIndexedDateValue(final long index, final Date value) {
        this.index = index;
        this.value = value;
    }
}
