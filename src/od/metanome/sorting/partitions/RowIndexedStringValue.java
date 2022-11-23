package od.metanome.sorting.partitions;

/**
 * Created by Mehdi on 7/1/2016.
 */
public class RowIndexedStringValue extends RowIndexedValue {
    public final String value;

    public RowIndexedStringValue(final long index, final String value) {
        this.index = index;
        this.value = value;
    }
}
