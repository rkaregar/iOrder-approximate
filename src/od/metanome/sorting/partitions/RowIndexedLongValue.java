package od.metanome.sorting.partitions;

/**
 * Created by Mehdi on 7/1/2016.
 */
public class RowIndexedLongValue extends RowIndexedValue {
    public final Long value;

    public RowIndexedLongValue(final long index, final Long value) {
        this.index = index;
        this.value = value;
    }
}
